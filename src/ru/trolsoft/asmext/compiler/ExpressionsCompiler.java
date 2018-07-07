package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.data.Variable.Type;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.processor.*;
import ru.trolsoft.asmext.utils.TokenString;

import static ru.trolsoft.asmext.compiler.AsmInstr.CLEAR_FLAG_MAP;
import static ru.trolsoft.asmext.compiler.AsmInstr.SET_FLAG_MAP;
import static ru.trolsoft.asmext.compiler.Cmd.*;

public class ExpressionsCompiler extends BaseCompiler {

    public ExpressionsCompiler(Parser parser) {
        super(parser);
    }

    public boolean compile(TokenString src, Expression expr, OutputFile out) throws SyntaxException {
        if (expr.size() < 2) {
            return false;
        }
        setup(src, out);
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
                return compileMultiMove(expr, moveCount);
            }
            return compileExpr(expr);
        } catch (RuntimeException e) {
            internalError(e.getMessage(), e);
            return false;
        }
    }


    private boolean compileMultiMove(Expression expr, int moveCount) throws SyntaxException {
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
            if (!compileExpr(lastExpression)) {
                return false;
            }
            expr.removeLast(len+1);
        } // for exprNumber
        return compileExpr(expr);
    }

    private boolean compileExpr(Expression expr) throws SyntaxException {
        Token dest = expr.get(0);
        Token operation = expr.get(1);
        if (!operation.isOperator()) {
            return false;
        }
        switch (operation.asString()) {
            case "=":
                expr.removeFirst(2);
                if (dest.isRegister()) {
                    moveToReg(dest, expr);
                } else if (dest.isRegGroup() || dest.isPair()) {
                    moveToGroupOrPair(dest, expr);
                } else if (dest.isVar()) {
                    moveToVar(dest, expr);
                } else if (dest.isArray()) {
                    moveToArray(dest, expr);
                } else if (dest.isFlag()) {
                    moveToFlag(dest, expr);
                } else if (dest.isRegisterBit()) {
                    moveToRegBit(dest, expr);
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "++":
            case "--":
                if (expr.size() != 2) {
                    unexpectedExpressionError();
                }
                incDecReg(dest, operation);
                return true;
            case "+=":
            case "-=":
                if (dest.isPair() || dest.isRegisterWord()) {
                    if (expr.size() < 3) {
                        unsupportedOperationError();
                    }
                    expr.removeFirst(2);
                    addPair(dest, operation, expr.getFirst());
                } else if (dest.isRegGroup() && expr.size() == 3) {
                    Token arg = expr.get(2);
                    if (arg.isRegGroup(dest.size())) {
                        addRegGroup(dest, operation, arg);
                    } else if (arg.isAnyConst()) {
                        addReg(dest, operation, arg);
                    } else {
                        unexpectedExpressionError();
                    }
                } else if (dest.isRegister() && expr.size() == 3) {
                    addReg(dest, operation, expr.get(2));
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "&=":
                if (expr.size() != 3) {
                    unsupportedOperationError();
                } else if (expr.size() == 3) {
                    andReg(dest, expr.get(2));
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "|=":
                if (expr.size() != 3) {
                    unsupportedOperationError();
                } else if (dest.isRegister()) {// (expr.size() == 3) {
                    orReg(dest, expr.get(2));
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case "<<=":
                if (expr.size() == 3) {
                    shlRegs(dest, expr.get(2));
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case ">>=":
                if (expr.size() == 3) {
                    shrRegs(dest, expr.get(2));
                } else {
                    unexpectedExpressionError();
                }
                return true;
            case ".":
                if (dest.getType() == Token.TYPE_ARRAY_IO) {
                    setBitInPort(dest, expr);
                } else {
                    unsupportedOperationError();
                }
                return true;
            default:
                unsupportedOperationError(operation);
        }
        return false;
    }


    private void tryToMergeConstants(Expression expr) throws SyntaxException {
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


    private void moveToReg(Token dest, Expression expr) throws SyntaxException {
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
                addCommand(MOV, dest, firstArg);
            }
        } else {
            // x = -3
            // x = -x
            // x = abc
            // x = var
            if (firstArg.isOperator("-") && expr.size() > 1) {    // x = -y
                moveNegative(dest, expr);
            } else {    // x = const | var | expression
                if (firstArg.isNumber() && firstArg.getNumberValue() == 0) {
                    addCommand(CLR, dest);
//                    return;
                } else {
                    moveValueToReg(dest, firstArg);
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
            compileOperation(dest, arg, operation.asString());
        }
    }

//    private boolean compileAddWithCarry(Token dest, Token firstArg, Token secondArg) throws SyntaxException {
//        if (firstArg.isRegister() && secondArg.isFlag("F_CARRY")) {
//            addCommand(ADC, dest, firstArg);
//            return true;
//        } else if (firstArg.isFlag("F_CARRY") && secondArg.isRegister()) {
//            addCommand(ADC, dest, secondArg);
//            return true;
//        }
//        return false;
//    }

    private void compileOperation(Token dest, Token arg, String operation) throws SyntaxException {
        switch (operation) {
            case "+":
                if (arg.isAnyConst()) {
                    if (arg.isNumber() && arg.getNumberValue() == 1) {
                        addCommand(INC, dest);
                    } else {
                        addCommand(SUBI, dest, arg.getNegativeExpr());
                    }
                } else if (arg.isRegister()) {
                    addCommand(ADD, dest, arg);
                } else {
                    wrongArgumentError(arg);
                }
                break;
            case "-":
                if (arg.isAnyConst()) {
                    if (arg.isNumber() && arg.getNumberValue() == 1) {
                        addCommand(DEC, dest);
                    } else {
                        addCommand(SUBI, dest, arg.wrapToBrackets());
                    }
                } else if (arg.isRegister()) {
                    addCommand(SUB, dest, arg);
                } else {
                    wrongArgumentError(arg);
                }
                break;
            case "&":
                if (arg.isAnyConst()) {
                    addCommand(ANDI, dest, arg.wrapToBrackets());
                } else if (arg.isRegister()) {
                    addCommand(AND, dest, arg);
                } else {
                    wrongArgumentError(arg);
                }
                break;
            case "|":
                if (arg.isAnyConst()) {
                    addCommand(ORI, dest, arg.wrapToBrackets());
                } else if (arg.isRegister()) {
                    addCommand(OR, dest, arg);
                } else {
                    wrongArgumentError(arg);
                }
                break;
            case ">>":
            case "<<":
                if (arg.isNumber()) {
                    int cnt = arg.getNumberValue();
                    Cmd cmd = "<<".equals(operation) ? LSL : LSR;
                    for (int i = 0; i < cnt; i++) {
                        addCommand(cmd, dest);
                    }
                } else {
                    wrongArgumentError(arg);
                }
                break;
            default:
                unsupportedOperationError(operation);
        }
    }


    private void moveNegative(Token dest, Expression expr) throws SyntaxException {
        Token secondArg = expr.get(1);
        // x = -y
        if (secondArg.isRegister()) {
            if (dest.equals(secondArg)) {
                addCommand(NEG, dest);
            } else {
                addCommand(MOV, dest, secondArg);
                addCommand(NEG, dest);
            }
        } else {
            // x = -123
            if (!secondArg.isAnyConst()) {
                unexpectedExpressionError(secondArg);
            }
            Token s = secondArg.isConst() ? resolveConst(secondArg) : secondArg;
            addCommand(LDI, dest, s.getNegativeExpr());
        }
        expr.removeFirst();
    }


    private void moveValueToReg(Token destReg, Token arg) throws SyntaxException {
        if (arg.isVar()) {
            int size = getVarSize(arg);
            if (size == 1) {
                addCommand(LDS, destReg, arg);
            } else {
                unsupportedOperationError();
            }
        } else if (arg.isConst()) {
            addCommand(LDI, destReg, resolveConst(arg));
        } else if (arg.isAnyConst()) {
            addCommand(LDI, destReg, arg);
        } else if (arg.isArray()) {
            moveFromArray(destReg, arg);
        } else {
            // TODO
            //addCommand(LDI, destReg, arg);
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


    private void moveToVar(Token dest, Expression expr) throws SyntaxException {
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
            addCommand(STS, dest, arg);
        } else {
            for (int i = 0; i < varSize; i++) {
                String varAddress = i < varSize-1 ? dest + "+" + (varSize-i-1) : dest.asString();
                addCommand(STS, varAddress, arg.getReg(i).asString());
            }
        }
    }

    private void moveToGroupOrPair(Token dest, Expression expr) throws SyntaxException {
        Token arg = expr.get(0);
        if (expr.size() > 1) {
            if (expr.size() == 3 && expr.get(1).isOperator("+", "-")) {
                Token operator = expr.get(1);
                Token var = expr.getFirst();
                Token secondArg = expr.get(2);
                if (var.isVar() && (secondArg.isPair() || secondArg.isRegGroup(2))) {
                    Type varType = getVarType(var);
                    if (varType == Type.POINTER || varType == Type.PRGPTR) {
                        loadPointerVarToPair(var, varType, dest);
                    } else if (varType == Type.WORD) {
                        addCommand(LDS, dest.getPairLow(), var);
                        addCommand(LDS, dest.getPairHigh(), var.asString()+"+1");
                    } else {
                        unsupportedOperationError();
                    }
                    addSubPairToPair(operator.isOperator("+"), dest, secondArg);
                    return;
                } else if (!secondArg.isNumber() && !secondArg.isSomeString()) {
                    unsupportedOperationError();
                }


            }
            if (!parser.gcc) {
                boolean canBeExpression = expr.operatorsCount("=", "!=", "+=", "-=", "&=", "|=", ">=", "<=", "<<=", ">>=") == 0;
                if (!canBeExpression) {
                    unsupportedOperationError();
                }
                Type varType = null;
                for (Token t : expr) {
                    if (t.isVar()) {
                        Type vt = getVarType(t);
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
                    loadPointerVarToPair(merged, varType, dest);
                } else {
                    addCommand(LDI, dest.getPairLow(), loByte(merged));
                    addCommand(LDI, dest.getPairHigh(), hiByte(merged));
                }
            } else {
                unsupportedOperationError();
            }
        } else if (arg.isAnyConst()) {
            if (dest.isRegGroup()) {
                for (int i = dest.size()-1; i >= 0; i--) {
                    addCommand(LDI, dest.getReg(i), numByte(arg, dest.size()-1-i));
                }
            } else {
                addCommand(LDI, dest.getPairLow(), loByte(arg));
                addCommand(LDI, dest.getPairHigh(), hiByte(arg));
            }
        } else if ((arg.isPair() || arg.isRegisterWord()) && (dest.isPair() || dest.isRegisterWord())) {
            addCommand(MOVW, dest.getPairLow(), arg.getPairLow());
        } else if (arg.isPair() || arg.isRegGroup(2)) {
            // TODO !!!! optimize with movw !!!!
            if (!dest.getPairLow().equals(arg.getPairLow())) {
                addCommand(MOV, dest.getPairLow(), arg.getPairLow());
            }
            if (!dest.getPairHigh().equals(arg.getPairHigh())) {
                addCommand(MOV, dest.getPairHigh(), arg.getPairHigh());
            }
        } else if (dest.isRegGroup() && arg.isRegGroup()) {
            if (dest.size() != arg.size()) {
                sizesMismatchError();
            }
            // TODO !!!! optimize with movw !!!!
            for (int i = dest.size()-1; i >= 0; i--) {
                if (!dest.getReg(i).equals(arg.getReg(i))) {
                    addCommand(MOV, dest.getReg(i), arg.getReg(i));
                }
            }
        } else if (getVarType(arg) == Type.POINTER || getVarType(arg) == Type.PRGPTR) {
            loadPointerVarToPair(arg, getVarType(arg), dest);
        } else if ((dest.isRegGroup() || dest.isPair()) && isVar(arg)) {
            if (getVarSize(arg) != dest.size()) {
                sizesMismatchError();
            }
            int sz = dest.size();
            for (int i = 0; i < sz; i++) {
                String varAddress = i < sz - 1 ? arg + "+" + (sz - i - 1) : arg.asString();
                addCommand(LDS, dest.getReg(i), varAddress);
            }
        } else if (arg.isPair() || arg.isRegisterWord()) {
            addCommand(MOVW, dest.getPairLow(), arg.getPairLow());
        } else if (arg.isRegGroup(2)) {
            // TODO !!!! optimize with movw !!!!
            addCommand(MOV, dest.getPairLow(), arg.getPairLow());
            addCommand(MOV, dest.getPairHigh(), arg.getPairHigh());
        } else if (arg.isArrayIow()) {
            addCommand(IN, dest.getPairHigh(), arrayIndexToPort(arg, "H"));
            addCommand(IN, dest.getPairLow(), arrayIndexToPort(arg, "L"));
        } else {
            unsupportedOperationError(expr);
        }
    }

    private void loadPointerVarToPair(Token var, Type varType, Token dest) throws SyntaxException {
        if (varType != Type.PRGPTR && varType != Type.POINTER) {
            unsupportedOperationError();
        }
        String low = loVarPtr(var, varType, false);
        String high = hiVarPtr(var, varType, false);
        addCommand(LDI, dest.getPairLow(), low);
        addCommand(LDI, dest.getPairHigh(), high);
    }

    private void moveFromArray(Token destReg, Token array) throws SyntaxException {
        Token.ArrayIndex index;
        switch (array.getType()) {
            case Token.TYPE_ARRAY_IO:
                addCommand(IN, destReg, arrayIndexToPort(array));
                break;
            case Token.TYPE_ARRAY_PRG:
                index = array.getIndex();
                if (!"Z".equals(index.getName())) {
                    wrongArrayIndex("prg");
                }
                if (!index.hasModifier()) {
                    if (destReg.isRegister("r0")) {
                        addCommand(LPM);
                    } else {
                        addCommand(LPM, destReg, index.getName());
                    }
                } else if (index.isPostInc()) {
                    addCommand(LPM, destReg, index.getName() + "+");
                } else {
                    wrongArrayIndex("prg");
                }
                break;
            case Token.TYPE_ARRAY_RAM:
                index = array.getIndex();
                if (!index.isPair() || index.isPreInc() || index.isPostDec()) {
                    unsupportedOperationError();
                }
                addCommand(LD, destReg, arrayPairIndexValue(index));
                break;
            default:
                unsupportedOperationError();
        }
    }

    private void moveToArray(Token dest, Expression expr) throws SyntaxException {
        if (expr.size() != 1) {
            unsupportedOperationError();
        }
        Token arg = expr.getFirst();
        if (dest.isArrayIow() && (arg.isPair() || arg.isRegGroup(2))) {
            addCommand(OUT, arrayIndexToPort(dest, "H"), arg.getPairHigh());
            addCommand(OUT, arrayIndexToPort(dest, "L"), arg.getPairLow());
            return;
        }
        if (!arg.isRegister()) {
            unsupportedOperationError();
        }
        switch (dest.getType()) {
            case Token.TYPE_ARRAY_IO:
                addCommand(OUT, arrayIndexToPort(dest), arg.asString());
                break;
            case Token.TYPE_ARRAY_PRG:
                unsupportedOperationError("can't write to prg[]");
                break;
            case Token.TYPE_ARRAY_RAM:
                Token.ArrayIndex index = dest.getIndex();
                 if (!index.isPair() || index.isPreInc() || index.isPostDec()) {
                     wrongArrayIndex("ram");
                 }
                addCommand(ST, arrayPairIndexValue(index), arg.asString());
                break;
            default:
                unsupportedOperationError();
        }
    }

    private void moveToFlag(Token dest, Expression expr) throws SyntaxException {
        if (expr.size() == 1) {
            Token arg = expr.getFirst();
            if (dest.isFlag("F_BIT_COPY") && arg.isRegisterBit()) {
                addCommand(BST, arg.asString(), arg.getBitIndex().asString());
            } else if (arg.isNumber()) {
                int val = arg.getNumberValue();
                if (val == 0) {
                    addCommand(CLEAR_FLAG_MAP.get(dest.asString()));
                } else if (val == 1) {
                    addCommand(SET_FLAG_MAP.get(dest.asString()));
                } else {
                    unsupportedOperationError("1 or 0 expected for flag");
                }
            } else {
                unsupportedOperationError("1 or 0 expected for flag");
            }
        } else if (expr.size() == 3 && expr.getFirst().isArrayIo() && expr.get(1).isOperator(".") && expr.get(2).isAnyConst()) {
            Token bitNumber = expr.get(2);
            checkBitNumber(bitNumber);
            addCommand(CLEAR_FLAG_MAP.get(dest.asString()));
            addCommand(SBIC, arrayIndexToPort(expr.getFirst()), bitNumber.asString());
            addCommand(SET_FLAG_MAP.get(dest.asString()));
        } else if (expr.size() == 4 && expr.getFirst().isOperator("!") && expr.get(1).isArrayIo() && expr.get(2).isOperator(".") && expr.get(3).isAnyConst()) {
            Token bitNumber = expr.get(3);
            checkBitNumber(bitNumber);
            addCommand(CLEAR_FLAG_MAP.get(dest.asString()));
            addCommand(SBIS, arrayIndexToPort(expr.get(1)), bitNumber.asString());
            addCommand(SET_FLAG_MAP.get(dest.asString()));
        } else {
            unsupportedOperationError();
        }
    }


    private void setBitInPort(Token array, Expression expr) throws SyntaxException {
        if (expr.size() == 5 && expr.get(3).isOperator("=") && expr.get(4).isNumber()) {
            // io[PORTC].5 = 1
            Token val = expr.get(4);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            if (val.getNumberValue() == 1) {
                addCommand(SBI, arrayIndexToPort(array), bitToken.asString());
            } else if (val.getNumberValue() == 0) {
                addCommand(CBI, arrayIndexToPort(array), bitToken.asString());
            } else {
                unsupportedOperationError("1 or 0 expected");
            }
        } else if (expr.size() == 5 && expr.get(3).isOperator("=") && expr.get(4).isRegisterBit()) {
            // io[PORTC].5 = r0[1]
            Token bitReg = expr.get(4);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            checkBitNumber(bitReg);

            addCommand(SBRS, bitReg.asString(), bitReg.getBitIndex().asString());
            addCommand(CBI, arrayIndexToPort(array), bitToken.asString());
            addCommand(SBRC, bitReg.asString(), bitReg.getBitIndex().asString());
            addCommand(SBI, arrayIndexToPort(array), bitToken.asString());
        } else if (expr.size() == 6 && expr.get(3).isOperator("=") && expr.get(4).isOperator("!") && expr.get(5).isRegisterBit()) {
            // io[PORTC].5 = !r10[1]
            Token bitReg = expr.get(5);
            Token bitToken = expr.get(2);
            checkBitNumber(bitToken);
            checkBitNumber(bitReg);
            addCommand(SBRS, bitReg.asString(), bitReg.getBitIndex().asString());
            addCommand(SBI, arrayIndexToPort(array), bitToken.asString());
            addCommand(SBRC, bitReg.asString(), bitReg.getBitIndex().asString());
            addCommand(CBI, arrayIndexToPort(array), bitToken.asString());
        } else {
            unsupportedOperationError();
        }
    }

    private void moveToRegBit(Token dest, Expression expr) throws SyntaxException {
        if (expr.size() != 1) {
            unsupportedOperationError();
        }
        Token arg = expr.getFirst();
        if (arg.isNumber()) {
            Token val = expr.getFirst();
            checkBitNumber(dest);
            Token bitIndex = dest.getBitIndex();
            String bitMask = dest.getBitIndex().isNumber() ? binByteStr(1 << bitIndex.getNumberValue()) : "1<<" + bitIndex.asString();
            if (val.getNumberValue() == 1) {
                addCommand(SBR, dest.asString(), bitMask);
            } else if (val.getNumberValue() == 0) {
                addCommand(CBR, dest.asString(), bitMask);
            } else {
                unsupportedOperationError("1 or 0 expected");
            }
        } else if (arg.isFlag("F_BIT_COPY")) {
            addCommand(BLD, dest.asString(), dest.getBitIndex().asString());
        } else {
            unsupportedOperationError();
        }
    }


    private void incDecReg(Token dest, Token operation) throws SyntaxException {
        if (dest.isPair() || dest.isRegisterWord()) {
            Cmd cmd = operation.isOperator("++") ? ADIW : SBIW;
            addCommand(cmd, dest.getPairLow(), "1");
        } else if (dest.isRegister()) {
            Cmd cmd = operation.isOperator("++") ? INC : DEC;
            addCommand(cmd, dest);
        } else {
            unsupportedOperationError();
        }
    }

    private void addRegGroup(Token dest, Token operation, Token arg) throws SyntaxException {
        if (operation.isOperator("+=")) {
            for (int i = dest.size()-1; i >= 0; i --) {
                Cmd cmd = i == dest.size()-1 ? ADD : ADC;
                addCommand(cmd, dest.getReg(i), arg.getReg(i));
            }
        } else if (operation.isOperator("-=")) {
            for (int i = dest.size()-1; i >= 0; i --) {
                Cmd cmd = i == dest.size()-1 ? SUB : SBC;
                addCommand(cmd, dest.getReg(i), arg.getReg(i));
            }
        } else {
            unsupportedOperationError(operation);
        }
    }

    private void addReg(Token dest, Token operation, Token argument) throws SyntaxException {
        boolean isAdd = operation.isOperator("+=");
        if (dest.isRegister() && argument.isRegister()) {
            Cmd cmd = isAdd ? ADD : SUB;
            addCommand(cmd, dest, argument);
        } else if (dest.isPair()) {
            boolean shortArg = argument.isNumber() && Math.abs(argument.getNumberValue()) <= 63;
            if (shortArg && argument.isAnyConst()) {
                Cmd cmd = isAdd ? ADIW : SBIW;
                addCommand(cmd, dest.getPairLow(), argument.wrapToBrackets());
            } else if (getVarType(argument) == Type.POINTER || getVarType(argument) == Type.PRGPTR) {
                String argLo = loVarPtr(argument, getVarType(argument), isAdd);
                String argHi = hiVarPtr(argument, getVarType(argument), isAdd);
                addCommand(SUBI, dest.getPairLow(), argLo);
                addCommand(SBCI, dest.getPairHigh(), argHi);
            } else if (argument.isPair() || argument.isRegGroup(2)) {
                addSubPairToPair(isAdd, dest, argument);
//                if (isAdd) {
//                    out.appendCommand(src, "add", dest.getPairLow(), argument.getPairLow());
//                    out.appendCommand(src, "adc", dest.getPairHigh(), argument.getPairHigh());
//                } else {
//                    out.appendCommand(src, "sub", dest.getPairLow(), argument.getPairLow());
//                    out.appendCommand(src, "sbc", dest.getPairHigh(), argument.getPairHigh());
//                }
            } else {
                unexpectedExpressionError(argument);
            }
        } else if (argument.isAnyConst()) {
            if (dest.isRegister()) {
                Token arg = isAdd ? argument.getNegativeExpr() : argument.wrapToBrackets();
                addCommand(SUBI, dest, arg);
            } else if (dest.isRegGroup()) {
                // TODO optimize it for pairs with ADIW/SBIW
                String op = isAdd ? "-" : "";
                addCommand(SUBI, dest.getReg(dest.size()-1), op+numByte(argument, 0));

                for (int i = dest.size()-2; i >= 0; i--) {
                    int byteOffset = dest.size() - i - 1;
                    addCommand(SBCI, dest.getReg(i), op+numByte(argument, byteOffset));
                }
            } else if (dest.isPair()) {
                addPair(dest, operation, argument);
            } else {
                unexpectedExpressionError(argument);
            }
        } else {
            unexpectedExpressionError(argument);
        }
    }

    private void addPair(Token dest, Token operation, Token arg) throws SyntaxException {
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
                addCommand(add ? ADIW : SBIW, dest.getPairLow(), arg.wrapToBrackets());
            } else {
                addCommand(SUBI, dest.getPairLow(), "-"+loByte(arg));
                addCommand(SBCI, dest.getPairHigh(), "-"+hiByte(arg));
            }
        } else if (arg.isPair() || arg.isRegGroup(2)) {
            addSubPairToPair(add, dest, arg);
        } else if (arg.isVar()) {
            Type varType = getVarType(arg);
            if (varType != Type.POINTER && varType != Type.PRGPTR) {
                unsupportedOperationError();
            }
            String argLo = loVarPtr(arg, varType, add);
            String argHi = hiVarPtr(arg, varType, add);
            addCommand(SUBI, dest.getPairLow(), argLo);
            addCommand(SBCI, dest.getPairHigh(), argHi);
        } else {
            unsupportedOperationError();
        }
    }

    private void addSubPairToPair(boolean add, Token dest, Token arg) throws SyntaxException {
        addCommand(add ? ADD : SUB, dest.getPairLow(), arg.getPairLow());
        addCommand(add ? ADC : SBC, dest.getPairHigh(), arg.getPairHigh());
    }


    private void andReg(Token dest, Token arg) throws SyntaxException {
        if (arg.isRegister()) {
            addCommand(AND, dest, arg);
        } else if (arg.isAnyConst()) {
            addCommand(ANDI, dest, arg);
        } else {
            unsupportedOperationError();
        }
    }

    private void orReg(Token dest, Token arg) throws SyntaxException {
        if (arg.isRegister()) {
            addCommand(OR, dest, arg);
        } else if (arg.isAnyConst()) {
            addCommand(ORI, dest, arg);
        } else {
            unsupportedOperationError();
        }
    }

    private void shlRegs(Token dest, Token arg) throws SyntaxException {
        if (!arg.isNumber()) {
            unsupportedOperationError();
        }
        int cnt = arg.getNumberValue();
        for (int i = 0; i < cnt; i++) {
            for (int ri = dest.size() - 1; ri >= 0; ri--) {
                Cmd cmd = ri == dest.size() - 1 ? LSL : ROL;
                addCommand(cmd, dest.getReg(ri));
            }
        }
    }

    private void shrRegs(Token dest, Token arg) throws SyntaxException {
        if (!arg.isNumber()) {
            unsupportedOperationError();
        }
        int cnt = arg.getNumberValue();
        for (int i = 0; i < cnt; i++) {
            for (int ri = dest.size() - 1; ri >= 0; ri--) {
                Cmd cmd = ri == dest.size() - 1 ? LSR : ROR;
                addCommand(cmd, dest.getReg(ri));
            }
        }
    }


}
