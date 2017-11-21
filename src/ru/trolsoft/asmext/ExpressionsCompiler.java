package ru.trolsoft.asmext;

import java.util.ArrayList;
import java.util.List;

public class ExpressionsCompiler {

    static class CompileException extends Exception {

        CompileException() {
            super();
        }

        CompileException(String msg) {
            super(msg);
        }
    }

    static boolean compile(String[] tokens, StringBuilder out) throws CompileException {
        List<String> syntaxTokens = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String s = tokens[i].trim();
            if (s.isEmpty() || ParserUtils.isComment(s)) {
                continue;
            }
            String prevToken = i > 0 ? tokens[i-1] : null;
            if (("+".equals(prevToken) || "-".equals(prevToken)) && ("=".equals(s) || s.equals(prevToken))) {
                syntaxTokens.set(syntaxTokens.size()-1, prevToken + s);
            } else {
                syntaxTokens.add(s);
            }
        }
        if (syntaxTokens.size() < 2) {
            return false;
        }
        String firstSpace = tokens[0].trim().isEmpty() ? tokens[0] : "";
        String operation = syntaxTokens.get(1);
        if (ParserUtils.isRegister(syntaxTokens.get(0))) {
            String destReg = syntaxTokens.get(0);
            switch (operation) {
                case "=":
                    syntaxTokens.remove(0);
                    syntaxTokens.remove(0);
                    moveToReg(firstSpace, destReg, syntaxTokens, out);
                    return true;
                case "++":
                case "--":
                    if (syntaxTokens.size() != 2) {
                        throw new CompileException();
                    }
                    incDecReg(firstSpace, destReg, operation, out);
                    return true;
                case "+=":
                case "-=":
                    if (syntaxTokens.size() != 3) {
                        throw new CompileException();
                    }
                    addReg(firstSpace, destReg, operation, syntaxTokens.get(2), out);
                    return true;
            }
        }
        return false;
    }



    private static void moveToReg(String firstSpaces, String destReg, List<String> tokens, StringBuilder out) throws CompileException {
        if (tokens.isEmpty()) {
            throw new CompileException("empty expression");
        }
        String firstArg = tokens.get(0);
        if (ParserUtils.isRegister(firstArg)) {
            if (!firstArg.equalsIgnoreCase(destReg)) {
                addInstruction(firstSpaces, "mov", destReg, firstArg, out);
            }
        } else {
            // x = -3
            // x = -x
            int val;
            if ("-".equals(firstArg) && tokens.size() > 1) {
                String secondArg = tokens.get(1);
                // x = -y
                if (ParserUtils.isRegister(secondArg)) {
                    if (destReg.equalsIgnoreCase(secondArg)) {
                        addInstruction(firstSpaces, "neg", destReg, null, out);
                    } else {
                        addInstruction(firstSpaces, "mov", destReg, secondArg, out);
                        addInstruction(firstSpaces, "neg", destReg, null, out);
                    }
                } else {
                    // x = -123
                    addInstruction(firstSpaces, "ldi", destReg, "-"+secondArg, out);
                }
                tokens.remove(0);
            } else {
                try {
                    val = ParserUtils.parseValue(firstArg);
                } catch (NumberFormatException e) {
                    val = -1;
                }
                if (val == 0) {
                    addInstruction(firstSpaces, "clr", destReg, null, out);
                } else {
                    addInstruction(firstSpaces, "ldi", destReg, firstArg, out);
                }
            }
        }
        if (tokens.size() == 1) {
            return;
        }
        tokens.remove(0);
        if (tokens.size() % 2 != 0) {
            throw new CompileException();
        }
        for (int i = 0; i < tokens.size()/2; i++) {
            String operation = tokens.get(i*2);
            String arg = tokens.get(i*2+1);
            switch (operation) {
                case "+":
                    if (ParserUtils.isNumber(arg)) {
                        int val = ParserUtils.parseValue(arg);
                        if (val == 1) {
                            addInstruction(firstSpaces, "inc", destReg, null, out);
                        } else {
                            addInstruction(firstSpaces, "subi", destReg, "-" + arg, out);
                        }
                    } else if (ParserUtils.isRegister(arg)) {
                        addInstruction(firstSpaces, "add", destReg, arg, out);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                case "-":
                    if (ParserUtils.isNumber(arg)) {
                        int val = ParserUtils.parseValue(arg);
                        if (val == 1) {
                            addInstruction(firstSpaces, "dec", destReg, null, out);
                        } else {
                            addInstruction(firstSpaces, "subi", destReg, arg, out);
                        }
                    } else if (ParserUtils.isRegister(arg)) {
                        addInstruction(firstSpaces, "sub", destReg, arg, out);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                default:
                    throw new CompileException("wrong operation: " + operation);

            }
        }
    }


    private static void incDecReg(String firstSpace, String destReg, String operation, StringBuilder out) throws CompileException {
        String instruction = "++".equals(operation) ? "inc" : "dec";
        addInstruction(firstSpace, instruction, destReg, null, out);
    }

    private static void addReg(String firstSpace, String destReg, String operation, String argument, StringBuilder out) throws CompileException {
        if (ParserUtils.isRegister(argument)) {
            String instruction = "+=".equals(operation) ? "add" : "sub";
            addInstruction(firstSpace, instruction, destReg, argument, out);
        } else if (ParserUtils.isNumber(argument)) {
            if ("+=".equals(operation)) {
                argument = "-" + argument;
            }
            addInstruction(firstSpace, "subi", destReg, argument, out);
        } else {
            throw new CompileException();
        }
    }



    private static void addInstruction(String firstSpaces, String operation, String destReg, String argument, StringBuilder out) {
        out.append(firstSpaces);
        out.append(operation);
        out.append("\t");
        out.append(destReg);
        if (argument != null) {
            out.append(", ");
            out.append(argument);
        }
        out.append("\n");
    }


    public static void main(String[] args) throws CompileException {
        String[] tokens = new String[] {
          "\t", "r31", " ", " = ", "r16", "+", "0x10"
        };
        StringBuilder out = new StringBuilder();
        compile(tokens, out);

        tokens = new String[] {
            "   ", "r20", " ", " = ", " ", "-0x12", "+", "", "1"
        };
        compile(tokens, out);
        tokens = new String[] {
                "   ", "r0", " ", "++",
        };
        compile(tokens, out);
        tokens = new String[] {
                "   ", "r0", " ", "--",
        };
        compile(tokens, out);

        tokens = new String[] {
                "   ", "r0", " ", "-=", "1"
        };
        compile(tokens, out);
        System.out.println(out);
    }


}
