package ru.trolsoft.asmext;


import java.io.*;
import java.util.*;

class Parser {
    Procedure currentProcedure;
    private int lineNumber;
    private List<String> output = new ArrayList<>();
    private final Compiler compiler = new Compiler(this);
    Map<String, Procedure> procedures = new HashMap<>();
    Map<String, Variable> variables = new HashMap<>();
    Stack<Block> blocks = new Stack<>();

    Parser() {

    }


    void parse(File file) throws IOException, SyntaxException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            parse(reader);
        }
    }

    private void parse(BufferedReader reader) throws IOException, SyntaxException {
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
        if (trimLine.isEmpty()) {
            output.add(line);
        } else if (checkDirective(trimLine, ".use")) {
            use(trimLine.substring(".use".length()).trim());
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
            startLoop(line, trimLine.substring(".loop".length()).trim());
        } else if (checkDirective(trimLine, ".endloop")) {
            endLoop(line, trimLine.substring(".endloop".length()).trim());
        } else if (checkDirective(trimLine, ".extern")) {
            extern(line, trimLine.substring(".extern".length()).trim());
        } else {
            processLine(line);
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


    String[] splitToTokens(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, " \t,.+-*/=():", true);
        List<String> result = new ArrayList<>();
        boolean commentStarted = false;
        while (tokenizer.hasMoreElements()) {
            String next = tokenizer.nextToken();
            String prev = result.isEmpty() ? null : result.get(result.size()-1);
            if (prev != null && next.trim().isEmpty() && prev.trim().isEmpty()) {
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
        String[] array = new String[result.size()];
        return result.toArray(array);
    }


    private Alias resolveProcAlias(String aliasName) {
        if (currentProcedure == null) {
            return null;
        }
        Alias alias = currentProcedure.uses.get(aliasName);
        if (alias != null) {
            return alias;
        }
        return currentProcedure.args.get(aliasName);
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

    private void startLoop(String line, String args) throws SyntaxException {
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

    private void endLoop(String line, String empty) throws SyntaxException {
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


    private void extern(String line, String args) throws SyntaxException {
        // check procedure
        int indx = args.indexOf('(');
        if (indx > 0) {
            String procArgs = args.substring(indx);
            if (!procArgs.endsWith(")")) {
                error("') not found");
            }
            procArgs = procArgs.substring(1, procArgs.length()-1).trim();
            String procName = args.substring(0, indx).trim();

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
                Alias alias = createAlias(name, reg);
                if (proc.hasArg(name)) {
                    error("duplicate argument '" + name + "'");
                }
                proc.addArg(alias);
            } // for
            procedures.put(procName, proc);
            output.add(".extern " + procName + "\t; " + procArgs);
            return;
        }
        // check variable
        indx = args.indexOf(':');
        if (indx > 0) {
            String varName = args.substring(0, indx).trim();
            String varType = args.substring(indx+1).trim().toLowerCase();
            if (variables.containsKey(varName)) {
                error("Variable already defined: " + varName);
            }
            int type = ParserUtils.getTypeSize(varType);
            if (type < 0) {
                error("Invalid variable type: " + varType);
            }
            Variable var = new Variable(varName, type);
            variables.put(varName, var);
            output.add(".extern " + varName + "\t; " + varType);
            return;
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
        if (!ParserUtils.isRegister(name)) {
            error("register expected: " + name);
        }
    }

    private void error(String msg) throws SyntaxException {
        throw new SyntaxException(msg).line(lineNumber);
    }


    List<String> getOutput() {
        return output;
    }

    public Variable getVariable(String name) {
        return variables.get(name);
    }
}
