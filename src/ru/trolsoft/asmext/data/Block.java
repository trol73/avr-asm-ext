package ru.trolsoft.asmext.data;

import ru.trolsoft.asmext.processor.Expression;
import ru.trolsoft.asmext.processor.Token;

public class Block {
    public static final int BLOCK_LOOP = 1;
    public static final int BLOCK_IF = 2;
    public static final int BLOCK_ELSE = 3;
    public static final int BLOCK_BYTES = 4;
    public static final int BLOCK_DO_WHILE = 5;

    public final int type;
    public final int beginLineNumber;
    public final Expression expr;
    private String labelStart;
    private String labelEnd;

    public Block(int type, Expression expr, int beginLine) {
        this.type = type;
        this.expr = expr;
        this.beginLineNumber = beginLine;
    }

    public Block(int type, Expression expr, int beginLine, String labelStart) {
        this.type = type;
        this.expr = expr;
        this.beginLineNumber = beginLine;
        this.labelStart = labelStart;
    }

//    public Block(int type, String[] args, int beginLine) {
//        this(type, buildList(args), beginLine);
//    }

//    private static List<String> buildList(String args[]) {
//        List<String> result = new ArrayList<>();
//        for (String s : args) {
//            if (!s.trim().isEmpty()) {
//                result.add(s);
//            }
//        }
//        return result;
//    }

    public String buildEndLabel() {
        if (labelEnd == null) {
            labelEnd = labelStart + "_end";
        }
        return labelEnd;
    }

    public String getLabelStart() {
        return labelStart;
    }

    public String getLabelEnd() {
        return labelEnd;
    }

    public Token getLabelStartToken() {
        return new Token(Token.TYPE_OTHER, labelStart);
    }

    public Token buildEndLabelToken() {
        return new Token(Token.TYPE_OTHER, buildEndLabel());
    }
}
