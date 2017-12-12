package ru.trolsoft.asmext.data;

import java.util.ArrayList;
import java.util.List;

public class Block {
    public static final int TYPE_LOOP = 1;

    public final int type;
    public final List<String> args;
    public final int beginLine;
    public String reg;
    public String label;

    public Block(int type, List<String> args, int beginLine) {
        this.type= type;
        this.args = args;
        this.beginLine = beginLine;
    }

    public Block(int type, String[] args, int beginLine) {
        this(type, buildList(args), beginLine);
    }

    private static List<String> buildList(String args[]) {
        List<String> result = new ArrayList<>();
        for (String s : args) {
            if (!s.trim().isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
