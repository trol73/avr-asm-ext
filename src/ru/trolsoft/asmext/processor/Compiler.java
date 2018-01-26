package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.Argument;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;

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
                if (compileIfGoto(src, parser.buildExpression(src), out)) {
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
        if (expr.size() == 2) {
            return procedure;
        }
        if (expr.size() == 3) {
            String argInBrackets = expr.getLast().asString();
            if (!ParserUtils.isInBrackets(argInBrackets)) {
                wrongCallSyntaxError();
            }
            Expression argExpr = new Expression(new TokenString(ParserUtils.removeBrackets(argInBrackets)));
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
            Token colon = index < expr.size() ? expr.get(index++) : null;
            if (colon == null || !colon.isOperator(":")) {
                wrongCallSyntaxError();
            }
            Expression argExpr = new Expression();
            while (index < expr.size()-1) {
                Token token = expr.get(index++);
                if (token.isOperator(",")) {
                    break;
                }
                argExpr.add(token);
            }
            Argument arg = new Argument(name.toString(), argExpr);
            args.add(arg);
        }
        return procedure;
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


    private boolean compileIfGoto(TokenString src, Expression expr, OutputFile out) throws SyntaxException {
        checkMinLength(expr, 6);
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
        if (!expr.getLast(1).isKeyword("goto") || !expr.get(1).isOperator("(") || !expr.getLast(2).isOperator(")")) {
            invalidExpressionError();
        }

        Token left = expr.get(2);
        Token operation = expr.get(3);
        Token right = expr.get(4);
        Token label = expr.getLast();

        out.addComment(src);
        String jumpCmd;
        //  BRCS = BRLO, BRCC = BRSH
        switch (operation.asString()) {
            case "==":
            case "!=":
                jumpCmd = operation.isOperator("==") ? "breq" : "brne";
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
        return true;
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
