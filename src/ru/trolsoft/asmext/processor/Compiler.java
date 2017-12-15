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
                compilePushPop(src, out);
                break;
            case "call":
            case "rcall":
            case "jmp":
            case "rjmp":
                compileCall(src, out);
                break;
            case "if":
                if (compileIfGoto(src, out)) {
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
            if (s.trim().isEmpty()) {// || ParserUtils.isComment(s)) {
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
                    } else { //if (!ParserUtils.isComment(s)) {
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
                    //if (!ParserUtils.isComment(s)) {
                        throw new SyntaxException("wrong call syntax");
                    //}
                    //break;
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
            if (!expressionsCompiler.compile(new TokenString(t), out)) {
                throw new SyntaxException("wrong value: '" + value + "'");
            }
            out.getLastLineBuilder().append("\t; ").append(argName).append(" = ").append(value);//.append("\n");
        } catch (ExpressionsCompiler.CompileException e) {
            throw new SyntaxException(e.getMessage());
        }
    }


    private void compilePushPop(TokenString src, OutputFile out) {
        List<String> regs = new ArrayList<>();
        for (String token : src) {
            if (ParserUtils.isRegister(token)) {
                regs.add(token);
            } else {
                // TODO !!!
            }
        }
        if (regs.size() <= 1) {
            out.add(src);
            return;
        }

        for (String reg : regs) {
            out.appendCommand(src, src.getFirstToken(), reg);
        }
    }


    private boolean compileIfGoto(TokenString src, OutputFile out) throws SyntaxException {
        src.removeEmptyTokens();
        List<String> tokens = src.getTokens();
        boolean signed;
        if ("s".equals(tokens.get(1))) {
            signed = true;
            tokens.remove(1);
        } else if ("u".equals(tokens.get(1))) {
            signed = false;
            tokens.remove(1);
        } else {
            signed = false;
        }

        if (tokens.size() < 8 || !"goto".equals(tokens.get(tokens.size()-2)) || !"(".equals(tokens.get(1)) || !")".equals(tokens.get(tokens.size()-3))) {
            return false;
        }
        int index = 2;
        String reg = tokens.get(index++);
        if (!ParserUtils.isRegister(reg)) {
            throw new SyntaxException("Invalid expression");
        }
        List<String> regs = new ArrayList<>();
        regs.add(reg);
        String next = tokens.get(index);
        if (".".equals(next)) {
            index++;
            while (".".equals(next) || ParserUtils.isRegister(next)) {
                if (!".".equals(next)) {
                    regs.add(next);
                }
                next = tokens.get(index++);
                if (index >= tokens.size()) {
                    break;
                }
            }
            index--;
        }
        String operation = tokens.get(index++);
        String arg = tokens.get(index++);
        List<String> args = new ArrayList<>();
        args.add(arg);
        next = tokens.get(index);
        if (".".equals(next)) {
            index++;
            while (".".equals(next) || ParserUtils.isRegister(next)) {
                if (!".".equals(next)) {
                    args.add(next);
                }
                next = tokens.get(index++);
                if (index >= tokens.size()) {
                    break;
                }
            }
            index--;
        }
        next = tokens.get(index);
        if (!")".equals(next)) {
            if (ParserUtils.isConstExpression(arg)) {
                arg += next;
                int bc = 0;
                loop:
                while (true) {
                    next = tokens.get(++index);
                    switch (next) {
                        case "(":
                            bc++;
                            break;
                        case ")":
                            if (bc == 0) break loop;
                            bc--;
                            break;
                    }
                    arg += next;
                }
                if (!ParserUtils.isConstExpression(arg)) {
                    throw new SyntaxException("Invalid expression");
                }
            } else {
                throw new SyntaxException("Invalid expression");
            }
        }
        Integer argVal = ParserUtils.isNumber(arg) ? ParserUtils.parseValue(arg) : null;
        String label = tokens.get(tokens.size()-1);
        out.addComment(src);
        String jumpCmd;
        //  BRCS = BRLO, BRCC = BRSH
        switch (operation) {
            case "==":
            case "!=":
                jumpCmd = "==".equals(operation) ? "breq" : "brne";
                if (argVal != null && argVal == 0) {
                    out.appendCommand(src, "tst", reg);
                } else if (ParserUtils.isRegister(arg)) {
                    addCompareInstruction(src, regs, args, out);
                } else {
                    out.appendCommand(src,"cpi", reg, arg);
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            case "<":
                if (argVal != null && argVal == 0) {
                    out.appendCommand(src,"tst", reg);
                    jumpCmd = "brmi";
                } else if (ParserUtils.isRegister(arg)) {
                    addCompareInstruction(src, regs, args, out);
                    jumpCmd = signed ? "brlt" : "brlo";
                } else {
                    out.appendCommand(src, "cpi", reg, arg);
                    jumpCmd = signed ? "brlt" : "brlo";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            case ">=":
                if (argVal != null && argVal == 0) {
                    out.appendCommand(src, "tst", reg);
                    jumpCmd = "brpl";
                } else if (ParserUtils.isRegister(arg)) {
                    //out.append(firstSpaces).append("cp\t").append(reg).append(", ").append(arg).append('\n');
                    addCompareInstruction(src, regs, args, out);
                    jumpCmd = signed ? "brge" : "brsh";
                } else {
                    out.appendCommand(src, "cpi", reg, arg);
                    jumpCmd = signed ? "brge" : "brsh";
                }
                out.appendCommand(src, jumpCmd, label);
                break;

            case ">":
                // x > 0  ->   x >= 1
                // x > y  ->   y < x
                // x > k  ->   x >= k+1
                if (ParserUtils.isRegister(arg)) {
                    //out.append(firstSpaces).append("cp\t").append(arg).append(", ").append(reg).append('\n');
                    addCompareInstruction(src, args, regs, out);
                    jumpCmd = signed ? "brlt" : "brlo";
                } else {
                    out.appendCommand(src, "cpi", reg, arg + "+1");
                    jumpCmd = signed ? "brge" : "brsh";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            case "<=":
                // x <= y  ->  y >= x
                // x >= k  ->  x > k-1
                if (ParserUtils.isRegister(arg)) {
                    addCompareInstruction(src, args, regs, out);
                    jumpCmd = signed ? "brge" : "brsh";
                } else {
                    out.appendCommand(src, "cpi", reg, arg + "-1");
                    jumpCmd = signed ? "brlt" : "brlo";
                }
                out.appendCommand(src, jumpCmd, label);
                break;
            default:
                throw new SyntaxException("unsupported operation");
        }
        return true;
    }

    private void addCompareInstruction(TokenString src, List<String> arg1, List<String> arg2, OutputFile out) throws SyntaxException {
        if (arg1.size() != arg2.size()) {
            throw new SyntaxException("unsupported operation");
        }
        for (int i = 0; i < arg1.size(); i++) {
            int j = arg1.size() - 1 - i;
            String reg = arg1.get(j);
            String arg = arg2.get(j);
            String cmd = i == 0 ? "cp" : "cpc";
            out.appendCommand(src, cmd, reg, arg);
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
            if (!expressionsCompiler.compile(src, out)) {
                out.add(src);
            }
        } catch (ExpressionsCompiler.CompileException e) {
            throw new SyntaxException(e.getMessage() != null ? e.getMessage() : "expression error", e);
        }
    }

}
