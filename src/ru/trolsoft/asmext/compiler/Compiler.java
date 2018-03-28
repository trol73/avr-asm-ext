package ru.trolsoft.asmext.compiler;

import ru.trolsoft.asmext.data.Argument;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.processor.*;
import ru.trolsoft.asmext.utils.AsmUtils;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.List;

import static ru.trolsoft.asmext.compiler.AsmInstr.BRANCH_IF_FLAG_CLEAR_MAP;
import static ru.trolsoft.asmext.compiler.AsmInstr.BRANCH_IF_FLAG_SET_MAP;
import static ru.trolsoft.asmext.compiler.Cmd.*;

public class Compiler extends BaseCompiler {

    private final ExpressionsCompiler expressionsCompiler;


    public Compiler(Parser parser) {
        super(parser);
        this.expressionsCompiler = new ExpressionsCompiler(parser);
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
                if (compileIf(parser.buildExpression(src))) {
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


    private boolean compileIf(Expression expr) throws SyntaxException {
        // if (a || b || c) action -> if (a) action, if (b) action, if (c) action
        return compileIf(expr, false);
    }

    public boolean compileIf(TokenString src, Expression expr, boolean inverse, OutputFile out) throws SyntaxException {
        setup(src, out);
        return compileIf(expr, inverse);
    }

    private boolean compileIf(Expression expr, boolean inverse) throws SyntaxException {
        checkExpressionMinLength(expr, 5);
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
        Cmd command = null;
        AsmUtils.Instruction instruction = null;
        int closeBracketLastIndex;
        // TODO убрать частные случаи инструкций
        if (expr.getLast(1).isKeyword("goto") || "rjmp".equals(expr.getLast(1).asString())) {
            command = RJMP;
            label = expr.getLast();
            closeBracketLastIndex = 2;
        } else if ("rcall".equals(expr.getLast(1).asString())) {
            command = RCALL;
            label = expr.getLast();
            closeBracketLastIndex = 2;
        } else if ("ret".equals(expr.getLast().asString())) {
            command = RET;
            closeBracketLastIndex = 1;
        } else if ("reti".equals(expr.getLast().asString())) {
            command = RETI;
            closeBracketLastIndex = 1;
        } else if ("continue".equals(expr.getLast().asString()) && parser.getCurrentCycleBlock() != null) {
            command = RJMP;
            label = new Token(Token.TYPE_OTHER, parser.getLastBlock().getLabelStart());
            closeBracketLastIndex = 1;
        } else if ("break".equals(expr.getLast().asString()) && parser.getCurrentCycleBlock() != null) {
            command = RJMP;
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
                    instruction = AsmUtils.parseLine(tempOut.get(0));
                } catch (SyntaxException e) {
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
            compileIfFlagExpression(inverse, a1, label);
        } else if (a1.isOperator("!") && a2.isFlag()) {
            compileIfFlagExpression(!inverse, a2, label);
        } else if (a1.isRegisterBit()) {
            if (instruction != null) {
                compileIfRegisterBitExpression(inverse, a1, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
            } else {
                compileIfRegisterBitExpression(inverse, a1, command, label, null);
            }
        } else if (a1.isOperator("!") && a2.isRegisterBit()) {
            if (instruction != null) {
                compileIfRegisterBitExpression(!inverse, a2, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
            } else {
                compileIfRegisterBitExpression(!inverse, a2, command, label, null);
            }
        } else if (a1.isArrayIo() && a2.isOperator(".")) {
            if (instruction != null) {
                compileIfIoBitExpression(inverse, a1, a3, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
            } else {
                compileIfIoBitExpression(inverse, a1, a3, command, label, null);
            }
        } else if (a1.isOperator("!") && a2.isArrayIo() && a3.isOperator(".") && a4 != null) {
            if (instruction != null) {
                compileIfIoBitExpression(!inverse, a2, a4, instruction.getCommand(), instruction.getArg1Token(), instruction.getArg2Str());
            } else {
                compileIfIoBitExpression(!inverse, a2, a4, command, label, null);
            }
        } else if (instruction != null && a1.isRegister() && a2.isOperator("!=") && a3.isRegister() && a4 != null && a4.isOperator(")")) {
            compileIfTwoRegsNotEquals(a1, a3, instruction);
        } else {
            if (instruction != null || a4 == null || !a4.isOperator(")")) {
                invalidExpressionError();
            }
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

    private void compileIfTwoRegsNotEquals(Token reg1, Token reg2, AsmUtils.Instruction instruction) throws SyntaxException {
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
                Cmd cmd = i == 0 ? CP : CPC;
                addCommand(cmd, reg1, reg2);
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
