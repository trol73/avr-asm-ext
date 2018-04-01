package ru.trolsoft.asmext.utils;

import ru.trolsoft.asmext.compiler.Cmd;
import ru.trolsoft.asmext.processor.Expression;
import ru.trolsoft.asmext.processor.SyntaxException;
import ru.trolsoft.asmext.processor.Token;

public class AsmUtils {


    public static AsmInstruction parseLine(String s) throws SyntaxException {
        TokenString ts = new TokenString(s);
        ts.removeEmptyTokens();
        Cmd cmd = Cmd.fromStr(ts.getFirstToken());
        ts.removeFirstToken();
        if (ts.size() == 0) {
            return new AsmInstruction(cmd, (String) null, null);
        } else if (ts.size() == 1) {
            return new AsmInstruction(cmd, ts.getToken(0), null);
        }
        String arg1 = ts.getFirstToken();
        ts.removeFirstToken();
        if (!",".equals(ts.getFirstToken())) {
            throw new SyntaxException("comma expected in \"" + s + "\"");
        }
        ts.removeFirstToken();
        if (ts.size() == 1) {
            return new AsmInstruction(cmd, arg1, ts.getFirstToken());
        }

        StringBuilder sb2 = new StringBuilder();
        for (String part : ts) {
            sb2.append(part);
        }
        return new AsmInstruction(cmd, arg1, sb2.toString());
    }

    public static AsmInstruction parseExpression(Expression expr) throws SyntaxException {
        Cmd cmd = Cmd.fromToken(expr.getFirst());
        Token arg1 = expr.getIfExist(1);
        Token comma = expr.getIfExist(2);
        if (comma != null && !comma.isOperator(",")) {
            throw new SyntaxException("comma expected, but \"" + comma + "\" found");
        }
        if (expr.size() > 4) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < expr.size(); i++) {
                sb.append(expr.get(i));
            }
            return new AsmInstruction(cmd, arg1.toString(), sb.toString());
        }
        Token arg2 = expr.getIfExist(3);
        String arg1s = arg1 != null ? arg1.toString() : null;
        String arg2s = arg2 != null ? arg2.toString() : null;
        return new AsmInstruction(cmd, arg1s, arg2s);
    }
}
