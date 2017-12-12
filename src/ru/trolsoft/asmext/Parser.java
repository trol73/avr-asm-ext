package ru.trolsoft.asmext;


import ru.trolsoft.asmext.data.*;

import java.io.*;
import java.util.*;

class Parser {
    Procedure currentProcedure;
    private int lineNumber;
    private List<String> output = new ArrayList<>();
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

    Parser() {

    }

    Parser(boolean gcc) {
        this();
        this.gcc = gcc;
    }


    void parse(File file) throws IOException, SyntaxException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            preload(reader);
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            parse(reader);
        }
    }

    private void parse(BufferedReader reader) throws IOException, SyntaxException {
        lineNumber = 0;
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            parseLine(line);
        }
    }

    void parseLine(String line) throws SyntaxException {
        lineNumber++;
        String trimLine = removeCommentsAndSpaces(line);
        String trimLower = trimLine.toLowerCase();
        if (trimLine.isEmpty()) {
            output.add(line);
        } else if (checkDirective(trimLine, ".use")) {
            use(trimLine.substring(".use".length()).trim());
            output.add(";" + line);
        } else if (checkDirective(trimLower, ".def")) {
            def(trimLine.substring(".def".length()).trim());
            output.add(";" + line);
        } else if (checkDirective(trimLine, ".proc")) {
            startProc(trimLine.substring(".proc".length()).trim());
            output.add(";" + line);
        } else if (checkDirective(trimLine, ".endproc")) {
            endProc(trimLine.substring(".endproc".length()).trim());
            output.add(";" + line);
        } else if (checkDirective(trimLine, ".args")) {
            procArgs(trimLine.substring(".args".length()).trim());
            output.add(";" + line);
        } else if (trimLine.startsWith("@") && trimLine.contains(":") && currentProcedure != null) {
            localLabel(trimLine.substring(1, trimLine.length() - 1).trim());
        } else if (checkDirective(trimLine, ".loop")) {
            processStartLoop(line, trimLine.substring(".loop".length()).trim());
        } else if (checkDirective(trimLine, ".endloop")) {
            processEndLoop(line, trimLine.substring(".endloop".length()).trim());
        } else if (checkDirective(trimLine, ".extern")) {
            processExtern(line, trimLine.substring(".extern".length()).trim());
        } else if (checkDirective(trimLine, ".equ")) {
            processEqu(line, trimLine.substring(".equ".length()).trim());
        } else if (checkDirective(trimLine, "#define")) {
            processDefine(line, trimLine.substring("#define".length()).trim());
        } else {
            processLine(line);
        }
    }

    void preload(BufferedReader reader) throws IOException {
        if (gcc) {
            currentSegment = Segment.CODE;
        }
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            preloadLine(line);
        }
    }

    void preloadLine(String line) {
        lineNumber++;
        String trimLine = removeCommentsAndSpaces(line);
        if (!gcc) {
            if (".dseg".equalsIgnoreCase(trimLine)) {
                currentSegment = Segment.DATA;
                return;
            } else if (".cseg".equalsIgnoreCase(trimLine)) {
                currentSegment = Segment.CODE;
                return;
            }
        }
        if (trimLine.endsWith(":") && !trimLine.startsWith(".") && !trimLine.startsWith("@")) {
            String labelName = trimLine.substring(0, trimLine.length()-1);
            Label label = new Label(labelName, lineNumber);
            if (currentSegment == Segment.DATA) {
                dataLabels.put(labelName, label);
            } else {
                codeLabels.put(labelName, label);
            }
        }

    }


    private void localLabel(String label) {
        output.add(resolveLocalLabel(label) + ":");
    }

    private String resolveLocalLabel(String label) {
        return currentProcedure.name + "__" + label;
    }

    private void processLine(String line) throws SyntaxException {
        String tokens[] = splitToTokens(line);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String nextToken = i < tokens.length-1 ? tokens[i+1] : tokens[i];
            Alias alias = ":".equals(nextToken) ? null : resolveProcAlias(token);
            if (alias != null) {
                tokens[i] = alias.register;
            } else if (token.startsWith("@") && currentProcedure != null) {
                tokens[i] = resolveLocalLabel(token.substring(1));
            }
        }
        StringBuilder outLine = new StringBuilder();
        try {
            compiler.compile(line, tokens, outLine);
        } catch (SyntaxException e) {
            throw e.line(lineNumber);
        }
        output.add(outLine.toString());
    }


    List<String> splitToTokensList(String line, boolean removeEmpty) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        final String delim = " \t,.+-*/=():";
        StringTokenizer tokenizer = new StringTokenizer(line, delim, true);
        boolean commentStarted = false;
        while (tokenizer.hasMoreElements()) {
            String next = tokenizer.nextToken();
            String prev = result.isEmpty() ? null : result.get(result.size()-1);
            if (prev != null && next.trim().isEmpty() && prev.trim().isEmpty()) {
                result.set(result.size()-1, prev + next);
                continue;
            }

            // ' ' and '\t' is char const
            if ("'".equals(prev) && delim.contains(next) && next.length() == 1) {
                result.set(result.size()-1, prev + next);
                continue;
            }
            if (("'".equals(next)) && (prev != null && prev.startsWith("'") && prev.length() == 2) && delim.contains(prev.substring(1))) {
                result.set(result.size()-1, prev + next);
                continue;
            }

            if (commentStarted) {
                result.set(result.size()-1, prev + next);
                continue;
            }
            if (";".equals(next)) {
                commentStarted = true;
            } else if ("/".equals(next) && "/".equals(prev)) {
                result.set(result.size()-1, prev + next);
                commentStarted = true;
                continue;
            }
            result.add(next);
        }
        if (removeEmpty) {
            ParserUtils.removeEmptyTokens(result);
        }
        return result;
    }

    String[] splitToTokens(String line, boolean removeEmpty) {
        List<String> list = splitToTokensList(line, removeEmpty);
        String[] array = new String[list.size()];
        return list.toArray(array);

    }

    String[] splitToTokens(String line) {
        return splitToTokens(line, false);
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

    private boolean checkDirective(String line, String directiveName) {
        if (!line.startsWith(directiveName)) {
            return false;
        }
        if (line.length() == directiveName.length()) {
            return true;
        }
        char ch = line.charAt(directiveName.length());
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '(';
    }

    private String removeCommentsAndSpaces(String s) {
        s = s.trim();
        int index = s.indexOf(';');
        if (index >= 0) {
            s = s.substring(0, index).trim();
        }
        index = s.indexOf("//");
        if (index >= 0) {
            s = s.substring(0, index).trim();
        }
        return s;
    }

    private void startProc(String name) throws SyntaxException {
        checkName(name);
        if (currentProcedure != null) {
            error("nested .proc: '" + name + "'");
        }
        currentProcedure = new Procedure(name);
        procedures.put(name, currentProcedure);
        output.add(name + ":");
    }

    private void endProc(String empty) throws SyntaxException {
        if (!empty.isEmpty()) {
            error("extra characters in line");
        }
        if (currentProcedure == null) {
            error("start .proc directive not found");
        }
        currentProcedure = null;
    }

    private void processStartLoop(String line, String args) throws SyntaxException {
        if (args.endsWith(":")) {
            args = args.substring(0, args.length()-1).trim();
        }
        if (args.length() < 3) {
            error("expression expected");
        }
        if (!(args.startsWith("(") && args.endsWith(")"))) {
            error("expected ()");
        }
        args = args.substring(1, args.length()-1).trim();
        Block block = new Block(Block.TYPE_LOOP, splitToTokens(args), lineNumber);
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
            StringBuilder sb = createStringBuilderWithSpaces(line);
            String val = block.args.get(2);
            if (currentProcedure != null) {
                String resolve = currentProcedure.resolveVariable(val);
                if (resolve != null) {
                    val = resolve;
                }
            }
            if (block.args.size() == 3 && ParserUtils.isRegister(val)) {
                sb.append("mov\t").append(reg).append(", ").append(val);
            } else {
                sb.append("ldi\t").append(reg).append(", ");
                for (int i = 2; i < block.args.size(); i++) {
                    sb.append(block.args.get(i));
                }
            }
            sb.append("\t\t; ").append(block.args.get(0));
            output.add(sb.toString());
        }
        output.add(block.label + ":");
    }

    private StringBuilder createStringBuilderWithSpaces(String line) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb;
    }

    private void processEndLoop(String line, String empty) throws SyntaxException {
        if (!empty.isEmpty()) {
            error("extra characters in line");
        }
        if (blocks.empty()) {
            error(".loop not found");
        }
        Block block = blocks.pop();
        if (block.type != Block.TYPE_LOOP) {
            error(".loop not found");
        }
        String spaces = createStringBuilderWithSpaces(line).toString();

        output.add(spaces + "dec\t" + block.reg + "\t\t; " + block.args.get(0));
        output.add(spaces + "brne\t" + block.label);
    }


    private void processExtern(String line, String args) throws SyntaxException {
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
            List<String> names = splitToTokensList(varNames, true);
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


    private void processDefine(String line, String args) {
        String split[] = splitToTokens(args);
        if (split.length >= 1) {
            String name = split[0];
            String value = args.substring(name.length()).trim();
            Constant c = new Constant(name, value, Constant.Type.DEFINE);
            constants.put(name, c);
        }
        output.add(line);
    }


    private void procArgs(String args) throws SyntaxException {
        if (currentProcedure == null) {
            error(".args can be defined in .proc block only");
        }
        String[] split = args.split(",");
        for (String s : split) {
            s = s.trim();
            String nv[] = s.split("\\(");
            if (nv.length != 2 && !nv[1].endsWith(")")) {
                error("wrong argument: " + s);
            }
            String name = nv[0];
            String reg = nv[1].substring(0, nv[1].length()-1);
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
        }
    }


    private void use(String str) throws SyntaxException {
        String uses[] = str.split(",");
        for (String use : uses) {
            use = use.trim();
            String args[] = use.split("\\s+");
            if (args.length != 3 || !args[1].toLowerCase().equals("as")) {
                error("wrong .use syntax ");
            }
            String regName = args[0];
            String aliasName = args[2];
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
    }

    private void def(String str) throws SyntaxException {
        String uses[] = str.split(",");
        for (String use : uses) {
            use = use.trim();
            String[] args = splitToTokens(use, true);//use.split("\\s+");
            if (args.length != 3 || !args[1].toLowerCase().equals("=")) {
                error("wrong .def syntax ");
            }
            String regName = args[2];
            String aliasName = args[0];
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


    List<String> getOutput() {
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
        String[] tokens = splitToTokens(constant.value);
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
        String[] tokens = splitToTokens(expr);
        StringBuilder builder = new StringBuilder();
        for (String s : tokens) {
            if (isConstant(s)) {
                s = makeConstExpression(s);
            }
            builder.append(s);
        }
        return ParserUtils.isConstExpression(builder.toString());
    }

    boolean isConstExpressionTokens(List<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String s : tokens) {
            if (isConstant(s)) {
                s = makeConstExpression(s);
            }
            builder.append(s);
        }
        return ParserUtils.isConstExpression(builder.toString());
    }
}
