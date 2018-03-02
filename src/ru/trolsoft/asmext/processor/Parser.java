package ru.trolsoft.asmext.processor;


import ru.trolsoft.asmext.data.*;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.files.SourceFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static ru.trolsoft.asmext.data.Block.*;

public class Parser {
    Procedure currentProcedure;
    private int lineNumber;
    private OutputFile output = new OutputFile();
    private final Compiler compiler = new Compiler(this);
    Map<String, Procedure> procedures = new HashMap<>();
    Map<String, Variable> variables = new HashMap<>();
    Map<String, Constant> constants = new HashMap<>();
    Map<String, Alias> globalAliases = new HashMap<>();
    private Map<String, Label> dataLabels = new HashMap<>();
    private Map<String, Label> codeLabels = new HashMap<>();
    private Stack<Block> blocks = new Stack<>();
    boolean gcc;
    private Segment currentSegment;
    private boolean blockComment;
    private File sourceParent;

    Parser() {

    }

    public Parser(boolean gcc) {
        this();
        this.gcc = gcc;
    }


    public void parse(File file) throws IOException, SyntaxException {
        SourceFile src = new SourceFile();
        src.read(file);
        sourceParent = file.getParentFile();
        preload(src);
        lineNumber = 0;
        currentProcedure = null;
        for (TokenString s : src) {
            parseLine(s);
        }
    }


    void parseLine(String line) throws SyntaxException {
        parseLine(new TokenString(line));
    }

    private void parseLine(TokenString line) throws SyntaxException {
        lineNumber++;
        if (skipComment(line)) {
            return;
        }

        String firstToken = line.getFirstToken();

        Block block = getLastBlock();
        if (block != null && block.type == BLOCK_BYTES && !"}".equals(firstToken)) {
            processBytesBlockLine(block, line);
            return;
        }

        if (".".equals(firstToken)) {
            String trimLine = line.pure();
            String name = line.size() > 1 ? line.getToken(1) : null;
            if (line.isEmpty()) {
                output.add(line);
            } else if ("use".equals(name)) {
                use(line);
            } else if ("def".equalsIgnoreCase(name)) {
                output.add(";" + line);
            } else if ("proc".equals(name)) {
                startProcedure(line);
            } else if ("endproc".equals(name)) {
                endProcedure(line);
            } else if ("args".equals(name)) {
                loadProcedureArgs(line);
            } else if ("extern".equals(name)) {
                processExtern(line);
            } else if ("equ".equalsIgnoreCase(name)) {
                processEqu(line.toString(), trimLine.substring(".equ".length()).trim(), true);
            } else if ("set".equalsIgnoreCase(name)) {
                processSet(line.toString(), trimLine.substring(".set".length()).trim(), true);
            } else {
                processLine(line);
            }
        } else if ("loop".equals(firstToken)) {
            processStartLoop(line);
        } else if ("if".equals(firstToken) && line.contains("{")) {
            processStartIf(line);
        } else if ("}".equals(firstToken) && line.contains("{") && line.contains("else")) { // TODO
            processStartElse(line);
        } else if ("byte".equals(firstToken) && line.size() >= 4 && "[".equals(line.getToken(1)) &&
                "]".equals(line.getToken(2)) && line.contains("{") ) {
            processBytesBlock();
        } else if ("}".equals(firstToken)) {
            processEndBlock(line);
        } else if ("#define".equals(firstToken)) {
            processDefine(line);
        } else if ("continue".equals(firstToken)) {
            processContinue(line);
        } else if ("break".equals(firstToken)) {
            processBreak(line);

//        } else if (trimLine.startsWith("@") && trimLine.contains(":") && currentProcedure != null) {
//            localLabel(trimLine.substring(1, trimLine.length() - 1).trim());

        } else {
            processLine(line);
        }
    }



    private boolean skipComment(TokenString line) {
        // comments in gcc
        if (gcc && line.size() > 1 && "/".equals(line.getToken(0)) && "*".equals(line.getToken(1))) {
            blockComment = true;
        } else if (blockComment && line.size() > 1 && "*".equals(line.getToken(0)) && "/".equals(line.getToken(1))) {
            blockComment = false;
            return true;
        }
        return blockComment;
    }

    private void preload(SourceFile src) throws SyntaxException {
        if (gcc) {
            currentSegment = Segment.CODE;
        }
        currentProcedure = null;
        for (TokenString s : src) {
            preloadLine(s);
        }
    }

    void preloadLine(String line) throws SyntaxException {
        preloadLine(new TokenString(line));
    }

    private void preloadLine(TokenString line) throws SyntaxException {
        lineNumber++;
        if (line.firstTokenIs(".")) {
            preloadDirective(line);
        } else if (line.lastTokenIs(":") && !line.firstTokenIs("@")) {
            preloadLabel(line);
        }
    }


    private void preloadDirective(TokenString line) throws SyntaxException {
        String secondToken = line.size() > 1 ? line.getToken(1) : null;
        if (!gcc) {
            if ("dseg".equalsIgnoreCase(secondToken)) {
                currentSegment = Segment.DATA;
                return;
            } else if ("cseg".equalsIgnoreCase(secondToken)) {
                currentSegment = Segment.CODE;
                return;
            }
        }
        if ("proc".equals(secondToken)) {
            replaceGlobalAliases(line);
            Expression expr = new Expression(line);
            expr.removeFirst(2);
            currentProcedure = loadProcedureDefinition(expr);
        } else if ("endproc".equals(secondToken)) {
            currentProcedure = null;
        } else if ("args".equals(secondToken)) {
            loadProcedureArgs(line);
        } else if ("def".equalsIgnoreCase(secondToken)) {
            def(line);
        } else if ("include".equalsIgnoreCase(secondToken)) {
            include(line);
        } else if ("equ".equalsIgnoreCase(secondToken)) {
            String trimLine = line.pure();
            processEqu(line.toString(), trimLine.substring(".equ".length()).trim(), false);
        } else if ("set".equalsIgnoreCase(secondToken)) {
            String trimLine = line.pure();
            processSet(line.toString(), trimLine.substring(".set".length()).trim(), false);
        }
    }

    private void preloadLabel(TokenString line) {
        String labelName = line.getFirstToken();
        Label label = new Label(labelName, lineNumber);
        if (currentSegment == Segment.DATA) {
            dataLabels.put(labelName, label);
        } else {
            codeLabels.put(labelName, label);
        }
    }

    private String resolveLocalLabel(String label) {
        return currentProcedure.name + "__" + label;
    }

    private void processLine(TokenString line) throws SyntaxException {
        resolveAliases(line);
        try {
            compiler.compile(line, output);
        } catch (SyntaxException e) {
            throw e.line(lineNumber);
        }
    }

    private void resolveAliases(TokenString line) {
        for (int i = 0; i < line.size(); i++) {
            String token = line.getToken(i);
            String nextToken = i < line.size() - 1 ? line.getToken(i + 1) : null;
            Alias alias = ":".equals(nextToken) ? null : resolveProcAlias(token);
            if (alias != null) {
                line.modifyToken(i, alias.register.toString());
            } else if (token.startsWith("@") && currentProcedure != null) {
                line.modifyToken(i, resolveLocalLabel(token.substring(1)));
            }
        }
    }


    private Alias resolveProcAlias(String aliasName) {
        if (currentProcedure == null) {
            return globalAliases.get(aliasName);
        }
        Alias alias = currentProcedure.uses.get(aliasName);
        if (alias != null) {
            return alias;
        }
        alias = currentProcedure.args.get(aliasName);
        if (alias == null) {
            alias = globalAliases.get(aliasName);
        }
        return alias;
    }


    private void startProcedure(TokenString src) throws SyntaxException {
        replaceGlobalAliases(src);
        Expression expr = new Expression(src);
        if (expr.size() < 3) {
            error("name expected");
        }
        expr.removeFirst(2); // .proc
        String name = expr.getFirst().asString();
        currentProcedure = loadProcedureDefinition(expr);
        output.add(name + ":");
        output.addComment(src);
    }

    private void endProcedure(TokenString src) throws SyntaxException {
        src.removeEmptyTokens();
        if (src.getTokens().size() != 2) {
            error("extra characters in line");
        }
        if (currentProcedure == null) {
            error("start .proc directive not found");
        }
        currentProcedure = null;
        output.addComment(src);
    }

    private void processStartLoop(TokenString str) throws SyntaxException {
        resolveAliases(str);
        Expression expr = buildExpression(str);

        if (!expr.getLast().isOperator("{")) {
            error("'{' expected");
        }
        Expression argExpr = null;
        if (expr.get(1).isOperator("(")) {
            if (!expr.getLast(1).isOperator(")")) {
                error("')' not found");
            }
            argExpr = expr.subExpression(2, expr.size() - 3);
            if (argExpr.isEmpty()) {
                argExpr = null;
            }
        } else if (expr.size() != 2) {
            error("wrong loop syntax");
        }
        String name = argExpr != null ? argExpr.getFirst().asString() + "_" : "";
        String label = (currentProcedure != null ? currentProcedure.name : "") + "__loop_" + name + lineNumber;
        Block block = new Block(BLOCK_LOOP, argExpr, lineNumber, label);
        blocks.push(block);

        if (argExpr != null) {
            Token reg = argExpr.getFirst();
            if (!reg.isRegister()) {
                error("register expected: " + reg);
            }
            if (argExpr.size() > 1) {
                if (!argExpr.get(1).isOperator("=")) {
                    error("wrong loop argument expression");
                }
                try {
                    new ExpressionsCompiler(this).compile(str, argExpr.copy(), output);
                } catch (ExpressionsCompiler.CompileException e) {
                    error("wrong argument");
                }
            }
        }
        output.add(block.getLabelStart() + ":");
    }

    private void processStartIf(TokenString src) throws SyntaxException {
        resolveAliases(src);
        String label = (currentProcedure != null ? currentProcedure.name : "") + "__if_" + lineNumber;
        Expression expr = buildExpression(src);
        if (!expr.getLast().isOperator("{")) {
            error("wrong if block syntax");
        }
        expr.set(expr.size() - 1, new Token(Token.TYPE_KEYWORD, "goto"));
        expr.add(new Token(Token.TYPE_OTHER, label));
        compiler.compileIf(src, expr, output, true);
        Block block = new Block(BLOCK_IF, expr, lineNumber, label);
        blocks.push(block);
    }

    private void processStartElse(TokenString src) throws SyntaxException {
        Expression expr = new Expression(src);
        if (expr.size() != 3 || !expr.getFirst().isOperator("}") || !expr.getLast().isOperator("{")
                || !expr.get(1).isKeyword("else")) {
            error("wrong else block syntax");
        }
        Block prevBlock = getLastBlock();
        if (prevBlock == null || prevBlock.type != BLOCK_IF) {
            error("if block not found");
        }
        blocks.pop();
        String label = (currentProcedure != null ? currentProcedure.name : "") + "__if_else_" + lineNumber;
        Block block = new Block(BLOCK_ELSE, null, lineNumber, label);
        blocks.push(block);
        output.appendCommand(src, "rjmp", block.getLabelStart());
        output.add(prevBlock.getLabelStart() + ":");
    }

    private void processBytesBlock() {
        Block block = new Block(BLOCK_BYTES, new Expression(), lineNumber);
        blocks.push(block);
    }

    private void processBytesBlockLine(Block block, TokenString line) throws SyntaxException {
        Expression expr = new Expression(line);
        boolean comaExpected = false;
        for (Token t : expr) {
            if (comaExpected) {
                if (!t.isOperator(",")) {
                    error("unexpected value: " + t);
                }
                comaExpected = false;
            } else if (t.isAnyConst() || t.isSomeString()) {
                block.expr.add(t);
                comaExpected = true;
            }
        }
    }

    private void processEndBlock(TokenString line) throws SyntaxException {
        line.removeEmptyTokens();
        if (line.size() != 1) {
            error("extra characters in line: " + line.getTokens());
        }
        if (blocks.isEmpty()) {
            error("open bracket not found: '{'");
        }
        Block lastBlock = blocks.pop();
        switch (lastBlock.type) {
            case BLOCK_LOOP:
                processEndLoop(line, lastBlock);
                break;
            case BLOCK_IF:
            case BLOCK_ELSE:
                processEndIf(line, lastBlock);
                break;
            case BLOCK_BYTES:
                processEndBytes(lastBlock);
                break;
        }
    }

    private void processEndLoop(TokenString line, Block block) {
        if (block.expr != null) {
            Token reg = block.expr.getFirst();
            output.appendCommand(line, "dec", reg).append("\t\t; ").append(reg);
            output.appendCommand(line, "brne", block.getLabelStart());
        } else {
            output.appendCommand(line, "rjmp", block.getLabelStart());
        }
        if (block.getLabelEnd() != null) {
            output.add(block.getLabelEnd() + ":");
        }
    }

    private void processEndIf(TokenString line, Block block) {
        output.add(block.getLabelStart() + ":");
    }

    private void processEndBytes(Block block) {
        int cnt = 0;
        StringBuilder line = output.startNewLine().append('\t');
        for (Token t : block.expr) {
            if (cnt == 0) {
                line.append(".db\t");
            }
            line.append(t);
            cnt++;
            if (cnt == 8) {
                line = output.startNewLine().append('\t');
                cnt = 0;
            } else {
                line.append(", ");
            }
        }
    }

    private void processContinue(TokenString line) throws SyntaxException {
        line.removeEmptyTokens();
        if (line.size() != 1) {
            error("extra characters in line");
        }
        Block block = getCurrentCycleBlock();
        output.appendCommand(line, "rjmp", block.getLabelStart());
    }

    private void processBreak(TokenString line) throws SyntaxException {
        line.removeEmptyTokens();
        if (line.size() != 1) {
            error("extra characters in line");
        }
        Block block = getCurrentCycleBlock();
        output.appendCommand(line, "rjmp", block.buildEndLabel());
    }

    private void processExtern(TokenString line) throws SyntaxException {
        Expression expr = new Expression(line);
        if (expr.size() < 3) {
            error("wrong syntax");
        }
        // check procedure
        // .extern drawCharXY (x:r24, y:r22, char:r20)
        if (expr.size() > 3 && expr.get(3).isOperator("(")) {
            expr.removeFirst(2); // .extern
            loadProcedureDefinition(expr);
            return;
        }

        // check variable
        if (expr.getLast(1).isOperator(":")) {
            Token varType = expr.getLast();
            Variable.Type type = ParserUtils.getVarType(varType.asString());
            if (type == null) {
                error("Invalid variable type: " + varType);
            }
            int index = 2;
            while (index < expr.size()-2) {
                Token varName = expr.get(index++);
                Token next = expr.get(index);
                if (next.isOperator(",")) {
                    index++;
                } else if (index != expr.size() - 2) {
                    error("error in .extern declaration");
                }
                if (!varName.isSomeString() || !ParserUtils.isValidName(varName.asString())) {
                    error("Wrong name: " + varName);
                }
                if (variables.containsKey(varName.asString())) {
                    error("Variable already defined: " + varName);
                }
                Variable var = new Variable(varName.asString(), type);
                variables.put(varName.asString(), var);
                if (gcc) {
                    output.add(".extern " + varName + "\t; " + varType);
                }
            }
            return;
        }
        output.add(line.toString());
    }

    private Procedure loadProcedureDefinition(Expression expr) throws SyntaxException {
        String procName = expr.get(0).asString();
        if (currentProcedure != null) {
            error("nested procedure declaration: " + procName);
        }
        checkName(procName);
        Procedure proc = new Procedure(procName);
        procedures.put(procName, proc);
        if (expr.size() == 1) {
            return proc;
        }
        if (expr.size() < 3 || !expr.get(1).isOperator("(") || !expr.getLast().isOperator(")")) {
            error("procedure declaration error");
        }
        int index = 2;
        while (index < expr.size()-1) {
            Token argName = expr.getIfExist(index++);
            Token colons = expr.getIfExist(index++);
            Token argRegs = expr.getIfExist(index++);
            if (argRegs == null || colons == null || !colons.isOperator(":") ||
                    (!argRegs.isRegister() && !argRegs.isPair() && !argRegs.isRegGroup())) {
                error("wrong argument: " + argName);
            }

            String arg = argName.asString();
            if (globalAliases.containsKey(arg)) {
                argRegs = globalAliases.get(arg).register;
            }

            if (proc.hasArg(arg)) {
                error("duplicate argument '" + arg + "'");
            }
            Alias alias = createAlias(arg, argRegs);
            proc.addArg(alias);
            Token next = expr.get(index);
            if (next.isOperator(",")) {
                index++;
            } else if (!(next.isOperator(")") && index == expr.size()-1)) {
                error("wrong syntax");
            }
        }
        if (gcc) {
            output.add(".extern " + procName);
        }
        return proc;
    }

    private void processEqu(String line, String args, boolean addToOutput) throws SyntaxException {
        String split[] = args.split("=");
        if (split.length > 2) {
            error("wrong expression");
        }
        String name = split[0].trim();
        String value = split.length > 1 ? split[1].trim() : null;
        Constant c = new Constant(name, value, Constant.Type.EQU);
        if (currentProcedure != null) {
            currentProcedure.consts.put(name, c);
        } else {
            constants.put(name, c);
        }
        if (!gcc && addToOutput && value != null) {
            output.add(line);
        }
    }

    private void processSet(String line, String args, boolean addToOutput) throws SyntaxException {
        String split[] = args.split("=");
        if (split.length > 2) {
            error("wrong expression");
        }
        String name = split[0].trim();
        Constant c = new Constant(name, null, Constant.Type.EQU);
        if (currentProcedure != null) {
            currentProcedure.consts.put(name, c);
        } else {
            constants.put(name, c);
        }
        if (!gcc && addToOutput) {
            output.add(line);
        }
    }


    private void processDefine(TokenString line) {
        line.removeEmptyTokens();
        line.removeFirstToken();
        if (!line.isEmpty()) {
            String name = line.getFirstToken();
            String value = line.mergeTokens(1);
            Constant c = new Constant(name, value, Constant.Type.DEFINE);
            constants.put(name, c);
        }
        output.add(line.toString());
    }


    private void loadProcedureArgs(TokenString line) throws SyntaxException {
// TODO executed twice - on preload and load !!!
        if (currentProcedure == null) {
            error(".args can be defined in .proc block only");
        }
        replaceGlobalAliases(line);
        Expression expr = new Expression(line);
        for (int i = 2; i < expr.size(); ) {
            String name = line.getToken(i++);
            if (i >= expr.size()-1 || !expr.get(i++).isOperator("(")) {
                error("wrong argument: " + name);
            }
            Token reg = expr.get(i++);
            if (i >= line.size() || !expr.get(i++).isOperator(")")) {
                error("wrong argument: " + name);
            }
            if (!reg.isRegister() && !reg.isPair() && !reg.isRegGroup()) {
                String regStr = reg.toString();
                if (globalAliases.containsKey(regStr)) {
                    reg = globalAliases.get(regStr).register;
                }
            }
            Alias alias = createAlias(name, reg);
            if (currentProcedure.hasAlias(name)) {
                error("alias '" + name + "' already defined for this procedure");
            }
            if (currentProcedure.hasArg(name)) {
                error("argument '" + name + "' already defined for this procedure");
            }
            currentProcedure.addArg(alias);
            if (i < expr.size() && !expr.get(i++).isOperator(",")) {
                error("wrong argument, comma expected after " + name);
            }
        }

        output.add(";" + line);
    }


    private void use(TokenString str) throws SyntaxException {
        replaceGlobalAliases(str);
        Expression expr = new Expression(str);
        int index = 2;
        while (index < expr.size()) {
            if (index + 2 >= expr.size()) {
                error("wrong .use syntax ");
            }
            Token aliasRegs = expr.get(index++);
            Token as = expr.get(index++);
            String aliasName = expr.get(index++).toString();
            if (!as.toString().equals("as")) {
                error("wrong .use syntax ");
            }
            if (index < expr.size()) {
                if (!expr.get(index++).isOperator(",")) {
                    error("wrong .use syntax ");
                }
            }
            checkName(aliasName);
            Alias alias = createAlias(aliasName, aliasRegs);
            if (currentProcedure != null) {
                if (currentProcedure.hasAlias(aliasName)) {
                    error("alias '" + aliasName + "' already defined for this procedure");
                }
                if (currentProcedure.hasArg(aliasName)) {
                    error("argument '" + aliasName + "' already defined for this procedure");
                }
                currentProcedure.addAlias(alias);
            } else {
                if (globalAliases.containsKey(aliasName)) {
                    error("global alias '" + aliasName + "' already defined");
                }
                globalAliases.put(aliasName, alias);
            }
        }
        output.addComment(str);
    }

    private void def(TokenString str) throws SyntaxException {
        str.removeEmptyTokens();
        if (str.size() != 5) {
            error("wrong .def syntax ");
        }
        String aliasName = str.getToken(2);
        String regName = str.getToken(4);
        Alias alias = createAlias(aliasName, new Token(Token.TYPE_REGISTER, regName));
        if (currentProcedure != null) {
            if (currentProcedure.hasAlias(aliasName)) {
                error("alias '" + aliasName + "' already defined for this procedure");
            }
            if (currentProcedure.hasArg(aliasName)) {
                error("argument '" + aliasName + "' already defined for this procedure");
            }
            currentProcedure.addAlias(alias);
        } else {
            if (globalAliases.containsKey(aliasName)) {
                error("global alias '" + aliasName + "' already defined");
            }
            globalAliases.put(aliasName, alias);
        }
    }

    private void include(TokenString str) throws SyntaxException {
        StringBuilder arg = new StringBuilder();
        // TODO move this to TokenString
        for (int i = 0; i < str.size(); i++) {
            String token = str.getToken(i);
            if (arg.length() == 0 && (token.startsWith("'") || token.startsWith("\""))) {
                arg.append(token);
            } else if (token.endsWith("'") || token.endsWith("\"")) {
                arg.append(token);
                break;
            } else if (arg.length() > 0) {
                arg.append(token);
            }
        }
        String fileName = ParserUtils.removeBrackets(arg.toString());
        if (!tryIncludeFile(fileName)) {
            tryIncludeFile(sourceParent.getAbsolutePath() + "/" + fileName);
        }
    }

    private boolean tryIncludeFile(String fileName) throws SyntaxException {
        File file = new File(fileName);
        if (!file.exists()) {
            return false;
        }
        SourceFile src = new SourceFile();
        try {
            src.read(file);
            for (TokenString s : src) {
                preloadLine(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private Alias createAlias(String name, Token reg) throws SyntaxException {
        checkName(name);
        if (!reg.isRegister() && !reg.isPair() && !reg.isRegGroup() && !globalAliases.containsKey(reg.asString())) {
            error("register, pair or group  expected: " + reg);
        }
        return new Alias(name, reg);
    }

    private void checkName(String name) throws SyntaxException {
        if (!ParserUtils.isValidName(name)) {
            error("wrong name: '" + name + "'");
        }
        if (ParserUtils.isRegister(name)) {
            error(" wrong name: '" + name + "' (register name)");
        }
        if (ParserUtils.isInstruction(name)) {
            error(" wrong name: '" + name + "' (instruction name)");
        }
    }


    private void error(String msg) throws SyntaxException {
        throw new SyntaxException(msg).line(lineNumber);
    }


    public OutputFile getOutput() {
        return output;
    }

    Variable getVariable(String name) {
        Variable result = variables.get(name);
        if (result == null) {
            if (codeLabels.containsKey(name) || procedures.containsKey(name)) {
                result = new Variable(name, Variable.Type.PRGPTR);
                variables.put(name, result);
            }
//            else if (dataLabels.containsKey(name)) {
//                result = new Variable(name, Variable.Type.POINTER);
//                variables.put(name, result);
//            }
        }
        return result;
    }

    boolean isConstant(String name) {
        return constants.containsKey(name) || (currentProcedure != null && currentProcedure.hasConst(name));
    }

    Constant getConstant(String name) {
        if (currentProcedure != null && currentProcedure.hasConst(name)) {
            return currentProcedure.getConst(name);
        } else {
            return constants.get(name);
        }
    }

    String makeConstExpression(Constant constant) {
        if (!gcc || constant.type == Constant.Type.DEFINE) {
            return constant.name;
        }
        TokenString tokens = new TokenString(constant.value);
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            if (isConstant(token)) {
                Constant c = getConstant(token);
                String sc = makeConstExpression(c);
                result.append(ParserUtils.wrapToBrackets(sc));
            } else {
                result.append(token);
            }
        }
        return ParserUtils.wrapToBrackets(result.toString());
    }

    String makeConstExpression(String name) {
        Constant c = getConstant(name);
        return makeConstExpression(c);
    }

//    boolean isConstExpression(String expr) {
//        if (ParserUtils.isConstExpression(expr)) {
//            return true;
//        }
//        TokenString tokens = new TokenString(expr);
//        StringBuilder builder = new StringBuilder();
//        for (String s : tokens) {
//            if (isConstant(s)) {
//                s = makeConstExpression(s);
//            }
//            builder.append(s);
//        }
//        return ParserUtils.isConstExpression(builder.toString());
//    }
//
//    boolean isConstExpressionTokens(List<String> tokens, List<String> constants) {
//        StringBuilder builder = new StringBuilder();
//        for (String s : tokens) {
//            if (isConstant(s)) {
//                s = makeConstExpression(s);
//            } else if (constants != null && constants.contains(s)) {
//                s = "0";
//            }
//            builder.append(s);
//        }
//        return ParserUtils.isConstExpression(builder.toString());
//    }

    private void markConstAndVariables(Expression expr) {
        boolean constFound = false;
        for (int i = 0; i < expr.size(); i++) {
            Token t = expr.get(i);
            if (t.isSomeString()) {
                String name = t.asString();
                Constant c = getConstant(name);
                if (c != null) {
                    constFound = true;
                    String val = c.value;
                    if (ParserUtils.isNumber(val)) {
                        expr.set(i, new Token(Token.TYPE_NUMBER, val));
                    } else if (val == null) {// && parser.gcc) {
                        expr.set(i, new Token(Token.TYPE_CONST_EXPRESSION, ParserUtils.wrapToBrackets(c.name)));
                    } else {
                        Expression valExpr = buildExpression(new TokenString(val));
                        markConstAndVariables(valExpr);
                        Token newToken;
                        if (gcc) {
                            newToken = new Token(Token.TYPE_CONST_EXPRESSION, ParserUtils.wrapToBrackets(valExpr.toString()));
                        } else {
                            newToken = new Token(Token.TYPE_CONST_EXPRESSION, name);
                        }
                        expr.set(i, newToken);
                        //expr.set(i, new Token(Token.TYPE_CONST_EXPRESSION, ParserUtils.wrapToBrackets(valExpr.toString())));
                    }
                } else if (getVariable(name) != null) {
                    expr.set(i, new Token(Token.TYPE_VARIABLE, name));
                }
            }
        }

        if (constFound) {
            mergeConst(expr);
        }
    }

    private void mergeConst(Expression expr) {
        while (true) {
            boolean anyMerge = false;
            for (int i = 1; i < expr.size() - 1; i++) {
                Token cur = expr.get(i);
                Token prev = expr.get(i - 1);
                Token next = expr.get(i + 1);
                boolean merge;
                if (cur.isOperator("+", "-", "*", "<<", ">>", "&", "|") &&
                        prev.isAnyConst() && next.isAnyConst()) {
                    merge = true;
                } else if (prev.isOperator("(") && next.isOperator(")") && cur.isAnyConst()) {
                    String cs = cur.asString();
                    if (cs.startsWith("(") && cs.endsWith(")")) {
                        expr.remove(i-1);
                        expr.remove(i);
                        anyMerge = true;
                        break;
                    }
                    merge = true;
                } else {
                    merge = false;
                }
                if (merge) {
                    Token t = new Token(Token.TYPE_CONST_EXPRESSION, prev.asString() + cur.asString() + next.asString());
                    expr.set(i - 1, t);
                    expr.remove(i);
                    expr.remove(i);
                    anyMerge = true;
                    break;
                }
            }
            if (!anyMerge) {
                break;
            }
        } // while
    }

    private void replaceGlobalAliases(TokenString str) {
        for (int i = 0; i < str.size(); i++) {
            String src = str.getToken(i);
            Alias alias = globalAliases.get(src);
            if (alias != null) {
                str.modifyToken(i, alias.register.toString());
            }
        }
    }

    Expression buildExpression(TokenString src) {
        Expression expr = new Expression(src);
        markConstAndVariables(expr);
        return expr;
    }


    Block getLastBlock() {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.get(blocks.size()-1);
    }


    Block getCurrentCycleBlock() throws SyntaxException {
        for (int i = blocks.size()-1; i >= 0; i--) {
            Block block = blocks.get(i);
            if (block.type == BLOCK_LOOP) {
                return block;
            }
        }
        error(".loop not found");
        return null;
    }
}
