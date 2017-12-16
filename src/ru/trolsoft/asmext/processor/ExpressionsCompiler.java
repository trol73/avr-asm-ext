package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.Constant;
import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.data.Variable.Type;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.processor.ParserUtils.*;

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

    boolean compile(TokenString src, OutputFile out) throws CompileException {
        List<String> syntaxTokens = src.getTokens();
        ParserUtils.mergeTokens(syntaxTokens);
        if (syntaxTokens.size() < 2) {
            return false;
        }
        String operation = syntaxTokens.get(1);
        String dest = syntaxTokens.get(0);
        if (ParserUtils.isRegister(dest) && syntaxTokens.size() != 3) {
            if ("+=".equals(operation) || "-=".equals(operation)) {
                syntaxTokens.set(1, "=");
                syntaxTokens.add(2, dest);
                String newOperation = operation.substring(0, 1);
                syntaxTokens.add(3, newOperation);
                operation = "=";
            }
        }
        boolean pairMatch = isPair(dest) && ("++".equals(operation) || "--".equals(operation) || "+=".equals(operation) || "-=".equals(operation));
        if (ParserUtils.isRegister(dest) || pairMatch) {
            switch (operation) {
                case "=":
                    syntaxTokens.remove(0);
                    syntaxTokens.remove(0);
                    moveToReg(src, dest, syntaxTokens, out);
                    return true;
                case "++":
                case "--":
                    if (syntaxTokens.size() != 2) {
                        throw new CompileException();
                    }
                    incDecReg(src, dest, operation, out);
                    return true;
                case "+=":
                case "-=":
                    if (pairMatch && syntaxTokens.size() != 3) {
                        if (syntaxTokens.size() < 3) {
                            unsupportedOperationError();
                        }
                        syntaxTokens.remove(0);
                        syntaxTokens.remove(0);
                        addPair(src, dest, operation, syntaxTokens, out);
                    } else if (syntaxTokens.size() == 3) {
                        addReg(src, dest, operation, syntaxTokens.get(2), out);
                    } else {
                        throw new CompileException();
                    }
                    return true;
                case "&=":
                    if (pairMatch && syntaxTokens.size() != 3) {
                        unsupportedOperationError();
                    } else if (syntaxTokens.size() == 3) {
                        //!!!!! syntaxTokens.size() == 19
                        andReg(src, dest, syntaxTokens.get(2), out);
                    } else {
                        throw new CompileException();
                    }
                    return true;
                case "|=":
                    if (pairMatch && syntaxTokens.size() != 3) {
                        unsupportedOperationError();
                    } else if (syntaxTokens.size() == 3) {
                        orReg(src, dest, syntaxTokens.get(2), out);
                    } else {
                        throw new CompileException();
                    }
                    return true;

                case ".":
                    return compileGroup(src, syntaxTokens, out);
            }
        } else if (isVar(dest)) {
            moveToVar(src, dest, syntaxTokens, out);
            return true;
        } else if (isPair(dest)) {
            if ("=".equals(operation)) {
                syntaxTokens.remove(0); // pair
                syntaxTokens.remove(0); // =
                moveToPair(src, dest, syntaxTokens, out);
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

    private void moveToReg(TokenString src, String destReg, List<String> tokens, OutputFile out) throws CompileException {
        if (tokens.isEmpty()) {
            throw new CompileException("empty expression");
        }
        String firstArg = mergeTokensInList(tokens);
        if (ParserUtils.isRegister(firstArg)) {
            if (!firstArg.equalsIgnoreCase(destReg)) {
                out.appendCommand(src, "mov", destReg, firstArg);
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
                        out.appendCommand(src, "neg", destReg);
                    } else {
                        out.appendCommand(src, "mov", destReg, secondArg);
                        out.appendCommand(src, "neg", destReg);
                    }
                } else {
                    // x = -123
                    if (!isConstExpression(secondArg) && !parser.isConstant(secondArg)) {
                        throw new CompileException("unexpected expression: " + secondArg);
                    }
                    String s = parser.isConstant(secondArg) ? parser.makeConstExpression(secondArg) : secondArg;
                    out.appendCommand(src, "ldi", destReg, "-"+wrapToBrackets(s));
                }
                tokens.remove(0);
            } else {    // x = const | var | expression
                try {
                    val = ParserUtils.parseValue(firstArg);
                } catch (NumberFormatException e) {
                    val = -1;
                }
                if (val == 0) {
                    out.appendCommand(src, "clr", destReg);
                } else {
                    if (isVar(firstArg)) {
                        int size = getVarSize(firstArg);
                        if (size == 1) {
                            out.appendCommand(src, "lds", destReg, firstArg);
                        } else {
                            unsupportedOperationError();
                        }
                    } else {
                        if (parser.isConstant(firstArg)) {
                            Constant c = parser.getConstant(firstArg);
                            String right = parser.makeConstExpression(c);
//                                    parser.gcc && c.type == Constant.Type.EQU ? c.value : firstArg;
 //                           right = parser.isConstant(right) ? parser.makeConstExpression(secondArg) : secondArg;

                            out.appendCommand(src, "ldi", destReg, right);
                        } else if (isConstExpression(firstArg)) {
                            out.appendCommand(src, "ldi", destReg, firstArg);
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
                            out.appendCommand(src, "inc", destReg);
                        } else {
                            out.appendCommand(src, "subi", destReg, "-" + wrapToBrackets(arg));
                        }
                    } else if (parser.isConstant(arg)) {
                        String s = parser.makeConstExpression(arg);
                        out.appendCommand(src, "subi", destReg, "-" + wrapToBrackets(s));
                    } else if (ParserUtils.isRegister(arg)) {
                        out.appendCommand(src, "add", destReg, arg);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                case "-":
                    if (isConstExpression(arg)) {
                        Integer val = ParserUtils.isNumber(arg) ? ParserUtils.parseValue(arg) : null;
                        if (val != null && val == 1) {
                            out.appendCommand(src, "dec", destReg);
                        } else {
                            out.appendCommand(src, "subi", destReg, wrapToBrackets(arg));
                        }
                    } else if (ParserUtils.isRegister(arg)) {
                        out.appendCommand(src, "sub", destReg, arg);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                case "&":
                    if (isConstExpression(arg)) {
                        if (!ParserUtils.isNumber(arg)) {
                            arg = wrapToBrackets(arg);
                        }
                        out.appendCommand(src, "andi", destReg, arg);
                    } else if (ParserUtils.isRegister(arg)) {
                        out.appendCommand(src, "and", destReg, arg);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                case "|":
                    if (isConstExpression(arg)) {
                        if (!ParserUtils.isNumber(arg)) {
                            arg = wrapToBrackets(arg);
                        }
                        out.appendCommand(src, "ori", destReg, arg);
                    } else if (ParserUtils.isRegister(arg)) {
                        out.appendCommand(src, "or", destReg, arg);
                    } else {
                        throw new CompileException("wrong argument: " + arg);
                    }
                    break;
                default:
                    unsupportedOperationError(operation);

            }
        }
    }


    private void moveToVar(TokenString src, String varName, List<String> syntaxTokens, OutputFile out) throws CompileException {
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
            out.appendCommand(src, "sts", varName, reg);
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
                out.appendCommand(src, "sts", varAddr, reg);
            }
        }
    }

    private void moveToPair(TokenString src, String pair, List<String> args, OutputFile out) throws CompileException {
        if (args.size() == 1) {
            String arg = args.get(0);
            if (isConstExpression(arg)) {
                out.appendCommand(src, "ldi", pair + "L", loByte(arg));
                out.appendCommand(src, "ldi", pair + "H", hiByte(arg));
            } else if (getVarType(arg) == Type.POINTER) {
                String low = loVarPtr(arg, Type.POINTER, false);
                String high = hiVarPtr(arg, Type.POINTER, false);
                out.appendCommand(src, "ldi", pair + "L", low);
                out.appendCommand(src, "ldi", pair + "H", high);
            } else if (getVarType(arg) == Type.PRGPTR) {
                String low = loVarPtr(arg, Type.PRGPTR, false);
                String high = hiVarPtr(arg, Type.PRGPTR, false);
                out.appendCommand(src, "ldi", pair + "L", low);
                out.appendCommand(src, "ldi", pair + "H", high);
            } else {
                unsupportedOperationError();
            }
            return;
        }

        if (isRegisterWord(args)) {
            out.appendCommand(src, "movw", pair + "L", args.get(2));
        } else if (args.size() == 3 && checkRegisterGroup(args)) {
            out.appendCommand(src, "mov", pair + "L", args.get(2));
            out.appendCommand(src, "mov", pair + "H", args.get(0));
        } else {
            List<String> constants = new ArrayList<>();
            StringBuilder argsStr = new StringBuilder();
            for (String arg : args) {
                if (getVarType(arg) == Type.POINTER) {
                    constants.add(arg);
                }
                argsStr.append(arg);
            }
            if (!parser.gcc && constants.size() == 1 && isConstExpressionTokens(args, constants)) {
                String arg = argsStr.toString();
                String low = loVarPtr(arg, Type.POINTER, false);
                String high = hiVarPtr(arg, Type.POINTER, false);
                out.appendCommand(src, "ldi", pair + "L", low);
                out.appendCommand(src, "ldi", pair + "H", high);
            } else {
                unsupportedOperationError();
            }
        }
    }



    private boolean compileGroup(TokenString src, List<String> syntaxTokens, OutputFile out) throws CompileException {
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
                if (getVarType(right) == Type.POINTER) {
                    String low = loVarPtr(right, Type.POINTER, isAdd);
                    String high = hiVarPtr(right, Type.POINTER, isAdd);
                    out.appendCommand(src, "subi", rl, low);
                    out.appendCommand(src, "sbci", rh, high);
                } else if (getVarType(right) == Type.PRGPTR) {
                    String low = loVarPtr(right, Type.PRGPTR, isAdd);
                    String high = hiVarPtr(right, Type.PRGPTR, isAdd);
                    out.appendCommand(src, "subi", rl, low);
                    out.appendCommand(src, "sbci", rh, high);
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
                    out.appendCommand(src, "lds", reg, varAddr);
                }
            } else {
                unsupportedOperationError(operator);
            }
        } else if (checkRegisterGroup(syntaxTokens) && isPair(right)) {
            if ("=".equals(operator)) {
                if (isRegisterWord(syntaxTokens)) {
                    // r1.r0 = Y -> movw	r0, YL
                    out.appendCommand(src, "movw", syntaxTokens.get(2), right + "L");
                } else {
                    out.appendCommand(src, "mov", syntaxTokens.get(2), right + "L");
                    out.appendCommand(src, "mov", syntaxTokens.get(0), right + "H");
                }
            } else if ("-=".equals(operator)) {
                out.appendCommand(src, "sub", syntaxTokens.get(2), right + "L");
                out.appendCommand(src, "sbc", syntaxTokens.get(0), right + "H");
            } else if ("+=".equals(operator)) {
                out.appendCommand(src, "add", syntaxTokens.get(2), right + "L");
                out.appendCommand(src, "adc", syntaxTokens.get(0), right + "H");
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
                    out.appendCommand(src, instruction, syntaxTokens.get(i), rightTokens.get(i));
                }
            } else if ("+=".equals(operator)) {
                for (int i = syntaxTokens.size()-1; i >= 0; i -= 2) {
                    String instruction = i == syntaxTokens.size()-1 ? "add" : "adc";
                    out.appendCommand(src, instruction, syntaxTokens.get(i), rightTokens.get(i));
                }
            } else if ("=".equals(operator)) {
                // TODO !!!! optimize with movw !!!!
                for (int i = syntaxTokens.size()-1; i >= 0; i -= 2) {
                    out.appendCommand(src, "mov", syntaxTokens.get(i), rightTokens.get(i));
                }
            } else {
                unsupportedOperationError();
            }
        } else {
            if (!isConstExpression(right) && !parser.isConstant(right)) {
                throw new CompileException("unexpected expression: " + right);
            }
            if ("=".equals(operator)) {
                groupAssignConst(src, syntaxTokens, right, out);
            } else if ("+=".equals(operator) || "-=".equals(operator)) {
                groupAddSub(src, syntaxTokens, operator, right, out);
            } else {
                unsupportedOperationError();
            }
        }
        return true;
    }


    private void groupAssignConst(TokenString srcLine, List<String> src, String right, OutputFile out) {
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
            out.appendCommand(srcLine, "ldi", reg, v);
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
            if (type == Type.POINTER) {
                return negative ? "-LOW(" + name + ")" : "LOW(" + name + ")";
            } else if (type == Type.PRGPTR) {
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
            if (type == Type.POINTER) {
                return negative ? "-HIGH(" + name + ")" : "HIGH(" + name + ")";
            } else if (type == Type.PRGPTR) {
                return negative ? "-HIGH(2*" + name + ")" : "HIGH(2*" + name + ")";
            } else {
                throw new RuntimeException("unexpected pointer type");
            }
        }
    }


    private void groupAddSub(TokenString srcLine, List<String> src, String operator, String right, OutputFile out) throws CompileException {
        if (isRegisterWord(src)) {
            String instruction = "+=".equals(operator) ? "adiw" : "sbiw";
            out.appendCommand(srcLine, instruction, src.get(2), right);
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



    private void incDecReg(TokenString src, String destReg, String operation, OutputFile out) throws CompileException {
        if (isPair(destReg)) {
            String instruction = "++".equals(operation) ? "adiw" : "sbiw";
            out.appendCommand(src, instruction, destReg + "L", "1");
        } else {
            String instruction = "++".equals(operation) ? "inc" : "dec";
            out.appendCommand(src, instruction, destReg);
        }
    }

    private void addReg(TokenString src, String destReg, String operation, String argument, OutputFile out) throws CompileException {
        boolean isAdd = "+=".equals(operation);
        if (ParserUtils.isRegister(argument)) {
            String instruction = isAdd ? "add" : "sub";
            out.appendCommand(src, instruction, destReg, argument);
        } else if (isPair(destReg)) {
            String instruction = isAdd ? "adiw" : "sbiw";
            if (isConstExpression(argument) || parser.isConstant(argument)) {
                argument = wrapToBrackets(argument);
                out.appendCommand(src, instruction, destReg + "L", argument);
            } else if (getVarType(argument) == Type.POINTER) {
                String argLo = loVarPtr(argument, Type.POINTER, isAdd);
                String argHi = hiVarPtr(argument, Type.POINTER, isAdd);
                out.appendCommand(src, "subi", destReg + "L", argLo);
                out.appendCommand(src, "sbci", destReg + "H", argHi);
            } else if (getVarType(argument) == Type.PRGPTR) {
                String argLo = loVarPtr(argument, Type.PRGPTR, isAdd);
                String argHi = hiVarPtr(argument, Type.PRGPTR, isAdd);
                out.appendCommand(src, "subi", destReg + "L", argLo);
                out.appendCommand(src, "sbci", destReg + "H", argHi);
            } else if (isPair(argument)) {
                if (isAdd) {
                    out.appendCommand(src, "add", destReg + "L", argument + "L");
                    out.appendCommand(src, "adc", destReg + "H", argument + "H");
                } else {
                    out.appendCommand(src, "sub", destReg + "L", argument + "L");
                    out.appendCommand(src, "sbc", destReg + "H", argument + "H");
                }
            } else {
                throw new CompileException("unexpected expression: " + argument);
            }
        } else if (isConstExpression(argument)) {
            argument = wrapToBrackets(argument);
            if (isAdd) {
                argument = "-" + argument;
            }
            out.appendCommand(src, "subi", destReg, argument);
        } else {
            throw new CompileException(" addReg " + destReg + ", " + argument);
        }
    }

    private void addPair(TokenString src, String dest, String operation, List<String> args, OutputFile out) throws CompileException {
        if (isPair(dest) && checkRegisterGroup(args)) {
            if ("+=".equals(operation)) {
                out.appendCommand(src, "add", dest + "L", args.get(2));
                out.appendCommand(src, "adc", dest + "H", args.get(0));
            } else {
                out.appendCommand(src, "sub", dest + "L", args.get(2));
                out.appendCommand(src, "sbc", dest + "H", args.get(0));
            }
        } else {
            unsupportedOperationError();
        }
    }

    private void andReg(TokenString src, String destReg, String argument, OutputFile out) throws CompileException {
        if (isRegister(argument)) {
            out.appendCommand(src, "and", destReg, argument);
        } else if (isConstExpression(argument)) {
            out.appendCommand(src, "andi", destReg, argument);
        } else {
            unsupportedOperationError();
        }
    }

    private void orReg(TokenString src, String destReg, String argument, OutputFile out) throws CompileException {
        if (isRegister(argument)) {
            out.appendCommand(src, "or", destReg, argument);
        } else if (isConstExpression(argument)) {
            out.appendCommand(src, "ori", destReg, argument);
        } else {
            unsupportedOperationError();
        }
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
        if (tokens == null || tokens.isEmpty() || tokens.size() % 2 == 0) {
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

    private boolean isConstExpressionTokens(List<String> tokens) {
        return parser.isConstExpressionTokens(tokens, null);
    }

    private boolean isConstExpressionTokens(List<String> tokens, List<String> constants) {
        return parser.isConstExpressionTokens(tokens, constants);
    }

    private static void unsupportedOperationError() throws CompileException {
        throw new CompileException("unsupported operation");
    }

    private static void unsupportedOperationError(String s) throws CompileException {
        throw new CompileException("unsupported operation: " + s);
    }

}
