package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.data.Variable.Type;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.List;

class ExpressionsCompiler {

    Parser parser;

    ExpressionsCompiler(Parser parser) {
        this.parser = parser;
    }

    static class CompileException extends Exception {

        CompileException(String msg) {
            super(msg);
        }
    }

    boolean compile(TokenString src, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() < 2) {
            return false;
        }
        Token operation = expr.get(1);
        Token dest = expr.get(0);
        if (dest.isRegister() && expr.size() != 3) {
            if (operation.isOperator("+=", "-=")) {
                expr.set(1, new Token(Token.TYPE_OPERATOR, "="));
                expr.add(2, dest);
                String newOperation = operation.asString().substring(0, 1);
                expr.add(3, new Token(Token.TYPE_OPERATOR, newOperation));
            }
        }

        int moveCount = expr.operatorsCount("=");
        try {
            if (moveCount > 1) {
                return compileMultiMove(src, expr, out, moveCount);
            }
            return compileExpr(src, expr, out);
        } catch (RuntimeException e) {
            throw new CompileException("internal error: " + e.getMessage());
        }
    }


    private boolean compileMultiMove(TokenString src, Expression expr, OutputFile out, int moveCount) throws CompileException {
        // a = b = c -> b = c, a = b
        if (expr.get(0).isOperator("=")) {
            unexpectedExpressionError();
        }
        Expression lastExpression = new Expression();
        for (int exrNumber = moveCount-1; exrNumber > 0; exrNumber--) {
            lastExpression.clear();
            int len = 0;
            boolean moveFound = false;
            for (int i = expr.size()-1; i >= 0; i--) {
                Token t = expr.get(i);
                if (t.isOperator("=")) {
                    if (moveFound) {
                        break;
                    }
                    moveFound = true;
                }
                if (!moveFound) {
                    len++;
                }
                lastExpression.add(0, t);
            }
            if (!compileExpr(src, lastExpression, out)) {
                return false;
            }
            expr.removeLast(len+1);
        } // for exprNumber
        return compileExpr(src, expr, out);
    }

    private boolean compileExpr(TokenString src, Expression expr, OutputFile out) throws CompileException {
        Token dest = expr.get(0);
        Token operation = expr.get(1);
        if (!operation.isOperator()) {
            return false;
        }
        switch (operation.asString()) {
            case "=":
                if (dest.isRegister()) {
                    expr.removeFirst(2);
                    moveToReg(src, dest, expr, out);
                } else if (dest.isRegGroup() || dest.isPair()) {
                    expr.removeFirst(2);
                    moveToGroupOrPair(src, dest, expr, out);
                } else if (dest.isVar()) {
                    expr.removeFirst(2);
                    moveToVar(src, dest, expr, out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "++":
            case "--":
                if (expr.size() != 2) {
                    unexpectedExpressionError();
                }
                incDecReg(src, dest, operation, out);
                return true;
            case "+=":
            case "-=":
                if (dest.isPair() || dest.isRegisterWord()) {
                    if (expr.size() < 3) {
                        unsupportedOperationError();
                    }
                    expr.removeFirst(2);
                    addPair(src, dest, operation, expr.get(0), out);
                } else if (dest.isRegGroup() && expr.size() == 3) {
                    Token arg = expr.get(2);
                    if (!arg.isRegGroup(dest.size())) {
                        unexpectedExpressionError();
                    }
                    addRegGroup(src, dest, operation, arg, out);
                } else if (dest.isRegister() && expr.size() == 3) {
                    addReg(src, dest, operation, expr.get(2), out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "&=":
                if (expr.size() != 3) {
                    unsupportedOperationError();
                } else if (expr.size() == 3) {
                    andReg(src, dest, expr.get(2), out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "|=":
                if (expr.size() != 3) {
                    unsupportedOperationError();
                } else if (expr.size() == 3) {
                    orReg(src, dest, expr.get(2), out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "<<=":
                if (expr.size() == 3) {
                    shlRegs(src, dest, expr.get(2), out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case ">>=":
                if (expr.size() == 3) {
                    shrRegs(src, dest, expr.get(2), out);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            default:
                unsupportedOperationError(operation);
        }
        return false;
    }


    private void tryToMergeConstants(Expression expr) throws CompileException {
        if (parser.gcc || expr.isEmpty() || !expr.get(0).isSomeString("BYTE1", "BYTE2", "BYTE3", "BYTE4", "HIGH", "LOW")) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(expr.get(0).asString());
        int bc = 0;
        int i = 1;
        while (i < expr.size()) {
            Token t = expr.get(i);
            if (t.isOperator("(")) {
                bc++;
            } else if (t.isOperator(")")) {
                bc--;
                if (bc == 0) {
                    i++;
                    sb.append(t.asString());
                    break;
                }
            }
            i++;
            sb.append(t.asString());
        }
        if (bc != 0) {
            unsupportedOperationError();
        }
        expr.removeFirst(i);
        Token t = new Token(Token.TYPE_CONST_EXPRESSION, sb.toString());
        expr.add(0, t);
    }


    private void moveToReg(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        if (expr.isEmpty()) {
            emptyExpressionError();
        }
        if (expr.size() > 1) {
            tryToMergeConstants(expr);
        }
        Token firstArg = expr.get(0);
        if (firstArg.isRegister()) {
            if (!firstArg.equals(dest)) {
                out.appendCommand(src, "mov", dest, firstArg);
            }
        } else {
            // x = -3
            // x = -x
            // x = abc
            // x = var
            if (firstArg.isOperator("-") && expr.size() > 1) {    // x = -y
                Token secondArg = expr.get(1);
                // x = -y
                if (secondArg.isRegister()) {
                    if (dest.equals(secondArg)) {
                        out.appendCommand(src, "neg", dest);
                    } else {
                        out.appendCommand(src, "mov", dest, secondArg);
                        out.appendCommand(src, "neg", dest);
                    }
                } else {
                    // x = -123
                    if (!secondArg.isAnyConst()) {
                        unexpectedExpressionError(secondArg);
                    }
                    Token s = secondArg.isConst() ? resolveConst(secondArg) : secondArg;
                    out.appendCommand(src, "ldi", dest, s.getNegativeExpr());
                }
                expr.removeFirst();
            } else {    // x = const | var | expression
                if (firstArg.isNumber() && firstArg.getNumberValue() == 0) {
                    out.appendCommand(src, "clr", dest);
                    return;
                } else {
                    if (firstArg.isVar()) {
                        int size = getVarSize(firstArg);
                        if (size == 1) {
                            out.appendCommand(src, "lds", dest, firstArg);
                        } else {
                            unsupportedOperationError();
                        }
                    } else {
                        if (firstArg.isConst()) {
                            out.appendCommand(src, "ldi", dest, resolveConst(firstArg));
                        } else if (firstArg.isAnyConst()) {
                            out.appendCommand(src, "ldi", dest, firstArg);
                        } else {
                            unexpectedExpressionError(firstArg);
                        }
                    }
                }
            }
        }
        if (expr.size() == 1) {
            return;
        }
        expr.removeFirst();


        if (expr.size() % 2 != 0) {
            unexpectedExpressionError();
        }

        for (int i = 0; i < expr.size()/2; i++) {
            Token operation = expr.get(i*2);
            Token arg = expr.get(i*2+1);
            switch (operation.asString()) {
                case "+":
                    if (arg.isAnyConst()) {
                        if (arg.isNumber() && arg.getNumberValue() == 1) {
                            out.appendCommand(src, "inc", dest);
                        } else {
                            out.appendCommand(src, "subi", dest, arg.getNegativeExpr());
                        }
                    } else if (arg.isRegister()) {
                        out.appendCommand(src, "add", dest, arg);
                    } else {
                        wrongArgumentError(arg);
                    }
                    break;
                case "-":
                    if (arg.isAnyConst()) {
                        if (arg.isNumber() && arg.getNumberValue() == 1) {
                            out.appendCommand(src, "dec", dest);
                        } else {
                            out.appendCommand(src, "subi", dest, arg.wrapToBrackets());
                        }
                    } else if (arg.isRegister()) {
                        out.appendCommand(src, "sub", dest, arg);
                    } else {
                        wrongArgumentError(arg);
                    }
                    break;
                case "&":
                    if (arg.isAnyConst()) {
                        out.appendCommand(src, "andi", dest, arg.wrapToBrackets());
                    } else if (arg.isRegister()) {
                        out.appendCommand(src, "and", dest, arg);
                    } else {
                        wrongArgumentError(arg);
                    }
                    break;
                case "|":
                    if (arg.isAnyConst()) {
                        out.appendCommand(src, "ori", dest, arg.wrapToBrackets());
                    } else if (arg.isRegister()) {
                        out.appendCommand(src, "or", dest, arg);
                    } else {
                        wrongArgumentError(arg);
                    }
                    break;
                default:
                    unsupportedOperationError(operation);

            }
        }
    }

    private Token resolveConst(Token name) {
        String s = parser.makeConstExpression(name.asString());
        if (ParserUtils.isNumber(s)) {
            return new Token(Token.TYPE_NUMBER, s);
        }
        return new Token(Token.TYPE_CONST, s);
    }


    private void moveToVar(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() != 1) {
            unsupportedOperationError();
        }
        Token arg = expr.get(0);
        int varSize = getVarSize(dest);
        if (arg.size() != varSize) {
            sizesMismatchError();
        }
        if (varSize == 1) {
            if (!arg.isRegister()) {
                unexpectedExpressionError("register expected: " + arg);
            }
            out.appendCommand(src, "sts", dest, arg);
        } else {
            for (int i = 0; i < varSize; i++) {
                String varAddress = i < varSize-1 ? dest + "+" + (varSize-i-1) : dest.asString();
                out.appendCommand(src, "sts", varAddress, arg.getReg(i).asString());
            }
        }
    }

    private void moveToGroupOrPair(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        Token arg = expr.get(0);
        if (expr.size() > 1) {
            if (!parser.gcc) {
                boolean canBeExpression = expr.operatorsCount("=", "!=", "+=", "-=", "&=", "|=", ">=", "<=", "<<=", ">>=") == 0;
                if (!canBeExpression) {
                    unsupportedOperationError();
                }
                Variable.Type varType = null;
                for (Token t : expr) {
                    if (t.isVar()) {
                        Variable.Type vt = getVarType(t);
                        if (vt != null && varType != null && vt != varType) {
                            unsupportedOperationError();
                        }
                        if (varType == null && vt != null) {
                            varType = vt;
                        }
                    }
                }
                Token merged = new Token(Token.TYPE_CONST_EXPRESSION, expr.toString());
                if (varType != null) {
                    String low = loVarPtr(merged, varType, false);
                    String high = hiVarPtr(merged, varType, false);
                    out.appendCommand(src, "ldi", dest.getPairLow(), low);
                    out.appendCommand(src, "ldi", dest.getPairHigh(), high);
                } else {
                    out.appendCommand(src, "ldi", dest.getPairLow(), loByte(merged));
                    out.appendCommand(src, "ldi", dest.getPairHigh(), hiByte(merged));
                }
            } else {
                unsupportedOperationError();
            }
        } else if (arg.isAnyConst()) {
            if (dest.isRegGroup()) {
                for (int i = dest.size()-1; i >= 0; i--) {
                    out.appendCommand(src, "ldi", dest.getReg(i), numByte(arg, dest.size()-1-i));
                }
            } else {
                out.appendCommand(src, "ldi", dest.getPairLow(), loByte(arg));
                out.appendCommand(src, "ldi", dest.getPairHigh(), hiByte(arg));
            }
        } else if ((arg.isPair() || arg.isRegisterWord()) && (dest.isPair() || dest.isRegisterWord())) {
            out.appendCommand(src, "movw", dest.getPairLow(), arg.getPairLow());
        } else if (arg.isPair() || (arg.isRegGroup() && arg.size() == 2)) {
            // TODO !!!! optimize with movw !!!!
            out.appendCommand(src, "mov", dest.getPairLow(), arg.getPairLow());
            out.appendCommand(src, "mov", dest.getPairHigh(), arg.getPairHigh());
        } else if (dest.isRegGroup() && arg.isRegGroup()) {
            if (dest.size() != arg.size()) {
                sizesMismatchError();
            }
            // TODO !!!! optimize with movw !!!!
            for (int i = dest.size()-1; i >= 0; i--) {
                out.appendCommand(src, "mov", dest.getReg(i), arg.getReg(i));
            }
        } else if (getVarType(arg) == Type.POINTER) {
            String low = loVarPtr(arg, Type.POINTER, false);
            String high = hiVarPtr(arg, Type.POINTER, false);
            out.appendCommand(src, "ldi", dest.getPairLow(), low);
            out.appendCommand(src, "ldi", dest.getPairHigh(), high);
        } else if (getVarType(arg) == Type.PRGPTR) {
            String low = loVarPtr(arg, Type.PRGPTR, false);
            String high = hiVarPtr(arg, Type.PRGPTR, false);
            out.appendCommand(src, "ldi", dest.getPairLow(), low);
            out.appendCommand(src, "ldi", dest.getPairHigh(), high);
        } else if ((dest.isRegGroup() || dest.isPair()) && isVar(arg)) {
            if (getVarSize(arg) != dest.size()) {
                sizesMismatchError();
            }
            int sz = dest.size();
            for (int i = 0; i < sz; i++) {
                String varAddress = i < sz - 1 ? arg + "+" + (sz - i - 1) : arg.asString();
                out.appendCommand(src, "lds", dest.getReg(i), varAddress);
            }
        } else if (arg.isPair() || arg.isRegisterWord()) {
            out.appendCommand(src, "movw", dest.getPairLow(), arg.getPairLow());
        } else if (arg.isRegGroup() && arg.size() == 2) {
            // TODO !!!! optimize with movw !!!!
            out.appendCommand(src, "mov", dest.getPairLow(), arg.getPairLow());
            out.appendCommand(src, "mov", dest.getPairHigh(), arg.getPairHigh());
        } else {
            unsupportedOperationError(expr);
        }
    }


    private static String hexByteStr(int val) {
        String s = Integer.toHexString(val);
        return s.length() == 1 ? "0x0" + s : "0x" + s;
    }


    private String hiByte(Token val) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            return hexByteStr(d >> 8);
        }
        if (parser.gcc) {
            return "(" +val + " >> 8)";
        } else {
            return "HIGH(" + val + ")";
        }
    }

    private String loByte(Token val) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            return hexByteStr(d & 0xff);
        }
        if (parser.gcc) {
            return "(" + val + " & 0xFF)";
        } else {
            return "LOW(" + val + ")";
        }
    }

    private String numByte(Token val, int byteNum) {
        if (val.isNumber()) {
            int d = val.getNumberValue();
            d = d >> (8*byteNum);
            return hexByteStr(d & 0xff);
        }
        if (parser.gcc) {
            return "((" + val + ">>" + (byteNum*8) + ") & 0xFF)";
        } else {
            return ("BYTE" + (byteNum+1)) + "(" + val + ")";
        }
    }

    private String loVarPtr(Token name, Variable.Type type, boolean negative) {
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

    private String hiVarPtr(Token name, Variable.Type type, boolean negative) {
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



    private void incDecReg(TokenString src, Token dest, Token operation, OutputFile out) {
        if (dest.isPair()) {
            String instruction = operation.isOperator("++") ? "adiw" : "sbiw";
            out.appendCommand(src, instruction, dest + "L", "1");
        } else {
            String instruction = operation.isOperator("++") ? "inc" : "dec";
            out.appendCommand(src, instruction, dest);
        }
    }

    private void addRegGroup(TokenString src, Token dest, Token operation, Token arg, OutputFile out) throws CompileException {
        if (operation.isOperator("+=")) {
            for (int i = dest.size()-1; i >= 0; i --) {
                String instruction = i == dest.size()-1 ? "add" : "adc";
                out.appendCommand(src, instruction, dest.getReg(i), arg.getReg(i));
            }
        } else if (operation.isOperator("-=")) {
            for (int i = dest.size()-1; i >= 0; i --) {
                String instruction = i == dest.size()-1 ? "sub" : "sbc";
                out.appendCommand(src, instruction, dest.getReg(i), arg.getReg(i));
            }
        } else {
            unsupportedOperationError(operation);
        }
    }

    private void addReg(TokenString src, Token dest, Token operation, Token argument, OutputFile out) throws CompileException {
        boolean isAdd = operation.isOperator("+=");
        if (argument.isRegister()) {
            String instruction = isAdd ? "add" : "sub";
            out.appendCommand(src, instruction, dest, argument);
        } else if (dest.isPair()) {
            String instruction = isAdd ? "adiw" : "sbiw";
            if (argument.isAnyConst()) {
                out.appendCommand(src, instruction, dest.getPairLow(), argument.wrapToBrackets());
            } else if (getVarType(argument) == Type.POINTER) {
                String argLo = loVarPtr(argument, Type.POINTER, isAdd);
                String argHi = hiVarPtr(argument, Type.POINTER, isAdd);
                out.appendCommand(src, "subi", dest.getPairLow(), argLo);
                out.appendCommand(src, "sbci", dest.getPairHigh(), argHi);
            } else if (getVarType(argument) == Type.PRGPTR) {
                String argLo = loVarPtr(argument, Type.PRGPTR, isAdd);
                String argHi = hiVarPtr(argument, Type.PRGPTR, isAdd);
                out.appendCommand(src, "subi", dest.getPairLow(), argLo);
                out.appendCommand(src, "sbci", dest.getPairHigh(), argHi);
            } else if (argument.isPair()) {
                if (isAdd) {
                    out.appendCommand(src, "add", dest.getPairLow(), argument.getPairLow());
                    out.appendCommand(src, "adc", dest.getPairHigh(), argument.getPairHigh());
                } else {
                    out.appendCommand(src, "sub", dest.getPairLow(), argument.getPairLow());
                    out.appendCommand(src, "sbc", dest.getPairHigh(), argument.getPairHigh());
                }
            } else {
                unexpectedExpressionError(argument);
            }
        } else if (argument.isAnyConst()) {
            if (isAdd) {
                out.appendCommand(src, "subi", dest, argument.getNegativeExpr());
            } else {
                out.appendCommand(src, "subi", dest, argument.wrapToBrackets());
            }
        } else {
            unexpectedExpressionError(argument);
        }
    }

    private void addPair(TokenString src, Token dest, Token operation, Token arg, OutputFile out) throws CompileException {
        boolean add;
        if (operation.isOperator("+=")) {
            add = true;
        } else if (operation.isOperator("-=")) {
            add = false;
        } else {
            unsupportedOperationError(operation);
            return;
        }
        if (arg.isAnyConst()) {
            out.appendCommand(src, add ? "adiw" : "sbiw", dest.getPairLow(), arg.wrapToBrackets());
        } else if (arg.isPair() || arg.isRegGroup()) {
            out.appendCommand(src, add ? "add" : "sub", dest.getPairLow(), arg.getPairLow());
            out.appendCommand(src, add ? "adc" : "sbc", dest.getPairHigh(), arg.getPairHigh());
        } else if (arg.isVar()) {
            Variable.Type varType = getVarType(arg);
            if (varType != Type.POINTER && varType != Type.PRGPTR) {
                unsupportedOperationError();
            }
            String argLo = loVarPtr(arg, varType, add);
            String argHi = hiVarPtr(arg, varType, add);
            out.appendCommand(src, "subi", dest.getPairLow(), argLo);
            out.appendCommand(src, "sbci", dest.getPairHigh(), argHi);
        } else {
            unsupportedOperationError();
        }
    }

    private void andReg(TokenString src, Token dest, Token arg, OutputFile out) throws CompileException {
        if (arg.isRegister()) {
            out.appendCommand(src, "and", dest, arg);
        } else if (arg.isAnyConst()) {
            out.appendCommand(src, "andi", dest, arg);
        } else {
            unsupportedOperationError();
        }
    }

    private void orReg(TokenString src, Token dest, Token arg, OutputFile out) throws CompileException {
        if (arg.isRegister()) {
            out.appendCommand(src, "or", dest, arg);
        } else if (arg.isAnyConst()) {
            out.appendCommand(src, "ori", dest, arg);
        } else {
            unsupportedOperationError();
        }
    }

    private void shlRegs(TokenString src, Token dest, Token arg, OutputFile out) throws CompileException {
        if (!arg.isNumber()) {
            unsupportedOperationError();
        }
        int cnt = arg.getNumberValue();
        for (int i = 0; i < cnt; i++) {
            for (int ri = dest.size() - 1; ri >= 0; ri--) {
                String instruction = ri == dest.size() - 1 ? "lsl" : "rol";
                out.appendCommand(src, instruction, dest.getReg(ri));
            }
        }
    }

    private void shrRegs(TokenString src, Token dest, Token arg, OutputFile out) throws CompileException {
        if (!arg.isNumber()) {
            unsupportedOperationError();
        }
        int cnt = arg.getNumberValue();
        for (int i = 0; i < cnt; i++) {
            for (int ri = dest.size() - 1; ri >= 0; ri--) {
                String instruction = ri == dest.size() - 1 ? "lsr" : "ror";
                out.appendCommand(src, instruction, dest.getReg(ri));
            }
        }
    }

    private Variable getVar(Token name) {
        return parser != null ? parser.getVariable(name.asString()) : null;
    }

    private boolean isVar(Token name) {
        return getVar(name) != null;
    }

    private int getVarSize(Token name) {
        Variable var = getVar(name);
        return var != null ? var.getSize() : -1;
    }

    private Variable.Type getVarType(Token name) {
        Variable var = getVar(name);
        return var != null ? var.type : null;
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

    private static void unsupportedOperationError(Object o) throws CompileException {
        throw new CompileException("unsupported operation: " + o);
    }

    private static void constExpectedError(String val) throws CompileException {
        throw new CompileException("constant expected: " + val);
    }

    private static void unexpectedExpressionError() throws CompileException {
        throw new CompileException("unexpected expression");
    }

    private static void unexpectedExpressionError(Object o) throws CompileException {
        throw new CompileException("unexpected expression: " + o);
    }

    private static void wrongArgumentError(Token t) throws CompileException {
        throw new CompileException("wrong argument: " + t);
    }

    private static void emptyExpressionError() throws CompileException {
        throw new CompileException("empty expression");
    }

    private static void sizesMismatchError() throws CompileException {
        unsupportedOperationError("sizes mismatch");
    }


}
