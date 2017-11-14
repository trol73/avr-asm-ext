package ru.trolsoft.asmext;


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Parser {
    private Proc currentProc;
    private int lineNumber;
    private List<String> output = new ArrayList<>();
    private final Compiler compiler = new Compiler();


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

    private void parseLine(String line) throws SyntaxException {
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
        } else if (trimLine.startsWith("@") && currentProc != null) {
            localLabel(trimLine.substring(1).trim());
        } else {
            processLine(line);
        }


    }

    private void localLabel(String label) {
        output.add(resolveLocalLabel(label));
    }

    private String resolveLocalLabel(String label) {
        return currentProc.name + "__" + label;
    }

    private void processLine(String line) {
        String tokens[] = splitToTokens(line);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            Alias alias = resolveProcAlias(token);
            if (alias != null) {
                tokens[i] = alias.register;
            } else if (token.startsWith("@") && currentProc != null) {
                tokens[i] = resolveLocalLabel(token.substring(1));
            }
        }
        StringBuilder outLine = new StringBuilder();
        compiler.compile(tokens, outLine);
        output.add(outLine.toString());
    }


    private String[] splitToTokens(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, " \t,.+-*/=", true);
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
        return currentProc != null ? currentProc.uses.get(aliasName) : null;
    }

    private boolean checkDirective(String line, String directiveName) {
        if (!line.startsWith(directiveName)) {
            return false;
        }
        if (line.length() == directiveName.length()) {
            return true;
        }
        char ch = line.charAt(directiveName.length());
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
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
        if (currentProc != null) {
            error("nested .proc: '" + name + "'");
        }
        currentProc = new Proc(name);
        output.add(name + ":");
    }

    private void endProc(String empty) throws SyntaxException {
        if (!empty.isEmpty()) {
            error("extra characters in line");
        }
        if (currentProc == null) {
            error("start .proc directive not found");
        }
        currentProc = null;
    }

    private void use(String str) throws SyntaxException {
        String args[] = str.split("\\s+");
        if (args.length != 3 || !args[1].toLowerCase().equals("as")) {
            error("wrong .use syntax ");
        }
        String regName = args[0];
        checkRegister(regName);
        String aliasName = args[2];
        checkName(aliasName);
        Alias alias = new Alias(aliasName, regName);
        if (currentProc != null) {
            if (currentProc.uses.keySet().contains(aliasName)) {
                error("alias already defined for this procedure");
            }
            currentProc.uses.put(aliasName, alias);
        }
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
}
