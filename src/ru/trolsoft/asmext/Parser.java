package ru.trolsoft.asmext;


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Parser {
    private Proc currentProc;
    private int lineNumber;
    private List<String> output = new ArrayList<>();


    public Parser() {

    }


    public void parse(File file) throws IOException, SyntaxException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            parse(reader);
        }
    }

    public void parse(BufferedReader reader) throws IOException, SyntaxException {
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
        } else {
            processLine(line);
        }


    }

    private void processLine(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, " \t,.+-*/=", true);
        StringBuilder outLine = new StringBuilder();
        while (tokenizer.hasMoreElements()) {
            String token = tokenizer.nextToken();
            Alias alias = resolveProcAlias(token);
            if (alias != null) {
                token = alias.register;
            }
            outLine.append(token);
        }
        output.add(outLine.toString());
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
        currentProc = new Proc(name);
        output.add(name + ":");
    }

    private void endProc(String empty) throws SyntaxException {
        if (!empty.isEmpty()) {
            throw new SyntaxException("extra characters in line").line(lineNumber);
        }
        if (currentProc == null) {
            throw new SyntaxException("start .proc directive not found").line(lineNumber);
        }
        currentProc = null;
    }

    private void use(String str) throws SyntaxException {
        String args[] = str.split("\\s+");
        if (args.length != 3 || !args[1].toLowerCase().equals("as")) {
            throw new SyntaxException("wrong .use syntax ").line(lineNumber);
        }
        String regName = args[0];
        checkRegister(regName);
        String aliasName = args[2];
        checkName(aliasName);
        Alias alias = new Alias(aliasName, regName);
        if (currentProc != null) {
            if (currentProc.uses.keySet().contains(aliasName)) {
                throw new SyntaxException("alias already defined for this procedure").line(lineNumber);
            }
            currentProc.uses.put(aliasName, alias);
        }
    }


    private void checkName(String name) throws SyntaxException {
        if (!ParserUtils.isValidName(name)) {
            throw new SyntaxException("wrong name: " + name).line(lineNumber);
        }
        if (ParserUtils.isRegister(name)) {
            throw new SyntaxException(" wrong name: " + name + " (register name)").line(lineNumber);
        }
        if (ParserUtils.isInstruction(name)) {
            throw new SyntaxException(" wrong name: " + name + " (instruction name)").line(lineNumber);
        }
    }

    private void checkRegister(String name) throws SyntaxException {
        if (!ParserUtils.isRegister(name)) {
            throw new SyntaxException("register expected: " + name);
        }
    }


    public List<String> getOutput() {
        return output;
    }
}
