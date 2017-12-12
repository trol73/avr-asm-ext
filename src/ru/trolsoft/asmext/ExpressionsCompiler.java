package ru.trolsoft.asmext;

import ru.trolsoft.asmext.data.Constant;
import ru.trolsoft.asmext.data.Variable;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.ParserUtils.isRegister;
import static ru.trolsoft.asmext.ParserUtils.isPair;
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
                    if (pairMatch && syntaxTokens.size() != 3) {
                        if (syntaxTokens.size() < 3) {
                            unsupportedOperationError();
                        }
                        syntaxTokens.remove(0);
                        syntaxTokens.remove(0);
                        addPair(firstSpace, dest, operation, syntaxTokens, out);
                    } else if (syntaxTokens.size() == 3) {
                        addReg(firstSpace, dest, operation, syntaxTokens.get(2), out);
                    } else {
                        throw new CompileException();
                    }
                    return true;
                case ".":
                    return compileGroup(firstSpace, syntaxTokens, out);
            }
        } else if (isVar(dest)) {
            moveToVar(firstSpace, dest, syntaxTokens, out);
            return true;
        } else if (isPair(dest)) {
            if ("=".equals(operation)) {
                syntaxTokens.remove(0); // pair
                syntaxTokens.remove(0); // =
                moveToPair(firstSpace, dest, syntaxTokens, out);
                return true;
            }
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
                            unsupportedOperationError();
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
                    unsupportedOperationError(operation);

            }
        }
    }


    private void moveToVar(String firstSpace, String varName, List<String> syntaxTokens, StringBuilder out) throws CompileException {
        if (syntaxTokens.size() < 3 || !"=".equals(syntaxTokens.get(1))) {
            unsupportedOperationError();
        }
        int varSize = getVarSize(varName);
        if (varSize == 1) {
            if (syntaxTokens.size() != 3) {
                unsupportedOperationError();
            }
            String reg = syntaxTokens.get(2);
            if (!ParserUtils.isRegister(reg)) {
                throw new CompileException("register expected: " + reg);
            }
            addInstruction(firstSpace, "sts", varName, reg, out);
        } else {
            if (syntaxTokens.size() != 2 + varSize + varSize-1) {
                unsupportedOperationError("sizes mismatch");
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

    private void moveToPair(String firstSpace, String pair, List<String> args, StringBuilder out) throws CompileException {
        if (args.size() == 1) {
            String arg = args.get(0);
            if (isConstExpression(arg)) {
                addInstruction(firstSpace, "ldi", pair + "L", loByte(arg), out);
                addInstruction(firstSpace, "ldi", pair + "H", hiByte(arg), out);
            } else if (getVarType(arg) == Variable.Type.POINTER) {
                String low = loVarPtr(arg, Variable.Type.POINTER, false);
                String high = hiVarPtr(arg, Variable.Type.POINTER, false);
                addInstruction(firstSpace, "ldi", pair + "L", low, out);
                addInstruction(firstSpace, "ldi", pair + "H", high, out);
            } else if (getVarType(arg) == Variable.Type.PRGPTR) {
                String low = loVarPtr(arg, Variable.Type.PRGPTR, false);
                String high = hiVarPtr(arg, Variable.Type.PRGPTR, false);
                addInstruction(firstSpace, "ldi", pair + "L", low, out);
                addInstruction(firstSpace, "ldi", pair + "H", high, out);
            } else {
                unsupportedOperationError();
            }
            return;
        }
        if (isRegisterWord(args)) {
            addInstruction(firstSpace, "movw", pair + "L", args.get(2), out);
        } else if (args.size() == 3 && checkRegisterGroup(args)) {
            addInstruction(firstSpace, "mov", pair + "L", args.get(2), out);
            addInstruction(firstSpace, "mov", pair + "H", args.get(0), out);
        } else {
            unsupportedOperationError();
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
            if (operator == null && (".".equals(s) || isRegister(s))) {
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
            unsupportedOperationError();
        }
        List<String> rightTokens = new ArrayList<>();
        while (syntaxTokens.size() >= operatorIndex) {
            String s = syntaxTokens.remove(syntaxTokens.size()-1);
            rightTokens.add(0, s);
        }
        if (!rightTokens.isEmpty()) {
            rightTokens.remove(0);
        }
        String right = rightBuilder.toString();
        if (!checkRegisterGroup(syntaxTokens)) {
            throw new CompileException("wrong left side expression");
        }

        if (isVar(right)) {
            if ("+=".equals(operator) || "-=".equals(operator)) {
                boolean isAdd = "+=".equals(operator);
                String rh = syntaxTokens.get(0);
                String rl = syntaxTokens.get(2);
                if (getVarType(right) == Variable.Type.POINTER) {
                    String low = loVarPtr(right, Variable.Type.POINTER, isAdd);
                    String high = hiVarPtr(right, Variable.Type.POINTER, isAdd);
                    addInstruction(firstSpace, "subi", rl, low, out);
                    addInstruction(firstSpace, "sbci", rh, high, out);
                } else if (getVarType(right) == Variable.Type.PRGPTR) {
                    String low = loVarPtr(right, Variable.Type.PRGPTR, isAdd);
                    String high = hiVarPtr(right, Variable.Type.PRGPTR, isAdd);
                    addInstruction(firstSpace, "subi", rl, low, out);
                    addInstruction(firstSpace, "sbci", rh, high, out);
                } else {
                    unsupportedOperationError();
                }
            } else if ("=".equals(operator)) {
                int varSize = getVarSize(right);
                if (syntaxTokens.size() != varSize * 2 - 1) {
                    unsupportedOperationError("sizes mismatch");
                }
                for (int i = 0; i < varSize; i++) {
                    String varAddr = i < varSize - 1 ? right + "+" + (varSize - i - 1) : right;
                    String reg = syntaxTokens.get(i * 2);
                    addInstruction(firstSpace, "lds", reg, varAddr, out);
                }
            } else {
                unsupportedOperationError(operator);
            }
        } else if (checkRegisterGroup(syntaxTokens) && isPair(right)) {
            if ("=".equals(operator)) {
                if (isRegisterWord(syntaxTokens)) {
                    // r1.r0 = Y -> movw	r0, YL
                    addInstruction(firstSpace, "movw", syntaxTokens.get(2), right + "L", out);
                } else {
                    addInstruction(firstSpace, "mov", syntaxTokens.get(2), right + "L", out);
                    addInstruction(firstSpace, "mov", syntaxTokens.get(0), right + "H", out);
                }
            } else if ("-=".equals(operator)) {
                addInstruction(firstSpace, "sub", syntaxTokens.get(2), right + "L", out);
                addInstruction(firstSpace, "sbc", syntaxTokens.get(0), right + "H", out);
            } else if ("+=".equals(operator)) {
                addInstruction(firstSpace, "add", syntaxTokens.get(2), right + "L", out);
                addInstruction(firstSpace, "adc", syntaxTokens.get(0), right + "H", out);
            } else {
                unsupportedOperationError();
            }
        } else if (checkRegisterGroup(syntaxTokens) && checkRegisterGroup(rightTokens)) {
            int len1 = syntaxTokens.size()/2+1;
            int len2 = rightTokens.size()/2+1;
            if (len1 != len2) {
                throw new CompileException("arguments sizes mismatch");
            }
            if ("-=".equals(operator)) {
                for (int i = syntaxTokens.size() - 1; i >= 0; i -= 2) {
                    String instruction = i == syntaxTokens.size() - 1 ? "sub" : "sbc";
                    addInstruction(firstSpace, instruction, syntaxTokens.get(i), rightTokens.get(i), out);
                }
            } else if ("+=".equals(operator)) {
                for (int i = syntaxTokens.size()-1; i >= 0; i -= 2) {
                    String instruction = i == syntaxTokens.size()-1 ? "add" : "adc";
                    addInstruction(firstSpace, instruction, syntaxTokens.get(i), rightTokens.get(i), out);
                }
            } else if ("=".equals(operator)) {
                // TODO !!!! optimize with movw !!!!
                for (int i = syntaxTokens.size()-1; i >= 0; i -= 2) {
                    addInstruction(firstSpace, "mov", syntaxTokens.get(i), rightTokens.get(i), out);
                }
            } else {
                unsupportedOperationError();
            }
        } else {
            if (!isConstExpression(right) && !parser.isConstant(right)) {
                throw new CompileException("unexpected expression: " + right);
            }
            if ("=".equals(operator)) {
                groupAssignConst(firstSpace, syntaxTokens, right, out);
            } else if ("+=".equals(operator) || "-=".equals(operator)) {
                groupAddSub(firstSpace, syntaxTokens, operator, right, out);
            } else {
                unsupportedOperationError();
            }
        }
        return true;
    }


    private void groupAssignConst(String firstSpace, List<String> src, String right, StringBuilder out) {
        int groupSize = src.size() / 2 + 1;
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
            // TODO support not-GCC mode
            addInstruction(firstSpace, "ldi", reg, v, out);
        }
    }

    private String hiByte(String val) {
        if (parser.gcc) {
            return wrapToBrackets(val + " >> 8");
        } else {
            return "HIGH(" + val + ")";
        }
    }

    private String loByte(String val) {
        if (parser.gcc) {
            return wrapToBrackets(val + " & 0xFF");
        } else {
            return "LOW(" + val + ")";
        }
    }

    private String loVarPtr(String name, Variable.Type type, boolean negative) {
        if (parser.gcc) {
            return negative ? "lo8(-(" + name + "))" : "lo8(" + name + ")";
        } else {
            if (type == Variable.Type.POINTER) {
                return negative ? "-LOW(" + name + ")" : "LOW(" + name + ")";
            } else if (type == Variable.Type.PRGPTR) {
                return negative ? "-LOW(2*" + name + ")" : "LOW(2*" + name + ")";
            } else {
                throw new RuntimeException("unexpected pointer type");
            }
        }
    }

    private String hiVarPtr(String name, Variable.Type type, boolean negative) {
        if (parser.gcc) {
            return negative ? "hi8(-(" + name + "))" : "hi8(" + name + ")";
        } else {
            if (type == Variable.Type.POINTER) {
                return negative ? "-HIGH(" + name + ")" : "HIGH(" + name + ")";
            } else if (type == Variable.Type.PRGPTR) {
                return negative ? "-HIGH(2*" + name + ")" : "HIGH(2*" + name + ")";
            } else {
                throw new RuntimeException("unexpected pointer type");
            }
        }
    }


    private void groupAddSub(String firstSpace, List<String> src, String operator, String right, StringBuilder out) throws CompileException {
        if (isRegisterWord(src)) {
            String instruction = "+=".equals(operator) ? "adiw" : "sbiw";
            addInstruction(firstSpace, instruction, src.get(2), right, out);
        } else {
            unsupportedOperationError();
        }
    }


    private static boolean isRegisterWord(List<String> arg) {
        if (arg.size() != 3) {
            return false;
        }
        String rh = arg.get(0).toLowerCase();
        String rl = arg.get(2).toLowerCase();
        if (isRegister(rh) && isRegister(rl) && rh.startsWith("r") && rl.startsWith("r")) {
            try {
                int nh = Integer.parseInt(rh.substring(1));
                int nl = Integer.parseInt(rl.substring(1));
                if (nl % 2 == 0 && nh == nl + 1) {
                    return true;
                }
            } catch (Exception ignore) {}
        }
        return false;
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
        boolean isAdd = "+=".equals(operation);
        if (ParserUtils.isRegister(argument)) {
            String instruction = isAdd ? "add" : "sub";
            addInstruction(firstSpace, instruction, destReg, argument, out);
        } else if (isPair(destReg)) {
            String instruction = isAdd ? "adiw" : "sbiw";
            if (isConstExpression(argument) || parser.isConstant(argument)) {
                argument = wrapToBrackets(argument);
                addInstruction(firstSpace, instruction, destReg + "L", argument, out);
            } else if (getVarType(argument) == Variable.Type.POINTER) {
                String argLo = loVarPtr(argument, Variable.Type.POINTER, isAdd);
                String argHi = hiVarPtr(argument, Variable.Type.POINTER, isAdd);
                addInstruction(firstSpace, "subi", destReg + "L", argLo, out);
                addInstruction(firstSpace, "sbci", destReg + "H", argHi, out);
            } else if (getVarType(argument) == Variable.Type.PRGPTR) {
                String argLo = loVarPtr(argument, Variable.Type.PRGPTR, isAdd);
                String argHi = hiVarPtr(argument, Variable.Type.PRGPTR, isAdd);
                addInstruction(firstSpace, "subi", destReg + "L", argLo, out);
                addInstruction(firstSpace, "sbci", destReg + "H", argHi, out);
            } else if (isPair(argument)) {
                if (isAdd) {
                    addInstruction(firstSpace, "add", destReg + "L", argument + "L", out);
                    addInstruction(firstSpace, "adc", destReg + "H", argument + "H", out);
                } else {
                    addInstruction(firstSpace, "sub", destReg + "L", argument + "L", out);
                    addInstruction(firstSpace, "sbc", destReg + "H", argument + "H", out);
                }
            } else {
                throw new CompileException("unexpected expression: " + argument);
            }
        } else if (isConstExpression(argument)) {
            argument = wrapToBrackets(argument);
            if (isAdd) {
                argument = "-" + argument;
            }
            addInstruction(firstSpace, "subi", destReg, argument, out);
        } else {
            throw new CompileException(" addReg " + destReg + ", " + argument);
        }
    }

    private void addPair(String firstSpace, String dest, String operation, List<String> args, StringBuilder out) throws CompileException {
        if (isPair(dest) && checkRegisterGroup(args)) {
            if ("+=".equals(operation)) {
                addInstruction(firstSpace, "add", dest + "L", args.get(2), out);
                addInstruction(firstSpace, "adc", dest + "H", args.get(0), out);
            } else {
                addInstruction(firstSpace, "sub", dest + "L", args.get(2), out);
                addInstruction(firstSpace, "sbc", dest + "H", args.get(0), out);
            }
        } else {
            unsupportedOperationError();
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

    private Variable getVar(String name) {
        return parser != null ? parser.getVariable(name) : null;
    }

    private boolean isVar(String name) {
        return getVar(name) != null;
    }

    private int getVarSize(String name) {
        Variable var = getVar(name);
        return var != null ? var.getSize() : -1;
    }

    private Variable.Type getVarType(String name) {
        Variable var = getVar(name);
        return var != null ? var.type : null;
    }

    private boolean checkRegisterGroup(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
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

    private boolean isConstExpression(String s) {
        return parser.isConstExpression(s);
    }

    private boolean isConstExpressionTokens(List<String> list) {
        return parser.isConstExpressionTokens(list);
    }

    private static void unsupportedOperationError() throws CompileException {
        throw new CompileException("unsupported operation");
    }

    private static void unsupportedOperationError(String s) throws CompileException {
        throw new CompileException("unsupported operation: " + s);
    }

}
