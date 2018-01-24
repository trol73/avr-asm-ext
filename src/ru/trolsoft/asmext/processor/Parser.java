package ru.trolsoft.asmext.processor;


import ru.trolsoft.asmext.data.*;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.files.SourceFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Parser {
    Procedure currentProcedure;
    private int lineNumber;
    private OutputFile output = new OutputFile();
    private final Compiler compiler = new Compiler(this);
    Map<String, Procedure> procedures = new HashMap<>();
    Map<String, Variable> variables = new HashMap<>();
    Map<String, Constant> constants = new HashMap<>();
    Map<String, Alias> globalAliases = new HashMap<>();
    Map<String, Label> dataLabels = new HashMap<>();
    Map<String, Label> codeLabels = new HashMap<>();
    Stack<Block> blocks = new Stack<>();
    boolean gcc;
    private Segment currentSegment;
    boolean blockComment;

    Parser() {

    }

    public Parser(boolean gcc) {
        this();
        this.gcc = gcc;
    }


    public void parse(File file) throws IOException, SyntaxException {
        SourceFile src = new SourceFile();
        src.read(file);
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

    void parseLine(TokenString line) throws SyntaxException {
        lineNumber++;
        if (skipComment(line)) {
            return;
        }

        String firstToken = line.getFirstToken();
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
            } else if ("loop".equals(name)) {
                processStartLoop(line);
            } else if ("endloop".equals(name)) {
                processEndLoop(line);
            } else if ("extern".equals(name)) {
                processExtern(line, trimLine.substring(".extern".length()).trim());
            } else if ("equ".equalsIgnoreCase(name)) {
                processEqu(line.toString(), trimLine.substring(".equ".length()).trim());
            } else if ("set".equalsIgnoreCase(name)) {
                processSet(line.toString(), trimLine.substring(".set".length()).trim());
            } else {
                processLine(line);
            }
        } else if (firstToken != null && firstToken.equals("#define")) {
            processDefine(line);


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
        if (blockComment) {
            return true;
        }
        return false;
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
            line.removeEmptyTokens();
            String name = line.getToken(2);
            currentProcedure = new Procedure(name);
            procedures.put(name, currentProcedure);
        } else if ("endproc".equals(secondToken)) {
            currentProcedure = null;
        } else if ("args".equals(secondToken)) {
            loadProcedureArgs(line);
        } else if ("def".equalsIgnoreCase(secondToken)) {
            def(line);
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
        for (int i = 0; i < line.size(); i++) {
            String token = line.getToken(i);
            String nextToken = i < line.size()-1 ? line.getToken(i+1) : null;
            Alias alias = ":".equals(nextToken) ? null : resolveProcAlias(token);
            if (alias != null) {
                line.modifyToken(i, alias.register);
            } else if (token.startsWith("@") && currentProcedure != null) {
                line.modifyToken(i, resolveLocalLabel(token.substring(1)));
            }
        }
        try {
            compiler.compile(line, output);
        } catch (SyntaxException e) {
            throw e.line(lineNumber);
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
        src.removeEmptyTokens();
        if (src.size() < 3) {
            error("name expected");
        }
        String name = src.getToken(2);
        checkName(name);
        if (currentProcedure != null) {
            error("nested .proc: '" + name + "'");
        }
        currentProcedure = new Procedure(name);
        procedures.put(name, currentProcedure);
        output.add(name + ":");
        output.addComment(src);
    }

    private void endProcedure(TokenString src) throws SyntaxException {
        src.removeEmptyTokens();
        if (src.size() != 2) {
            error("extra characters in line");
        }
        if (currentProcedure == null) {
            error("start .proc directive not found");
        }
        currentProcedure = null;
        output.addComment(src);
    }

    private void processStartLoop(TokenString str) throws SyntaxException {
        str.removeEmptyTokens();
        if (":".equals(str.getLastToken())) {
            str.removeLastToken();
        }
        // .loop (x>2)
        if (str.size() < 4) {
            error("expression expected");
        }
        if (!"(".equals(str.getToken(2)) || !")".equals(str.getLastToken())) {
            error("expected ()");
        }
        str.removeFirstToken(); // .
        str.removeFirstToken(); // loop
        str.removeFirstToken(); // (
        str.removeLastToken();  // )
        Block block = new Block(Block.TYPE_LOOP, str.getTokens(), lineNumber);
        if (block.args.isEmpty()) {
            error("loop parameter expected");
        }
        blocks.push(block);
        block.label = currentProcedure != null ? currentProcedure.name : "";
        block.label += "__" + block.args.get(0) + "_" + lineNumber;
        String reg = block.args.get(0);
        if (currentProcedure != null) {
            String resolve = currentProcedure.resolveVariable(reg);
            if (resolve != null) {
                reg = resolve;
            }
        }
        Alias alias = resolveProcAlias(reg);
        if (alias != null) {
            reg = alias.register;
        }
        if (!ParserUtils.isRegister(reg)) {
            error("register or alias expected: " + reg);
        }
        block.reg = reg;
        if (block.args.size() > 1) {
            if (!"=".equals(block.args.get(1)) || block.args.size() < 3) {
                error("wrong expression");
            }
            String val = block.args.get(2);
            if (currentProcedure != null) {
                String resolve = currentProcedure.resolveVariable(val);
                if (resolve != null) {
                    val = resolve;
                }
            }
            // TODO it's compiler code !!!
            StringBuilder sb;
            if (block.args.size() == 3 && ParserUtils.isRegister(val)) {
                sb = output.appendCommand(str, "mov", reg, val);
            } else {
                sb = output.appendCommand(str, "ldi", reg, "");
                for (int i = 2; i < block.args.size(); i++) {
                    sb.append(block.args.get(i));
                }
            }
            sb.append("\t\t; ").append(block.args.get(0));
        }
        output.add(block.label + ":");
    }


    private void processEndLoop(TokenString line) throws SyntaxException {
        line.removeEmptyTokens();
        if (line.size() != 2) {
            error("extra characters in line");
        }
        if (blocks.empty()) {
            error(".loop not found");
        }
        Block block = blocks.pop();
        if (block.type != Block.TYPE_LOOP) {
            error(".loop not found");
        }
        output.appendCommand(line, "dec", block.reg).append("\t\t; ").append(block.args.get(0));
        output.appendCommand(line, "brne", block.label);
    }


    private void processExtern(TokenString line, String args) throws SyntaxException {
        // check procedure
        int index = args.indexOf('(');
        if (index > 0) {
            String procArgs = args.substring(index);
            if (!procArgs.endsWith(")")) {
                error("') not found");
            }
            procArgs = procArgs.substring(1, procArgs.length()-1).trim();
            String procName = args.substring(0, index).trim();

            String[] split = procArgs.split(",");
            Procedure proc = new Procedure(procName);
            for (String s : split) {
                s = s.trim();
                String nv[] = s.split(":");
                if (nv.length != 2) {
                    error("wrong argument: " + s);
                }
                String name = nv[0].trim();
                String reg = nv[1].trim();
                if (globalAliases.containsKey(reg)) {
                    reg = globalAliases.get(reg).register;
                }
                Alias alias = createAlias(name, reg);
                if (proc.hasArg(name)) {
                    error("duplicate argument '" + name + "'");
                }
                proc.addArg(alias);
            } // for
            procedures.put(procName, proc);
            if (gcc) {
                output.add(".extern " + procName + "\t; " + procArgs);
            }
            return;
        }
        // check variable
        index = args.indexOf(':');
        if (index > 0) {
            String varNames = args.substring(0, index).trim();
            String varType = args.substring(index+1).trim().toLowerCase();
            List<String> names = new TokenString(varNames).getTokens();
            ParserUtils.removeEmptyTokens(names);
            for (String varName : names) {
                if (",".equals( varName)) {
                    continue;
                }
                if (!ParserUtils.isValidName(varName) || ParserUtils.isRegister(varName)) {
                    error("Wrong name: " + varName);
                }
                if (variables.containsKey(varName)) {
                    error("Variable already defined: " + varName);
                }
                Variable.Type type = ParserUtils.getVarType(varType);
                if (type == null) {
                    error("Invalid variable type: " + varType);
                }
                Variable var = new Variable(varName, type);
                variables.put(varName, var);
                if (gcc) {
                    output.add(".extern " + varName + "\t; " + varType);
                }
            }
            return;
        }
        output.add(line);
    }

    private void processEqu(String line, String args) throws SyntaxException {
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
        if (!gcc) {
            output.add(line);
        }
    }

    private void processSet(String line, String args) throws SyntaxException {
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
        if (!gcc) {
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
        if (currentProcedure == null) {
            error(".args can be defined in .proc block only");
        }
        // TODO executed twice - on preload and load !!!
        line.removeEmptyTokens();
        for (int i = 2; i < line.size(); ) {
            String name = line.getToken(i++);
            if (i >= line.size()-1 || !"(".equals(line.getToken(i++))) {
                error("wrong argument: " + name);
            }
            String reg = line.getToken(i++);
            if (i >= line.size() || !")".equals(line.getToken(i++))) {
                error("wrong argument: " + name);
            }
            if (globalAliases.containsKey(reg)) {
                reg = globalAliases.get(reg).register;
            }
            Alias alias = createAlias(name, reg);
            if (currentProcedure.hasAlias(name)) {
                error("alias '" + name + "' already defined for this procedure");
            }
            if (currentProcedure.hasArg(name)) {
                error("argument '" + name + "' already defined for this procedure");
            }
            currentProcedure.addArg(alias);
            if (i < line.size() && !",".equals(line.getToken(i++))) {
                error("wrong argument, comma expected after " + name);
            }
        }
        output.add(";" + line);
    }


    private void use(TokenString str) throws SyntaxException {
        str.removeEmptyTokens();
        int index = 2;
        while (index < str.size()) {
            if (index + 2 >= str.size()) {
                error("wrong .use syntax ");
            }
            String regName = str.getToken(index++);
            String as = str.getToken(index++);
            String aliasName = str.getToken(index++);
            if (!"as".equals(as)) {
                error("wrong .use syntax ");
            }
            if (index < str.size()) {
                if (!",".equals(str.getToken(index++))) {
                    error("wrong .use syntax ");
                }
            }
            Alias alias = createAlias(aliasName, regName);
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
        Alias alias = createAlias(aliasName, regName);
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

    private Alias createAlias(String name, String reg) throws SyntaxException {
        checkName(name);
        checkRegister(reg);
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

    private void checkRegister(String name) throws SyntaxException {
        if (!ParserUtils.isRegister(name) && !globalAliases.containsKey(name)) {
            error("register expected: " + name);
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
            if (codeLabels.containsKey(name)) {
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

    boolean isConstExpression(String expr) {
        if (ParserUtils.isConstExpression(expr)) {
            return true;
        }
        TokenString tokens = new TokenString(expr);
        StringBuilder builder = new StringBuilder();
        for (String s : tokens) {
            if (isConstant(s)) {
                s = makeConstExpression(s);
            }
            builder.append(s);
        }
        return ParserUtils.isConstExpression(builder.toString());
    }

    boolean isConstExpressionTokens(List<String> tokens, List<String> constants) {
        StringBuilder builder = new StringBuilder();
        for (String s : tokens) {
            if (isConstant(s)) {
                s = makeConstExpression(s);
            } else if (constants != null && constants.contains(s)) {
                s = "0";
            }
            builder.append(s);
        }
        return ParserUtils.isConstExpression(builder.toString());
    }

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
                        expr.set(i, new Token(Token.TYPE_CONST_EXPRESSION, ParserUtils.wrapToBrackets(valExpr.toString())));
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

    Expression buildExpression(TokenString src) {
        Expression expr = new Expression(src);
        markConstAndVariables(expr);
        return expr;
    }
}
