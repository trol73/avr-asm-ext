package ru.trolsoft.asmext.utils;

import ru.trolsoft.asmext.compiler.Cmd;
import ru.trolsoft.asmext.processor.SyntaxException;
import ru.trolsoft.asmext.processor.Token;

public class AsmUtils {
    public static class Instruction {
        private final Cmd command;
        private final String arg1;
        private final String arg2;

        public Instruction(Cmd cmd, String arg1, String arg2) {
            this.command = cmd;
            this.arg1 = arg1;
            this.arg2 = arg2;
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

    public static Instruction parseLine(String s) throws SyntaxException {
        TokenString ts = new TokenString(s);
        ts.removeEmptyTokens();
        Cmd cmd = Cmd.fromStr(ts.getFirstToken());
        ts.removeFirstToken();
        if (ts.size() == 0) {
            return new Instruction(cmd, null, null);
        } else if (ts.size() == 1) {
            return new Instruction(cmd, ts.getToken(0), null);
        }
        String arg1 = ts.getFirstToken();
        ts.removeFirstToken();
        if (!",".equals(ts.getFirstToken())) {
            throw new SyntaxException("comma expected in \"" + s + "\"");
        }
        ts.removeFirstToken();
        if (ts.size() == 1) {
            return new Instruction(cmd, arg1, ts.getFirstToken());
        }

        StringBuilder sb2 = new StringBuilder();
        for (String part : ts) {
            sb2.append(part);
        }
        return new Instruction(cmd, arg1, sb2.toString());
    }
}
