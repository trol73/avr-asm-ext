package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.Argument;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.processor.AsmInstr.BRANCH_IF_FLAG_CLEAR_MAP;
import static ru.trolsoft.asmext.processor.AsmInstr.BRANCH_IF_FLAG_SET_MAP;

class Compiler {

    private final Parser parser;
    private final ExpressionsCompiler expressionsCompiler;


    Compiler(Parser parser) {
        this.parser = parser;
        this.expressionsCompiler = new ExpressionsCompiler(parser);
    }

    void compile(TokenString src, OutputFile out) throws SyntaxException {
        if (src.isEmpty()) {
            return;
        }
        switch (src.getFirstToken()) {
            case "push":
            case "pop":
                compilePushPop(src, parser.buildExpression(src), out);
                break;
            case "call":
            case "rcall":
            case "jmp":
            case "rjmp":
                compileCall(src, parser.buildExpression(src), out);
                break;
            case "if":
                if (compileIf(src, parser.buildExpression(src), out)) {
                    break;
                }
            default:
                compileDefault(src, out);
        }
    }


    private Procedure parseProcedureArgs(List<Argument> args, Expression expr) throws SyntaxException {
        if (expr.size() < 2) {
            wrongCallSyntaxError();
        }
        Token procedureName = expr.get(1);
        Procedure procedure = parser.procedures.get(procedureName.asString());
        if (procedure == null || expr.size() == 2) {
            return procedure;
        }
        if (expr.size() == 3) {
            String argInBrackets = expr.getLast().toString();
            if (!ParserUtils.isInBrackets(argInBrackets)) {
                wrongCallSyntaxError();
            }
            Expression argExpr = new Expression();
            argExpr.add(removeBracketFromToken(expr.getLast()));
            parseSingleAnonymousArg(procedure, args, argExpr);
            return procedure;
        }
        if (expr.size() == 5) {
            if (!expr.getLast(2).isOperator("(") || !expr.getLast().isOperator(")")) {
                wrongCallSyntaxError();
            }
            Expression argExpr = new Expression();
            argExpr.add(expr.getLast(1));
            parseSingleAnonymousArg(procedure, args, argExpr);
            return procedure;
        }
        if (!expr.get(2).isOperator("(") || !expr.getLast().isOperator(")")) {
            wrongCallSyntaxError();
        }
        int index = 3;
        while (index < expr.size()-1) {
            Token name = expr.get(index++);
            Token colon = expr.getIfExist(index++);
            if (colon == null || !colon.isOperator(":")) {
                wrongCallSyntaxError();
            }
            Expression argExpr = new Expression();
            while (index < expr.size()-1) {
                Token token = expr.get(index++);
                if (token.isOperator(",")) {
                    break;
                }
                argExpr.add(removeBracketFromToken(token));
            }
            Argument arg = new Argument(name.toString(), argExpr);
            args.add(arg);
        }
        return procedure;
    }

    private static Token removeBracketFromToken(Token t) {
        String ts = t.toString();
        if (ParserUtils.isInBrackets(ts)) {
            return new Token(t.getType(), ParserUtils.removeBrackets(ts));
        }
        return t;
    }


    private void parseSingleAnonymousArg(Procedure procedure, List<Argument> args, Expression expr) throws SyntaxException {
        if (procedure.args.size() != 1) {
            wrongCallSyntaxError();
        }
        String argName = procedure.args.keySet().iterator().next();
        Argument arg = new Argument(argName, expr);
        args.add(arg);
    }

    private void checkCallArguments(Procedure procedure, List<Argument> args) throws SyntaxException {
        for (Argument arg : args) {
            if (!procedure.args.containsKey(arg.name)) {
                throw new SyntaxException("wrong argument: " + arg.name);
            }
        }
    }

    private void compileCall(TokenString src, Expression expr, OutputFile out) throws SyntaxException {
        List<Argument> args = new ArrayList<>();
        Procedure procedure = parseProcedureArgs(args, expr);
        if (procedure == null) {
            if (expr.operatorsCount("(") > 0) {
                undefinedProcedureError(expr.get(1).asString());
            }
            out.appendCommand(src, expr.getFirst().asString(), expr.get(1));
            return;
        }
        checkCallArguments(procedure, args);
        out.addComment(src);

        for (Argument arg : args) {
            String argName = arg.name;
            Token argRegs = procedure.args.get(argName).register;
            Expression value = arg.expr;
            if (value.size() != 1 || !value.getFirst().equals(argRegs)) {
                addArgumentAssign(out, argRegs, value);
            } else {
                out.startNewLine().append(src.getIndent()).append("; ").append(argName).append(" = ").append(value);
            }
        } // for args
        out.appendCommand(src, src.getFirstToken(), procedure.name);
    }

    private void addArgumentAssign(OutputFile out, Token arg, Expression value) throws SyntaxException {
        //value.add(0, new Token(Token.TYPE_REGISTER, regName));
        value.add(0, arg);
        value.add(1, new Token(Token.TYPE_OPERATOR, "="));

        try {
            if (!expressionsCompiler.compile(new TokenString(""), value, out)) {
                throw new SyntaxException("wrong value: '" + value + "'");
            }
            //out.getLastLineBuilder().append("\t; ").append(argName).append(" = ").append(value);
        } catch (ExpressionsCompiler.CompileException e) {
            throw new SyntaxException(e.getMessage());
        }
    }


    private void compilePushPop(TokenString src, Expression expr, OutputFile out) throws SyntaxException {
        String cmd = expr.removeFirst().asString();
        for (Token t : expr) {
            checkRegister(t);
            out.appendCommand(src, cmd, t);
        }
    }


    private boolean compileIf(TokenString src, Expression expr, OutputFile out) throws SyntaxException {
        // if (a || b || c) action -> if (a) action, if (b) action, if (c) action
        return compileIf(src, expr, out, false);
    }

    boolean compileIf(TokenString src, Expression expr, OutputFile out, boolean inverse) throws SyntaxException {
        checkMinLength(expr, 5);
        boolean signed;
        Token t = expr.get(1);

        if (t.isSomeString("s")) {
            signed = true;
            expr.remove(1);
        } else if (t.isSomeString("u")) {
            signed = false;
            expr.remove(1);
        } else {
            signed = false;
        }
        Token label = null;
        String command = null;
        int closeBracketLastIndex;
        if (expr.getLast(1).isKeyword("goto") || "rjmp".equals(expr.getLast(1).asString())) {
            command = "rjmp";
            label = expr.getLast();
            closeBracketLastIndex = 2;
        } else if ("rcall".equals(expr.getLast(1).asString())) {
            command = "rcall";
            label = expr.getLast();
            closeBracketLastIndex = 2;
        } else if ("ret".equals(expr.getLast().asString())) {
            command = "ret";
            closeBracketLastIndex = 1;
        } else if ("reti".equals(expr.getLast().asString())) {
            command = "reti";
            closeBracketLastIndex = 1;
        } else if ("continue".equals(expr.getLast().asString()) && parser.getCurrentCycleBlock() != null) {
            command = "rjmp";
            label = new Token(Token.TYPE_OTHER, parser.getLastBlock().getLabelStart());
            closeBracketLastIndex = 1;
        } else if ("break".equals(expr.getLast().asString()) && parser.getCurrentCycleBlock() != null) {
            command = "rjmp";
            label = new Token(Token.TYPE_OTHER, parser.getLastBlock().buildEndLabel());
            closeBracketLastIndex = 1;
        } else {
            int bracketCode = 1;
            closeBracketLastIndex = 0;
            for (int i = 2; i < expr.size(); i++) {
                Token next = expr.get(i);
                if (next.isOperator(")")) {
                    bracketCode--;
                    if (bracketCode == 0) {
                        closeBracketLastIndex = i;
                        break;
                    }
                } else if (next.isOperator("(")) {
                    bracketCode++;
                }
            }
            if (closeBracketLastIndex > 0) {
                Expression subexpr = expr.subExpression(closeBracketLastIndex+1);
                OutputFile tempOut = new OutputFile();
                try {
                    new ExpressionsCompiler(parser).compile(src, subexpr, tempOut);
                    if (tempOut.size() != 1) {
                        invalidExpressionError("too big, one command expected");
                    }
                    command = tempOut.get(0);
                } catch (ExpressionsCompiler.CompileException e) {
                    invalidExpressionError();
                }
                closeBracketLastIndex = expr.size() - closeBracketLastIndex - 1;
            } else {
                invalidExpressionError();
            }
        }
        if (!expr.get(1).isOperator("(") || !expr.getLast(closeBracketLastIndex).isOperator(")")) {
            invalidExpressionError();
        }

        Token a1 = expr.get(2);
        Token a2 = expr.get(3);
        Token a3 = expr.get(4);
        Token a4 = expr.size() > 5 ? expr.get(5) : null;
        if (a1.isFlag()) {
            if (label == null) {
                unsupportedOperationError();
            }
            compileIfFlagExpression(src, inverse, a1, label, out);
        } else if (a1.isOperator("!") && a2.isFlag()) {
            compileIfFlagExpression(src, !inverse, a2, label, out);
        } else if (a1.isRegisterBit()) {
            compileIfRegisterBitExpression(src, inverse, a1, command, label, out);
        } else if (a1.isOperator("!") && a2.isRegisterBit()) {
            compileIfRegisterBitExpression(src, !inverse, a2, command, label, out);
        } else if (a1.isArrayIo() && a2.isOperator(".")) {
            compileIfIoBitExpression(src, inverse, a1, a3, command, label, out);
        } else if (a1.isOperator("!") && a2.isArrayIo() && a3.isOperator(".") && a4 != null) {
            compileIfIoBitExpression(src, !inverse, a2, a4, command, label, out);
        } else {
            out.addComment(src);
            String operation = a2.asString();
            if (inverse) {
                operation = inverseBinaryCompareOperation(operation);
            }
            if (label == null) {
                unsupportedOperationError();
            }
            compileIfBinaryExpression(src, signed, operation, a1, a3, label, out);
        }
        return true;
    }

    private String inverseBinaryCompareOperation(String operation) {
        switch (operation) {
            case "==": return "!=";
            case "!=": return "==";
            case ">": return "<=";
            case "<": return ">=";
            case "<=": return ">";
            case ">=": return "<";
        }
        throw new RuntimeException("can't inverse operation: " + operation);
    }

    private void compileIfFlagExpression(TokenString src, boolean not, Token flag, Token label, OutputFile out) {
        String cmd = not ? BRANCH_IF_FLAG_CLEAR_MAP.get(flag.asString()) : BRANCH_IF_FLAG_SET_MAP.get(flag.asString());
        out.appendCommand(src, cmd, label);
    }

    private void compileIfRegisterBitExpression(TokenString src, boolean not, Token regBit, String cmd, Token label, OutputFile out) throws SyntaxException {
        Token index = regBit.getBitIndex();
        checkBitIndex(index);
        out.appendCommand(src, not ? "sbrs" : "sbrc", regBit.asString(), index.asString());
        out.appendCommand(src, cmd, label);
    }

    private void compileIfIoBitExpression(TokenString src, boolean not, Token ioPort, Token index, String cmd, Token label, OutputFile out) throws SyntaxException {
        checkBitIndex(index);
        out.appendCommand(src, not ? "sbis" : "sbic", arrayIndexToPort(ioPort), index.asString());
        out.appendCommand(src, cmd, label);
    }

    private void compileIfBinaryExpression(TokenString src, boolean signed, String operation, Token left, Token right, Token label, OutputFile out) throws SyntaxException {
        String jumpCmd;
        //  BRCS = BRLO, BRCC = BRSH
        switch (operation) {
            case "==":
            case "!=":
                jumpCmd = "==".equals(operation) ? "breq" : "brne";
                addCompareInstruction(src, left, right, out);
                out.appendCommand(src, jumpCmd, label);
                break;
            case "<":
                addCompareInstruction(src, left, right, out);
                if (right.isNumber() && right.getNumberValue() == 0) {
                    jumpCmd = "brmi";
                } else  {
                    jumpCmd = signed ? "brlt" : "brlo";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            case ">=":
                addCompareInstruction(src, left, right, out);
                if (right.isNumber() && right.getNumberValue() == 0) {
                    jumpCmd = "brpl";
                } else  {
                    jumpCmd = signed ? "brge" : "brsh";
                }
                out.appendCommand(src, jumpCmd, label);
                break;

            case ">":
                // x > 0  ->   x >= 1
                // x > y  ->   y < x
                // x > k  ->   x >= k+1
                if (right.isAnyConst()) {
                    // TODO calculate value if possible
                    right = new Token(Token.TYPE_CONST_EXPRESSION, right.asString() + "+1");
                    addCompareInstruction(src, left, right, out);
                    jumpCmd = signed ? "brge" : "brsh";
                } else {
                    addCompareInstruction(src, right, left, out);
                    jumpCmd = signed ? "brlt" : "brlo";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            case "<=":
                // x <= y  ->  y >= x
                // x >= k  ->  x > k-1
                if (right.isAnyConst()) {
                    // TODO calculate value if possible
                    right = new Token(Token.TYPE_CONST_EXPRESSION, right.asString() + "-1");
                    addCompareInstruction(src, left, right, out);
                    jumpCmd = signed ? "brlt" : "brlo";
                } else {
                    addCompareInstruction(src, right, left, out);
                    jumpCmd = signed ? "brge" : "brsh";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            default:
                unsupportedOperationError();
        }

    }

    private void addCompareInstruction(TokenString src, Token left, Token right, OutputFile out) throws SyntaxException {
        if (left.isRegister() && right.isNumber() && right.getNumberValue() == 0) {
            out.appendCommand(src, "tst", left);
        } else if (left.isRegister() && right.isRegister()) {
            out.appendCommand(src, "cp", left, right);
        } else if (left.isRegister() && right.isAnyConst()) {
            out.appendCommand(src, "cpi", left, right);
//        } else if (left.isRegGroup() && right.isAnyConst()) {
//        } else if (left.isPair() && right.isAnyConst()) {
        } else if ((left.isRegGroup() || left.isPair()) && (right.isRegGroup() || right.isPair())) {
            if (left.size() != right.size()) {
                sizesMismatchError();
            }
            for (int i = 0; i < left.size(); i++) {
                int j = left.size() - 1 - i;
                Token reg1 = left.getReg(j);
                Token reg2 = right.getReg(j);
                String cmd = i == 0 ? "cp" : "cpc";
                out.appendCommand(src, cmd, reg1, reg2);
            }
        } else {
            unsupportedOperationError();
        }
    }


    private boolean canBeCompiled(TokenString src) {
        String first = src.getFirstToken();
        if (src.firstTokenIs(".") || ParserUtils.isInstruction(first) || first.startsWith("#")) {
            return false;
        }
        if (src.size() >= 2 && ":".equals(src.getToken(1))) {
            return false;
        }
//        Variable var = parser.getVariable(src.getFirstToken());
//        if (var != null && var.isPointer() && src.size() >= 2 && ":".equals(src.getToken(1))) {
//            return false;
//        }
        return true;
    }


    private void compileDefault(TokenString src, OutputFile out) throws SyntaxException {
        if (!canBeCompiled(src)) {
            out.add(src);
            return;
        }
        try {
            if (!expressionsCompiler.compile(src, parser.buildExpression(src), out)) {
                out.add(src);
            }
        } catch (ExpressionsCompiler.CompileException e) {
            throw new SyntaxException(e.getMessage() != null ? e.getMessage() : "expression error", e);
        }
    }

    private static void checkBitIndex(Token t) throws SyntaxException {
        if (t.isNumber()) {
            int val = t.getNumberValue();
            if (val < 0 || val > 7) {
                throw new SyntaxException("wrong index: " + val);
            }
        } else if (!t.isAnyConst() && !t.isSomeString()) {
            throw new SyntaxException("bit offset constant expected: " + t);
        }
    }

    private String arrayIndexToPort(Token tokenIndex) throws SyntaxException {
        Token.ArrayIndex index = tokenIndex.getIndex();
        if (index.hasModifier() || index.isPair()) {
            throw new SyntaxException("io");
        }
        return parser.gcc ? "_SFR_IO_ADDR(" + index.getName() + ")" : index.getName();
    }

    private static void checkRegister(Token t) throws SyntaxException {
        if (!t.isRegister()) {
            throw new SyntaxException("register expected: " + t);
        }
    }

    private static void checkMinLength(Expression expr, int minLength) throws SyntaxException {
        if (expr.size() < minLength) {
            invalidExpressionError();
        }
    }

    private static void invalidExpressionError() throws SyntaxException {
        throw new SyntaxException("invalid expression");
    }

    private static void invalidExpressionError(String msg) throws SyntaxException {
        throw new SyntaxException("invalid expression: " + msg);
    }

    private static void unsupportedOperationError() throws SyntaxException {
        throw new SyntaxException("unsupported operation");
    }

    private static void sizesMismatchError() throws  SyntaxException {
        throw new SyntaxException("sizes mismatch");
    }

    private static void wrongCallSyntaxError() throws SyntaxException {
        throw new SyntaxException("wrong call/jump syntax");
    }

    private static void undefinedProcedureError(String name) throws SyntaxException {
        throw new SyntaxException("undefined procedure: \"" + name + '"');
    }

}
