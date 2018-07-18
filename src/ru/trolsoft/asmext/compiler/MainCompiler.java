package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.data.Argument;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.processor.*;
import ru.trolsoft.asmext.utils.AsmInstruction;
import ru.trolsoft.asmext.utils.AsmUtils;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.compiler.AsmInstr.BRANCH_IF_FLAG_CLEAR_MAP;
import static ru.trolsoft.asmext.compiler.AsmInstr.BRANCH_IF_FLAG_SET_MAP;
import static ru.trolsoft.asmext.compiler.Cmd.*;

public class MainCompiler extends BaseCompiler {

    private final ExpressionsCompiler expressionsCompiler;


    public MainCompiler(Parser parser) {
        super(parser);
        this.expressionsCompiler = new ExpressionsCompiler(parser, this);
    }


    public void compile(TokenString src, OutputFile out) throws SyntaxException {
        if (src.isEmpty()) {
            return;
        }
        setup(src, out);
        switch (src.getFirstToken()) {
            case "push":
            case "pop":
                compilePushPop(parser.buildExpression(src));
                break;
            case "call":
            case "rcall":
            case "jmp":
            case "rjmp":
                compileCall(parser.buildExpression(src));
                break;
            case "if":
                if (compileIfNoBlock(parser.buildExpression(src))) {
                    break;
                }
            default:
                compileDefault();
        }
        cleanup();
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
                wrongArgumentError(arg.name);
            }
        }
    }


    private void compileCall(Expression expr) throws SyntaxException {
        List<Argument> args = new ArrayList<>();
        Procedure procedure = parseProcedureArgs(args, expr);
        if (procedure == null) {
            if (expr.operatorsCount("(") > 0) {
                undefinedProcedureError(expr.get(1).asString());
            }
            Cmd cmd = Cmd.fromToken(expr.getFirst());
            addCommand(cmd, expr.get(1));
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
        Cmd cmd = Cmd.fromStr(src.getFirstToken());
        addCommand(cmd, procedure.name);
    }

    private void addArgumentAssign(OutputFile out, Token arg, Expression value) throws SyntaxException {
        //value.add(0, new Token(Token.TYPE_REGISTER, regName));
        value.add(0, arg);
        value.add(1, new Token(Token.TYPE_OPERATOR, "="));

        try {
            if (!expressionsCompiler.compile(new TokenString(""), value, out)) {
                wrongValueError(value.toString());
            }
            //out.getLastLineBuilder().append("\t; ").append(argName).append(" = ").append(value);
        } catch (SyntaxException e) {
            error(e.getMessage(), e);
        }
    }


    private void compilePushPop(Expression expr) throws SyntaxException {
        Cmd cmd = Cmd.fromToken(expr.removeFirst());
        for (Token t : expr) {
            checkRegister(t);
            addCommand(cmd, t);
        }
    }


    boolean compileIfNoBlock(Expression expr) throws SyntaxException {
        return compileIfExpression(expr, false, false);
    }

    public boolean compileIfExpressionBlock(TokenString src, Expression expr, boolean inverse) throws SyntaxException {
        setup(src);
        return compileIfExpression(expr, inverse, true);
    }

    private AsmInstruction compileIfBodyInstruction(Expression expr) throws SyntaxException {
        Token firstBodyToken = expr.getFirst();
        if (firstBodyToken.isKeyword("goto") || "rjmp".equals(firstBodyToken.asString())) {
            checkExpressionSize(expr, 2);
            return new AsmInstruction(RJMP, expr.getLast());
        } else if (firstBodyToken.isKeyword("continue") && parser.getCurrentCycleBlock() != null) {
            checkExpressionSize(expr, 1);
            return new AsmInstruction(RJMP, parser.getLastBlock().getLabelStartToken());
        } else if (firstBodyToken.isKeyword("break") && parser.getCurrentCycleBlock() != null) {
            return new AsmInstruction(RJMP, parser.getLastBlock().buildEndLabelToken());
        } else {
            OutputFile tempOut = new OutputFile();
            if (new ExpressionsCompiler(parser, this).compile(src, expr, tempOut)) {
                if (tempOut.size() != 1) {
                    invalidExpressionError("too big, one command expected");
                }
                return AsmUtils.parseLine(tempOut.get(0));
            } else {
                return AsmUtils.parseExpression(expr);
            }
        }
    }

    private boolean compileSimpleIf(Expression condition, Expression bodyExpr, boolean inverse, boolean signed) throws SyntaxException {
        AsmInstruction instruction = compileIfBodyInstruction(bodyExpr);
        if (instruction == null) {
            unsupportedOperationError();
            return false;
        }
        if (condition.getFirst().isOperator("!")) {
            inverse = !inverse;
            condition.removeFirst();
        }
        Token a1 = condition.getFirst();
        Token a2 = condition.getIfExist(1);
        Token a3 = condition.getIfExist(2);
        Token label = instruction.getCommand() == RJMP ? instruction.getArg1Token() : null;
        boolean notEqualCompare = a2 != null && ((a2.isOperator("!=") && !inverse) || (a2.isOperator("==") && inverse));
        if (a1.isFlag()) {
            if (label == null) {
                unsupportedOperationError();
            }
            checkExpressionSize(condition, 1);
            compileIfFlagExpression(inverse, a1, instruction.getArg1Token());
        } else if (a1.isRegisterBit()) {
            checkExpressionSize(condition, 1);
            compileIfRegisterBitExpression(inverse, a1, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
        } else if (a1.isArrayIo() && a2 != null && a2.isOperator(".")) {
            checkExpressionSize(condition, 3);
            compileIfIoBitExpression(inverse, a1, a3, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
        } else if (a1.isRegister() && notEqualCompare && a3.isRegister()) {
            checkExpressionSize(condition, 3);
            compileIfTwoRegsNotEquals(a1, a3, instruction);
        } else {
            checkExpressionSize(condition, 3);
            out.addComment(src);
            String operation = a2.asString();
            if (inverse) {
                operation = inverseBinaryCompareOperation(operation);
            }
            if (label == null) {
                unsupportedOperationError();
            }

            compileIfBinaryExpression(signed, operation, a1, a3, label);
        }
        return true;

    }

    private boolean compileIfExpression(Expression expr, boolean inverse, boolean isBlock) throws SyntaxException {
        if (!expr.getFirst().isKeyword("if")) {
            throw new RuntimeException("'if' not found");
        }
        checkExpressionMinLength(expr, 5);
        expr.removeFirst(); // if
        boolean signed = checkSignedIf(expr);
        if (!expr.getFirst().isOperator("(")) {
            unexpectedExpressionError("if without ()");
        }
        int closeBracketIndex = expr.findCloseBracketIndex(0);
        if (closeBracketIndex < 0) {
            unexpectedExpressionError("close bracket not found ')'");
        }
        Expression condition = expr.subExpression(1, closeBracketIndex-1);
        Expression body = expr.subExpression(closeBracketIndex + 1);

        boolean hasOr = condition.operatorsCount("||") > 0;
        boolean hasAnd = condition.operatorsCount("&&") > 0;
        if (hasOr && hasAnd) {
            unsupportedOperationError();
        }
        if (hasOr) {
            return compileMultipleOrIf(condition, body, signed, inverse, isBlock);
        } else if (hasAnd) {
            return compileMultipleAndIf(condition, body, signed, inverse, isBlock);
        } else {
            return compileSimpleIf(condition, body, inverse, signed);
        }
    }


    private boolean compileMultipleOrIf(Expression condition, Expression body, boolean signed, boolean inverse,
                                        boolean isBlock) throws SyntaxException {
        List<Expression> conditionsList = condition.splitByOperator("||");
        boolean result = true;
        if (isBlock) {
            Expression lastBody = new Expression();
            String bodyEndLabel = body.get(1).asString();
            String bodyStartLabel = bodyEndLabel + "_body";
            lastBody.add(new Token(Token.TYPE_KEYWORD, "goto"));
            lastBody.add(new Token(Token.TYPE_OTHER, bodyStartLabel));
            for (int i = 0; i < conditionsList.size(); i++) {
                Expression c = conditionsList.get(i);
                boolean last = i == conditionsList.size() - 1;
                Expression thisBody = last ? body : lastBody;
                if (!compileSimpleIf(c, thisBody, last, signed)) {
                    result = false;
                }
            }
            addLabel(bodyStartLabel);
        } else {
            for (Expression c : conditionsList) {
                if (!compileSimpleIf(c, body, inverse, signed)) {
                    result = false;
                }
            }
        }
        return result;
    }

    private boolean compileMultipleAndIf(Expression condition, Expression body, boolean signed, boolean inverse, boolean isBlock) throws SyntaxException {
        List<Expression> conditionsList = condition.splitByOperator("&&");
        boolean result = true;
        if (isBlock) {
            for (Expression c : conditionsList) {
                if (!compileSimpleIf(c, body, inverse, signed)) {
                    result = false;
                }
            }
        } else {
            String labelEnd = parser.generateLabelName("if_and_");
            Expression firstBodies = new Expression();
            firstBodies.add(new Token(Token.TYPE_KEYWORD, "goto"));
            firstBodies.add(new Token(Token.TYPE_OTHER, labelEnd));

            for (int i = 0; i < conditionsList.size(); i++) {
                Expression c = conditionsList.get(i);
                boolean last = i == conditionsList.size() - 1;
                Expression thisBody = !last ? firstBodies : body;
                boolean thisInverse = last == inverse;
                if (!compileSimpleIf(c, thisBody, thisInverse, signed)) {
                    result = false;
                }
            }
            addLabel(labelEnd);
        }
        return result;
    }

    private static boolean checkSignedIf(Expression expr) {
        Token t = expr.getFirst();

        if (t.isSomeString("s")) {
            expr.removeFirst();
            return true;
        } else if (t.isSomeString("u")) {
            expr.removeFirst();
            return false;
        }
        return false;
    }


    private void compileIfTwoRegsNotEquals(Token reg1, Token reg2, AsmInstruction instruction) throws SyntaxException {
        addCommand(CPSE, reg1, reg2);
        addCommand(instruction);
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

    private void compileIfFlagExpression(boolean not, Token flag, Token label) throws SyntaxException {
        Cmd cmd = not ? BRANCH_IF_FLAG_CLEAR_MAP.get(flag.asString()) : BRANCH_IF_FLAG_SET_MAP.get(flag.asString());
        addCommand(cmd, label);
    }

    private void compileIfRegisterBitExpression(boolean not, Token regBit, Cmd cmd, Token label, String arg2) throws SyntaxException {
        Token index = regBit.getBitIndex();
        checkBitIndex(index);
        addCommand(not ? SBRS : SBRC, regBit.asString(), index.asString());
        addCommand(cmd, label, arg2);
    }

    private void compileIfIoBitExpression(boolean not, Token ioPort, Token index, Cmd cmd, Token label, String arg2) throws SyntaxException {
        checkBitIndex(index);
        addCommand(not ? SBIS : SBIC, arrayIndexToPort(ioPort), index.asString());
        addCommand(cmd, label, arg2);
    }

    private void compileIfBinaryExpression(boolean signed, String operation, Token left, Token right, Token label) throws SyntaxException {
        Cmd jumpCmd;
        //  BRCS = BRLO, BRCC = BRSH
        switch (operation) {
            case "==":
            case "!=":
                jumpCmd = "==".equals(operation) ? BREQ : BRNE;
                addCompareInstruction(left, right);
                addCommand(jumpCmd, label);
                break;
            case "<":
                addCompareInstruction(left, right);
                if (right.isNumber() && right.getNumberValue() == 0) {
                    jumpCmd = BRMI;
                } else  {
                    jumpCmd = signed ? BRLT : BRLO;
                }
                addCommand(jumpCmd, label);
                break;
            case ">=":
                addCompareInstruction(left, right);
                if (right.isNumber() && right.getNumberValue() == 0) {
                    jumpCmd = BRPL;
                } else  {
                    jumpCmd = signed ? BRGE : BRSH;
                }
                addCommand(jumpCmd, label);
                break;

            case ">":
                // x > 0  ->   x >= 1
                // x > y  ->   y < x
                // x > k  ->   x >= k+1
                if (right.isAnyConst()) {
                    // TODO calculate value if possible
                    right = new Token(Token.TYPE_CONST_EXPRESSION, right.asString() + "+1");
                    addCompareInstruction(left, right);
                    jumpCmd = signed ? BRGE : BRSH;
                } else {
                    addCompareInstruction(right, left);
                    jumpCmd = signed ? BRLT : BRLO;
                }
                addCommand(jumpCmd, label);
                break;
            case "<=":
                // x <= y  ->  y >= x
                // x >= k  ->  x > k-1
                if (right.isAnyConst()) {
                    // TODO calculate value if possible
                    right = new Token(Token.TYPE_CONST_EXPRESSION, right.asString() + "-1");
                    addCompareInstruction(left, right);
                    jumpCmd = signed ? BRLT : BRLO;
                } else {
                    addCompareInstruction(right, left);
                    jumpCmd = signed ? BRGE : BRSH;
                }
                addCommand(jumpCmd, label);
                break;
            default:
                unsupportedOperationError();
        }

    }

    private void addCompareInstruction(Token left, Token right) throws SyntaxException {
        if (left.isRegister() && right.isNumber() && right.getNumberValue() == 0) {
            addCommand(TST, left);
        } else if (left.isRegister() && right.isRegister()) {
            addCommand(CP, left, right);
        } else if (left.isRegister() && right.isAnyConst()) {
            addCommand(CPI, left, right);
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
                addCommand(i == 0 ? CP : CPC, reg1, reg2);
            }
        } else {
            unsupportedOperationError();
        }
    }


    private static boolean canBeCompiled(TokenString src) {
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


    private void compileDefault() throws SyntaxException {
        if (!canBeCompiled(src)) {
            out.add(src);
            return;
        }
        if (!expressionsCompiler.compile(src, parser.buildExpression(src), out)) {
            out.add(src);
        }
    }



}
