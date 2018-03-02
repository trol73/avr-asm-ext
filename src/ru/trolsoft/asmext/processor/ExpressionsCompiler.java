package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.data.Variable.Type;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import static ru.trolsoft.asmext.processor.AsmInstr.CLEAR_FLAG_MAP;
import static ru.trolsoft.asmext.processor.AsmInstr.SET_FLAG_MAP;

class ExpressionsCompiler {

    private static final String[] STRING_OF_ZERO = {"", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000", "000000000", "0000000000"};


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
e.printStackTrace();
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
                expr.removeFirst(2);
                if (dest.isRegister()) {
                    moveToReg(src, dest, expr, out);
                } else if (dest.isRegGroup() || dest.isPair()) {
                    moveToGroupOrPair(src, dest, expr, out);
                } else if (dest.isVar()) {
                    moveToVar(src, dest, expr, out);
                } else if (dest.isArray()) {
                    moveToArray(src, dest, expr, out);
                } else if (dest.isFlag()) {
                    moveToFlag(src, dest, expr, out);
                } else if (dest.isRegisterBit()) {
                    moveToRegBit(src, dest, expr, out);
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
                    if (arg.isRegGroup(dest.size())) {
                        addRegGroup(src, dest, operation, arg, out);
                    } else if (arg.isAnyConst()) {
                        addReg(src, dest, operation, arg, out);
                    } else {
                        unexpectedExpressionError();
                    }
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
                } else if (dest.isRegister()) {// (expr.size() == 3) {
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
            case ".":
                if (dest.getType() == Token.TYPE_ARRAY_IO) {
                    setBitInPort(src, dest, expr, out);
                } else {
                    unsupportedOperationError();
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
        Token firstArg = expr.getFirst();
        if (expr.size() == 3 && expr.get(1).isOperator("+")) {
//            if (compileAddWithCarry(src, dest, firstArg, expr.get(2), out)) {
//                expr.removeFirst();
//                expr.removeFirst();
//                return;
//            }
        }

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
                moveNegative(src, dest, expr, out);
            } else {    // x = const | var | expression
                if (firstArg.isNumber() && firstArg.getNumberValue() == 0) {
                    out.appendCommand(src, "clr", dest);
                    return;
                } else {
                    moveValueToReg(src, dest, firstArg, out);
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
            compileOperation(src, dest, arg, operation.asString(), out);
        }
    }

    private boolean compileAddWithCarry(TokenString src, Token dest, Token firstArg, Token secondArg, OutputFile out) {
        if (firstArg.isRegister() && secondArg.isFlag("F_CARRY")) {
            out.appendCommand(src, "adc", dest, firstArg);
            return true;
        } else if (firstArg.isFlag("F_CARRY") && secondArg.isRegister()) {
            out.appendCommand(src, "adc", dest, secondArg);
            return true;
        }
        return false;
    }

    private void compileOperation(TokenString src, Token dest, Token arg, String operation, OutputFile out) throws CompileException {
        switch (operation) {
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
            case ">>":
            case "<<":
                if (arg.isNumber()) {
                    int cnt = arg.getNumberValue();
                    String cmd = "<<".equals(operation) ? "lsl" : "lsr";
                    for (int i = 0; i < cnt; i++) {
                        out.appendCommand(src, cmd, dest);
                    }
                } else {
                    wrongArgumentError(arg);
                }
                break;
            default:
                unsupportedOperationError(operation);
        }
    }


    private void moveNegative(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
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
    }


    private void moveValueToReg(TokenString src, Token destReg, Token arg, OutputFile out) throws CompileException {
        if (arg.isVar()) {
            int size = getVarSize(arg);
            if (size == 1) {
                out.appendCommand(src, "lds", destReg, arg);
            } else {
                unsupportedOperationError();
            }
        } else if (arg.isConst()) {
            out.appendCommand(src, "ldi", destReg, resolveConst(arg));
        } else if (arg.isAnyConst()) {
            out.appendCommand(src, "ldi", destReg, arg);
        } else if (arg.isArray()) {
            moveFromArray(src, destReg, arg, out);
        } else {
            // TODO
            //out.appendCommand(src, "ldi", destReg, arg);
            unexpectedExpressionError(arg);
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

    private void moveFromArray(TokenString src, Token destReg, Token array, OutputFile out) throws CompileException {
        Token.ArrayIndex index;
        switch (array.getType()) {
            case Token.TYPE_ARRAY_IO:
                out.appendCommand(src, "in", destReg, arrayIndexToPort(array));
                break;
            case Token.TYPE_ARRAY_PRG:
                index = array.getIndex();
                if (!"Z".equals(index.getName())) {
                    wrongArrayIndex("prg");
                }
                if (!index.hasModifier()) {
                    if (destReg.isRegister("r0")) {
                        out.appendCommand(src, "lpm");
                    } else {
                        out.appendCommand(src, "lpm", destReg, index.getName());
                    }
                } else if (index.isPostInc()) {
                    out.appendCommand(src, "lpm", destReg, index.getName() + "+");
                } else {
                    wrongArrayIndex("prg");
                }
                break;
            case Token.TYPE_ARRAY_RAM:
                index = array.getIndex();
                if (!index.isPair() || index.isPreInc() || index.isPostDec()) {
                    unsupportedOperationError();
                }
                out.appendCommand(src, "ld", destReg, arrayPairIndexValue(index));
                break;
            default:
                unsupportedOperationError();
        }
    }

    private void moveToArray(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() != 1) {
            unsupportedOperationError();
        }
        Token arg = expr.getFirst();
        if (!arg.isRegister()) {
            unsupportedOperationError();
        }
        switch (dest.getType()) {
            case Token.TYPE_ARRAY_IO:
                out.appendCommand(src, "out", arrayIndexToPort(dest), arg.asString());
                break;
            case Token.TYPE_ARRAY_PRG:
                unsupportedOperationError("can't write to prg[]");
                break;
            case Token.TYPE_ARRAY_RAM:
                Token.ArrayIndex index = dest.getIndex();
                 if (!index.isPair() || index.isPreInc() || index.isPostDec()) {
                     wrongArrayIndex("ram");
                 }
                out.appendCommand(src, "st", arrayPairIndexValue(index), arg.asString());
                break;
            default:
                unsupportedOperationError();
        }
    }

    private void moveToFlag(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() == 1) {
            Token arg = expr.getFirst();
            if (!arg.isNumber()) {
                unsupportedOperationError("1 or 0 expected for flag");
            }
            int val = arg.getNumberValue();
            if (val == 0) {
                out.appendCommand(src, CLEAR_FLAG_MAP.get(dest.asString()));
            } else if (val == 1) {
                out.appendCommand(src, SET_FLAG_MAP.get(dest.asString()));
            } else {
                unsupportedOperationError("1 or 0 expected for flag");
            }
        } else if (expr.size() == 3 && expr.getFirst().isArrayIo() && expr.get(1).isOperator(".") && expr.get(2).isAnyConst()) {
            Token bitNumber = expr.get(2);
            checkBitNumber(bitNumber);
            out.appendCommand(src, CLEAR_FLAG_MAP.get(dest.asString()));
            out.appendCommand(src, "sbic", arrayIndexToPort(expr.getFirst()), bitNumber.asString());
            out.appendCommand(src, SET_FLAG_MAP.get(dest.asString()));
        } else if (expr.size() == 4 && expr.getFirst().isOperator("!") && expr.get(1).isArrayIo() && expr.get(2).isOperator(".") && expr.get(3).isAnyConst()) {
            Token bitNumber = expr.get(3);
            checkBitNumber(bitNumber);
            out.appendCommand(src, CLEAR_FLAG_MAP.get(dest.asString()));
            out.appendCommand(src, "sbis", arrayIndexToPort(expr.get(1)), bitNumber.asString());
            out.appendCommand(src, SET_FLAG_MAP.get(dest.asString()));
        } else {
            unsupportedOperationError();
        }
    }


    private void setBitInPort(TokenString src, Token array, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() == 5 && expr.get(3).isOperator("=") && expr.get(4).isNumber()) {
            // io[PORTC].5 = 1
            Token val = expr.get(4);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            if (val.getNumberValue() == 1) {
                out.appendCommand(src, "sbi", arrayIndexToPort(array), bitToken.asString());
            } else if (val.getNumberValue() == 0) {
                out.appendCommand(src, "cbi", arrayIndexToPort(array), bitToken.asString());
            } else {
                unsupportedOperationError("1 or 0 expected");
            }
        } else if (expr.size() == 5 && expr.get(3).isOperator("=") && expr.get(4).isRegisterBit()) {
            // io[PORTC].5 = r0[1]
            Token bitReg = expr.get(4);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            checkBitNumber(bitReg);

            out.appendCommand(src, "sbrs", bitReg.asString(), bitReg.getBitIndex().asString());
            out.appendCommand(src, "cbi", arrayIndexToPort(array), bitToken.asString());
            out.appendCommand(src, "sbrc", bitReg.asString(), bitReg.getBitIndex().asString());
            out.appendCommand(src, "sbi", arrayIndexToPort(array), bitToken.asString());
        } else if (expr.size() == 6 && expr.get(3).isOperator("=") && expr.get(4).isOperator("!") && expr.get(5).isRegisterBit()) {
            // io[PORTC].5 = !r10[1]
            Token bitReg = expr.get(5);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            checkBitNumber(bitReg);
            out.appendCommand(src, "sbrs", bitReg.asString(), bitReg.getBitIndex().asString());
            out.appendCommand(src, "sbi", arrayIndexToPort(array), bitToken.asString());
            out.appendCommand(src, "sbrc", bitReg.asString(), bitReg.getBitIndex().asString());
            out.appendCommand(src, "cbi", arrayIndexToPort(array), bitToken.asString());
        } else {
            unsupportedOperationError();
        }
    }

    private void moveToRegBit(TokenString src, Token dest, Expression expr, OutputFile out) throws CompileException {
        if (expr.size() == 1 && expr.getFirst().isNumber()) {
            Token val = expr.getFirst();
            checkBitNumber(dest);
            Token bitIndex = dest.getBitIndex();
            String bitMask = dest.getBitIndex().isNumber() ? binByteStr(1 << bitIndex.getNumberValue()) : "1<<"+bitIndex.asString();
            if (val.getNumberValue() == 1) {
                out.appendCommand(src, "sbr", dest, bitMask);
            } else if (val.getNumberValue() == 0) {
                out.appendCommand(src, "cbr", dest, bitMask);
            } else {
                unsupportedOperationError("1 or 0 expected");
            }
        } else {
            unsupportedOperationError();
        }
    }

    private void checkBitNumber(Token token) throws CompileException {
        if (token.isNumber()) {
            int bit = token.getNumberValue();
            if (bit < 0 || bit > 7) {
                unsupportedOperationError("wrong bit number");
            }
        } else if (token.isRegisterBit()) {
            Token index = token.getBitIndex();
            int bit;
            if (index.isAnyConst() && parser.getConstant(index.asString()) != null) {
                String val = parser.getConstant(index.asString()).value;
                if (!ParserUtils.isNumber(val)) {
                    unsupportedOperationError("unexpected value for bit index: " + index);
                }
                bit = ParserUtils.parseValue(val);
            } else if (index.isNumber()) {
                bit = index.getNumberValue();
            } else {
                unsupportedOperationError("unexpected value for bit index: " + index);
                return;
            }
            if (bit < 0 || bit > 7) {
                unsupportedOperationError("wrong bit number");
            }
        } else if (!(token.isAnyConst() || token.isSomeString())) {
            unsupportedOperationError("bit number expected after dot");
        }
    }


    private static String hexByteStr(int val) {
        String s = Integer.toHexString(val);
        return s.length() == 1 ? "0x0" + s : "0x" + s;
    }

    private static String binByteStr(int val) {
        String s = Integer.toBinaryString(val);
        s = STRING_OF_ZERO[8 - s.length()] + s;
        return "0b" + s;
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
                return negative ? "LOW(-" + name + ")" : "LOW(" + name + ")";
            } else if (type == Type.PRGPTR) {
                return negative ? "LOW(-2*" + name + ")" : "LOW(2*" + name + ")";
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
                return negative ? "HIGH(-" + name + ")" : "HIGH(" + name + ")";
            } else if (type == Type.PRGPTR) {
                return negative ? "HIGH(-2*" + name + ")" : "HIGH(2*" + name + ")";
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
        if (dest.isRegister() && argument.isRegister()) {
            String instruction = isAdd ? "add" : "sub";
            out.appendCommand(src, instruction, dest, argument);
        } else if (dest.isPair()) {
            boolean shortArg = argument.isNumber() && Math.abs(argument.getNumberValue()) <= 63;
            if (shortArg && argument.isAnyConst()) {
                String instruction = isAdd ? "adiw" : "sbiw";
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
            if (dest.isRegister()) {
                if (isAdd) {
                    out.appendCommand(src, "subi", dest, argument.getNegativeExpr());
                } else {
                    out.appendCommand(src, "subi", dest, argument.wrapToBrackets());
                }
            } else if (dest.isRegGroup()) {
                // TODO optimize it for pairs with ADIW/SBIW
                String op = isAdd ? "-" : "";
                out.appendCommand(src, "subi", dest.getReg(dest.size()-1), op+numByte(argument, 0));

                for (int i = dest.size()-2; i >= 0; i--) {
                    int byteOffset = dest.size() - i - 1;
                    out.appendCommand(src, "sbci", dest.getReg(i), op+numByte(argument, byteOffset));
                }
            } else if (dest.isPair()) {
                addPair(src, dest, operation, argument, out);
            } else {
                unexpectedExpressionError(argument);
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
            // TODO calculate numbers
            boolean shortArg = !arg.isNumber() || Math.abs(arg.getNumberValue()) <= 63;
            if (shortArg) {
                out.appendCommand(src, add ? "adiw" : "sbiw", dest.getPairLow(), arg.wrapToBrackets());
            } else {
                out.appendCommand(src, "subi", dest.getPairLow(), "-"+loByte(arg));
                out.appendCommand(src, "sbci", dest.getPairHigh(), "-"+hiByte(arg));
            }
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

    private static String arrayPairIndexValue(Token.ArrayIndex index) {
        String name = index.getName();
        if (index.isPreDec()) {
            return "-" + name;
        } else if (index.isPreInc()) {
            return "+" + name;
        } else if (index.isPostDec()) {
            return name + "-";
        } else if (index.isPostInc()) {
            return name + "+";
        } else {
            return name;
        }
    }

    private String arrayIndexToPort(Token tokenIndex) throws CompileException {
        Token.ArrayIndex index = tokenIndex.getIndex();
        if (index.hasModifier() || index.isPair()) {
            wrongArrayIndex("io");
        }
        return parser.gcc ? "_SFR_IO_ADDR(" + index.getName() + ")" : index.getName();
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

    private static void wrongArrayIndex(String arrayName) throws CompileException {
        unsupportedOperationError("wrong " + arrayName + "[] index");
    }


}
