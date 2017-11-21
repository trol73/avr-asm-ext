package ru.trolsoft.asmext;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

class Compiler {

    private final Parser parser;

    Compiler(Parser parser) {
        this.parser = parser;
    }

    void compile(String src, String[] tokens, StringBuilder out) throws SyntaxException {
        if (tokens.length == 0) {
            return;
        }
        String firstToken = null;
        for (String token : tokens) {
            if (!token.trim().isEmpty()) {
                firstToken = token.toLowerCase();
                break;
            }
        }
        if (firstToken == null) {
            compileDefault(src, tokens, out, null);
            return;
        }
        switch (firstToken) {
            case "push":
            case "pop":
                compilePushPop(tokens, out);
                break;
            case "call":
            case "rcall":
            case "jmp":
            case "rjmp":
                compileCall(src, tokens, out, firstToken);
                break;
            default:
                compileDefault(src, tokens, out, firstToken);
        }
    }


    private Procedure parseProcCallArgs(List<NamedPair> args, String[] tokens, String firstToken) throws SyntaxException {
        final int STATE_WAIT_INSTRUCTION = 0;
        final int STATE_WAIT_PROC_NAME = 1;
        final int STATE_WAIT_ARGS = 2;
        final int STATE_ARG_NAME = 3;
        final int STATE_WAIT_NAME_SEPARATOR = 4;
        final int STATE_ARG_VALUE = 5;
//        final int STATE_DONE_ARG = 7;
        final int STATE_DONE = 6;
        int state = STATE_WAIT_INSTRUCTION;
        String procedureName = null;
        String argName = null;
        String argValue = "";
        Procedure procedure = null;

        for (String s : tokens) {
            if (s.trim().isEmpty() || ParserUtils.isComment(s)) {
                continue;
            }
            switch (state) {
                case STATE_WAIT_INSTRUCTION:
                    if (!s.equals(firstToken)) {
                        throw new SyntaxException("internal error");
                    }
                    state = STATE_WAIT_PROC_NAME;
                    break;
                case STATE_WAIT_PROC_NAME:
                    procedureName = s;
                    procedure = parser.procedures.get(procedureName);
                    state = STATE_WAIT_ARGS;
                    break;
                case STATE_WAIT_ARGS:
                    if (s.equals("(")) {
                        state = STATE_ARG_NAME;
                        if (procedure == null) {
                            throw new SyntaxException("undefined procedure: \"" + procedureName + '"');
                        }
                    } else if (!ParserUtils.isComment(s)) {
                        throw new SyntaxException("wrong call syntax");
                    }
                    break;
                case STATE_ARG_NAME:
                    argName = s;
                    argValue = "";
                    state = STATE_WAIT_NAME_SEPARATOR;
                    break;
                case STATE_WAIT_NAME_SEPARATOR:
                    if (")".equals(s) && procedure.args.size() == 1) {
                        String name = procedure.args.values().iterator().next().name;
                        args.add(new NamedPair(name, argName));
                    } else if (!":".equals(s)) {
                        throw new SyntaxException("wrong call syntax");
                    }
                    state = STATE_ARG_VALUE;
                    break;
                case STATE_ARG_VALUE:
                    if (",".equals(s)) {
                        args.add(new NamedPair(argName, argValue));
                        state = STATE_ARG_NAME;
                        argValue = "";
                    } else if (")".equals(s)) {
                        args.add(new NamedPair(argName, argValue));
                        state = STATE_DONE;
                        argValue = "";
                    } else {
                        argValue += s;
                    }
                    break;
//                case STATE_DONE_ARG:
//                    if (",".equals(s)) {
//                        state = STATE_ARG_NAME;
//                    } else if (")".equals(s)) {
//                        state = STATE_DONE;
//                    }
//                    break;
                case STATE_DONE:
                    if (!ParserUtils.isComment(s)) {
                        throw new SyntaxException("wrong call syntax");
                    }
                    break;
            }
        }

        if (procedureName == null || args.isEmpty()) {
            return null;
        }

        if (procedure == null) {
            throw new SyntaxException("undefined procedure: \"" + procedureName + '"');
        }
        return procedure;
    }

    private void checkCallArguments(Procedure procedure, List<NamedPair> args) throws SyntaxException {
        for (NamedPair arg : args) {
            if (!procedure.args.containsKey(arg.name)) {
                throw new SyntaxException("wrong argument: " + arg.name);
            }
        }
    }

    private void compileCall(String src, String[] tokens, StringBuilder out, String firstToken) throws SyntaxException {
        List<NamedPair> args = new ArrayList<>();
        Procedure procedure = parseProcCallArgs(args, tokens, firstToken);
        if (procedure != null) {
            String firstSpaces = tokens.length > 0 && tokens[0].trim().isEmpty() ? tokens[0] : "";
            checkCallArguments(procedure, args);
            out.append("; ").append(src).append("\n");

            for (NamedPair arg : args) {
                String argName = arg.name;
                String regName = procedure.args.get(argName).register;
                String value = arg.value;
                if (!regName.equalsIgnoreCase(value)) {
                    out.append(firstSpaces);
                    addArgumentAssign(out, argName, regName, value);
                } else {
                    out.append(firstSpaces).append("; ").append(argName).append(" = ").append(value).append("\n");
                }
            } // for args
            out.append(firstSpaces).append(firstToken).append("\t").append(procedure.name).append("\n");
            return;
        } // if procedure

        compileDefault(src, tokens, out, firstToken);

    }

    private void addArgumentAssign(StringBuilder out, String argName, String regName, String value) throws SyntaxException {
        StringTokenizer tokenizer = new StringTokenizer(value, "+-", true);
        String[] t = new String[2 + tokenizer.countTokens()];
        t[0] = regName;
        t[1] = "=";
        int i = 2;
        while (tokenizer.hasMoreTokens()) {
            t[i++] = tokenizer.nextToken();
        }
        try {
            if (!ExpressionsCompiler.compile(t, out)) {
                throw new SyntaxException("wrong value: '" + value + "'");
            }
            if (out.length() > 0) {
                out.deleteCharAt(out.length() - 1);
            }
            out.append("\t; ").append(argName).append(" = ").append(value).append("\n");
        } catch (ExpressionsCompiler.CompileException e) {
            throw new SyntaxException(e.getMessage());
        }
    }


    private void compilePushPop(String[] tokens, StringBuilder out) {
        List<String> regs = new ArrayList<>();
        for (String token : tokens) {
            if (ParserUtils.isRegister(token)) {
                regs.add(token);
            }
        }
        if (regs.size() <= 1) {
            for (String token : tokens) {
                out.append(token);
            }
        } else {
            int regNum = 0;
            for (String reg : regs) {
                regNum++;
                for (int i = 0; i < tokens.length; i++) {
                    String token = tokens[i];
                    if (token == null) {
                        continue;
                    }
                    boolean isReg = ParserUtils.isRegister(token);
                    if (!isReg || token.equalsIgnoreCase(reg)) {
                        out.append(token);
                        if (isReg) {
                            tokens[i] = null;
                            if (regNum < regs.size() && i < tokens.length-1 && tokens[i+1].trim().isEmpty()) {
                                tokens[i+1] = null;
                            }
                        }
                    }
                }
                out.append('\n');
            }
        }
    }


    private void compileDefault(String src, String[] tokens, StringBuilder out, String firstToken) throws SyntaxException {
        if (!ParserUtils.isInstruction(firstToken)) {
            try {
                StringBuilder tempOut = new StringBuilder();
                if (ExpressionsCompiler.compile(tokens, tempOut)) {
                    if (tempOut.length() > 0) {
                        tempOut.deleteCharAt(tempOut.length() - 1);
                    }
                    out.append("; ").append(src).append("\n").append(tempOut);
                    return;
                }
            } catch (ExpressionsCompiler.CompileException e) {
                if (e.getMessage() != null) {
                    throw new SyntaxException(e.getMessage());
                } else {
                    throw new SyntaxException("expression error");
                }
            }
        }
        for (String token : tokens) {
            out.append(token);
        }
    }

}
