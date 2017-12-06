package ru.trolsoft.asmext;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.ParserUtils.isRegister;
import static ru.trolsoft.asmext.ParserUtils.wrapToBrackets;

class ExpressionsCompiler {

    private Parser parser;

    ExpressionsCompiler(Parser parser) {
        this.parser = parser;
    }

    static class CompileException extends Exception {

        CompileException() {
            super();
        }

        CompileException(String msg) {
            super(msg);
        }
    }

    boolean compile(String[] tokens, StringBuilder out) throws CompileException {
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
        ParserUtils.mergeTokens(syntaxTokens);
        if (syntaxTokens.size() < 2) {
            return false;
        }
        String firstSpace = tokens[0].trim().isEmpty() ? tokens[0] : "";
        String operation = syntaxTokens.get(1);
        String dest = syntaxTokens.get(0);
        if (ParserUtils.isRegister(dest) && syntaxTokens.size() != 3 && ("+=".equals(operation) || "-=".equals(operation))) {
            syntaxTokens.set(1, "=");
            syntaxTokens.add(2, dest);
            syntaxTokens.add(3, "+=".equals(operation) ? "+" : "-");
            operation = "=";
        }

        boolean pairMatch = isPair(dest) && ("++".equals(operation) || "--".equals(operation) || "+=".equals(operation) || "-=".equals(operation));
        if (ParserUtils.isRegister(dest) || pairMatch) {
            switch (operation) {
                case "=":
                    syntaxTokens.remove(0);
                    syntaxTokens.remove(0);
                    moveToReg(firstSpace, dest, syntaxTokens, out);
                    return true;
                case "++":
                case "--":
                    if (syntaxTokens.size() != 2) {
                        throw new CompileException();
                    }
                    incDecReg(firstSpace, dest, operation, out);
                    return true;
                case "+=":
                case "-=":
                    if (syntaxTokens.size() != 3) {
                        throw new CompileException();
                    }
                    addReg(firstSpace, dest, operation, syntaxTokens.get(2), out);
                    return true;
                case ".":
                    return compileGroup(firstSpace, syntaxTokens, out);
            }
        } else if (isVar(dest)) {
            moveToVar(firstSpace, dest, syntaxTokens, out);
            return true;
        }
        return false;
    }

    private String mergeTokensInList(List<String> tokens) {
        String result;
        if (tokens.size() > 1 && isConstExpressionTokens(tokens)) {
            StringBuilder builder = new StringBuilder();
            for (String s : tokens) {
                if (parser.isConstant(s)) {
                    builder.append(parser.makeConstExpression(s));
                } else {
                    builder.append(s);
                }
            }
            result = builder.toString();
            tokens.clear();
            tokens.add(result);
        } else {
            result = tokens.get(0);
        }
        return result;
    }

    private void moveToReg(String firstSpaces, String destReg, List<String> tokens, StringBuilder out) throws CompileException {
        if (tokens.isEmpty()) {
            throw new CompileException("empty expression");
        }
        String firstArg = mergeTokensInList(tokens);
        if (ParserUtils.isRegister(firstArg)) {
            if (!firstArg.equalsIgnoreCase(destReg)) {
                addInstruction(firstSpaces, "mov", destReg, firstArg, out);
            }
        } else {
            // x = -3
            // x = -x
            // x = abc
            // x = var
            int val;
            if ("-".equals(firstArg) && tokens.size() > 1) {    // x = -y
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
                    if (!isConstExpression(secondArg) && !parser.isConstant(secondArg)) {
                        throw new CompileException("unexpected expression: " + secondArg);
                    }
                    String s = parser.isConstant(secondArg) ? parser.makeConstExpression(secondArg) : secondArg;
                    addInstruction(firstSpaces, "ldi", destReg, "-"+wrapToBrackets(s), out);
                }
                tokens.remove(0);
            } else {    // x = const | var | expression
                try {
                    val = ParserUtils.parseValue(firstArg);
                } catch (NumberFormatException e) {
                    val = -1;
                }
                if (val == 0) {
                    addInstruction(firstSpaces, "clr", destReg, null, out);
                } else {
                    if (isVar(firstArg)) {
                        int size = getVarSize(firstArg);
                        if (size == 1) {
                            addInstruction(firstSpaces, "lds", destReg, firstArg, out);
                        } else {
                            throw new CompileException("unsupported expression");
                        }
                    } else {
                        if (parser.isConstant(firstArg)) {
                            Constant c = parser.getConstant(firstArg);
                            String right = parser.makeConstExpression(c);
//                                    parser.gcc && c.type == Constant.Type.EQU ? c.value : firstArg;
 //                           right = parser.isConstant(right) ? parser.makeConstExpression(secondArg) : secondArg;

                            addInstruction(firstSpaces, "ldi", destReg, right, out);
                        } else if (isConstExpression(firstArg)) {
                            addInstruction(firstSpaces, "ldi", destReg, firstArg, out);
                        } else {
                            throw new CompileException("unexpected expression: " + firstArg);
                        }
                    }
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

        // tyy to combine all tokens in line
        String firstOperation = tokens.get(0);
        mergeTokensInList(tokens);
        if (tokens.size() == 1) {
            String expr = tokens.get(0).substring(firstOperation.length());
            tokens.clear();
            tokens.add(firstOperation);
            tokens.add(expr);
        }

        for (int i = 0; i < tokens.size()/2; i++) {
            String operation = tokens.get(i*2);
            String arg = tokens.get(i*2+1);
            switch (operation) {
                case "+":
                    if (isConstExpression(arg)) {
                        Integer val = ParserUtils.isNumber(arg) ? ParserUtils.parseValue(arg) : null;
                        if (val != null && val == 1) {
                            addInstruction(firstSpaces, "inc", destReg, null, out);
                        } else {
                            addInstruction(firstSpaces, "subi", destReg, "-" + wrapToBrackets(arg), out);
                        }
                    } else if (parser.isConstant(arg)) {
                        String s = parser.makeConstExpression(arg);
                        addInstruction(firstSpaces, "subi", destReg, "-" + wrapToBrackets(s), out);
                    } else if (ParserUtils.isRegister(arg)) {
                        addInstruction(firstSpaces, "add", destReg, arg, out);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                case "-":
                    if (isConstExpression(arg)) {
                        Integer val = ParserUtils.isNumber(arg) ? ParserUtils.parseValue(arg) : null;
                        if (val != null && val == 1) {
                            addInstruction(firstSpaces, "dec", destReg, null, out);
                        } else {
                            addInstruction(firstSpaces, "subi", destReg, wrapToBrackets(arg), out);
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


    private void moveToVar(String firstSpace, String varName, List<String> syntaxTokens, StringBuilder out) throws CompileException {
        if (syntaxTokens.size() < 3 || !"=".equals(syntaxTokens.get(1))) {
            throw new CompileException("wrong operation");
        }
        int varSize = getVarSize(varName);
        if (varSize == 1) {
            if (syntaxTokens.size() != 3) {
                throw new CompileException("wrong operation");
            }
            String reg = syntaxTokens.get(2);
            if (!ParserUtils.isRegister(reg)) {
                throw new CompileException("register expected: " + reg);
            }
            addInstruction(firstSpace, "sts", varName, reg, out);
        } else {
            if (syntaxTokens.size() != 2 + varSize + varSize-1) {
                throw new CompileException("wrong operation, sizes mismatch");
            }
            for (int i = 0; i < varSize; i++) {
                String reg = syntaxTokens.get(2 + i*2);
                if (!ParserUtils.isRegister(reg)) {
                    throw new CompileException("register expected: " + reg);
                }
                String varAddr = i < varSize-1 ? varName + "+" + (varSize-i-1) : varName;
                addInstruction(firstSpace, "sts", varAddr, reg, out);
            }
        }
    }


    private boolean compileGroup(String firstSpace, List<String> syntaxTokens, StringBuilder out) throws CompileException {
        if (syntaxTokens.size() < 3) {
            return false;
        }
        String operator = null;
        StringBuilder rightBuilder = new StringBuilder();
        int operatorIndex = 1;
        for (String s : syntaxTokens) {
            if (operator == null && ".".equals(s) || isRegister(s)) {
                operatorIndex++;
                continue;
            }
            if (operator == null) {
                operator = s;
            } else {
                rightBuilder.append(s);
            }
        }
        if (operatorIndex < 0) {
            throw new CompileException("wrong expression");
        }
        while (syntaxTokens.size() >= operatorIndex) {
            syntaxTokens.remove(syntaxTokens.size()-1);
        }
        String right = rightBuilder.toString();
        if (!checkRegisterGroup(syntaxTokens)) {
            throw new CompileException("wrong left side expression");
        }
        if (isVar(right)) {
            if (!"=".equals(operator)) {
                throw new CompileException("unsupported operation: " + operator);
            }
            int varSize = getVarSize(right);
            if (syntaxTokens.size() != varSize*2 - 1) {
                throw new CompileException("wrong operation, sizes mismatch");
            }
            for (int i = 0; i < varSize; i++) {
                String varAddr = i < varSize - 1 ? right + "+" + (varSize - i - 1) : right;
                String reg = syntaxTokens.get(i*2);
                addInstruction(firstSpace, "lds", reg, varAddr, out);
            }
        } else {
            if (!isConstExpression(right) && !parser.isConstant(right)) {
                throw new CompileException("unexpected expression: " + right);
            }
            if ("=".equals(operator)) {
                groupAssignConst(firstSpace, syntaxTokens, right, out);
            } else if ("+=".equals(operator) || "-=".equals(operator)) {
                groupAddSub(firstSpace, syntaxTokens, operator, right, out);
            }
        }
        return true;
    }


    private void groupAssignConst(String firstSpace, List<String> src, String right, StringBuilder out) {
        int groupSize = src.size();
        boolean isNumber = ParserUtils.isNumber(right);
        int val = isNumber ? ParserUtils.parseValue(right) : -1;
        for (int i = 0; i < groupSize; i++) {
            String reg = src.get(i * 2);
            String v;
            if (isNumber) {
                int part = (val >> (8 * (groupSize - i - 1))) & 0xff;
                v = "0x" + Integer.toHexString(part);
            } else {
                v = "(" + right + " >> " + 8 * (groupSize - i - 1) + ") & 0xff";
            }
            addInstruction(firstSpace, "ldi", reg, v, out);
        }

    }

    private void groupAddSub(String firstSpace, List<String> src, String operator, String right, StringBuilder out) throws CompileException {
        int groupSize = src.size();
        if (groupSize == 3) {
            String r1 = src.get(0).toLowerCase();
            String r2 = src.get(2).toLowerCase();
            if (isRegister(r1) && isRegister(r2) && ".".equals(src.get(1)) && r1.startsWith("r") && r2.startsWith("r")) {
                try {
                    int n1 = Integer.parseInt(r1.substring(1));
                    int n2 = Integer.parseInt(r2.substring(1));
                    if (n1 == n2 - 1) {
                        String instruction = "+=".equals(operator) ? "adiw" : "sbiw";
                        addInstruction(firstSpace, instruction, r2, right, out);
                        return;
                    }
                } catch (Exception ignore) {}
            }
        }
        throw new CompileException("unsupported operation");
    }



    private void incDecReg(String firstSpace, String destReg, String operation, StringBuilder out) throws CompileException {
        if (isPair(destReg)) {
            String instruction = "++".equals(operation) ? "adiw" : "sbiw";
            addInstruction(firstSpace, instruction, destReg + "L", "1", out);
        } else {
            String instruction = "++".equals(operation) ? "inc" : "dec";
            addInstruction(firstSpace, instruction, destReg, null, out);
        }
    }

    private void addReg(String firstSpace, String destReg, String operation, String argument, StringBuilder out) throws CompileException {
        if (ParserUtils.isRegister(argument)) {
            String instruction = "+=".equals(operation) ? "add" : "sub";
            addInstruction(firstSpace, instruction, destReg, argument, out);
        } else if (isPair(destReg)) {
            String instruction = "+=".equals(operation) ? "adiw" : "sbiw";
            if (isConstExpression(argument) || parser.isConstant(argument)) {
                argument = wrapToBrackets(argument);
                addInstruction(firstSpace, instruction, destReg + "L", argument, out);
            } else {
                throw new CompileException("unexpected expression: " + argument);
            }
        } else if (isConstExpression(argument)) {
            argument = wrapToBrackets(argument);
            if ("+=".equals(operation)) {
                argument = "-" + argument;
            }
            addInstruction(firstSpace, "subi", destReg, argument, out);
        } else {
            throw new CompileException(" addReg " + destReg + ", " + argument);
        }
    }

    private void addInstruction(String firstSpaces, String operation, String destReg, String argument, StringBuilder out) {
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

    private boolean isVar(String name) {
        return parser != null && parser.getVariable(name) != null;
    }

    private int getVarSize(String name) {
        Variable var = parser != null ? parser.getVariable(name) : null;
        return var != null ? var.getSize() : -1;
    }

    private boolean checkRegisterGroup(List<String> tokens) {
        for (int i = 0; i < tokens.size()/2+1; i++) {
            if (!ParserUtils.isRegister(tokens.get((2*i)))) {
                return false;
            }
            if (2*i + 1 < tokens.size() && !".".equals(tokens.get(2*i+1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPair(String name) {
        return "X".equals(name) || "Y".equals(name) || "Z".equals(name);
    }

    private boolean isConstExpression(String s) {
        return parser.isConstExpression(s);
    }

    private boolean isConstExpressionTokens(List<String> list) {
        return parser.isConstExpressionTokens(list);
    }

}
