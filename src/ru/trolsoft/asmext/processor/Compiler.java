package ru.trolsoft.asmext.processor;

import ru.trolsoft.asmext.data.NamedPair;
import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

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
                compileCall(src, out);
                break;
            case "if":
                if (compileIfGoto(src, parser.buildExpression(src), out)) {
                    break;
                }
            default:
                compileDefault(src, out);
        }
    }


    private Procedure parseProcCallArgs(List<NamedPair> args, TokenString src) throws SyntaxException {
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
        for (String s : src) {
            if (s.trim().isEmpty()) {
                continue;
            }
            switch (state) {
                case STATE_WAIT_INSTRUCTION:
                    if (!s.equals(src.getFirstToken())) {
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
                    } else {
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
                    throw new SyntaxException("wrong call syntax");
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

    private void compileCall(TokenString src, OutputFile out) throws SyntaxException {
        List<NamedPair> args = new ArrayList<>();
        Procedure procedure = parseProcCallArgs(args, src);
        if (procedure != null) {
            checkCallArguments(procedure, args);
            out.addComment(src);

            for (NamedPair arg : args) {
                String argName = arg.name;
                String regName = procedure.args.get(argName).register;
                String value = arg.value;
                if (!regName.equalsIgnoreCase(value)) {
                    addArgumentAssign(out, argName, regName, value);
                } else {
                    out.startNewLine().append(src.getIndent()).append("; ").append(argName).append(" = ").append(value);
                }
            } // for args
            out.appendCommand(src, src.getFirstToken(), procedure.name);
            return;
        } // if procedure

        compileDefault(src, out);

    }

    private void addArgumentAssign(OutputFile out, String argName, String regName, String value) throws SyntaxException {
        StringTokenizer tokenizer = new StringTokenizer(value, "+-", true);
        String[] t = new String[2 + tokenizer.countTokens()];
        t[0] = regName;
        t[1] = "=";
        int i = 2;
        while (tokenizer.hasMoreTokens()) {
            t[i++] = tokenizer.nextToken();
        }
        try {
            TokenString ts = new TokenString(t);
            if (!expressionsCompiler.compile(ts, parser.buildExpression(ts), out)) {
                throw new SyntaxException("wrong value: '" + value + "'");
            }
            out.getLastLineBuilder().append("\t; ").append(argName).append(" = ").append(value);//.append("\n");
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
        Variable var = parser.getVariable(src.getFirstToken());
        if (var != null && var.isPointer() && src.size() >= 2 && ":".equals(src.getToken(1))) {
            return false;
        }
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

}
