package ru.trolsoft.asmext.utils;

import ru.trolsoft.asmext.compiler.Cmd;
import ru.trolsoft.asmext.processor.Token;

public class AsmInstruction {
    private final Cmd command;
    private final String arg1;
    private final String arg2;

    public AsmInstruction(Cmd cmd, String arg1, String arg2) {
        this.command = cmd;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public AsmInstruction(Cmd cmd, Token arg1, Token arg2) {
        this.command = cmd;
        this.arg1 = arg1.asString();
        this.arg2 = arg2.asString();
    }

    public AsmInstruction(Cmd cmd, Token arg) {
        this.command = cmd;
        this.arg1 = arg.asString();
        this.arg2 = null;
    }

    public Cmd getCommand() {
        return command;
    }

    public String getArg1Str() {
        return arg1;
    }

    public String getArg2Str() {
        return arg2;
    }

    public Token getArg1Token() {
        return arg1 == null ? null : new Token(Token.TYPE_OTHER, arg1);
    }

    public Token getArg2Token() {
        return arg2 == null ? null : new Token(Token.TYPE_OTHER, arg2);
    }
}
