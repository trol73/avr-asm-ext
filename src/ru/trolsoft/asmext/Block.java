package ru.trolsoft.asmext;

import java.util.ArrayList;
import java.util.List;

class Block {
    static final int TYPE_LOOP = 1;

    final int type;
    final List<String> args;
    final int beginLine;
    String reg;
    String label;

    Block(int type, List<String> args, int beginLine) {
        this.type= type;
        this.args = args;
        this.beginLine = beginLine;
    }

    Block(int type, String[] args, int beginLine) {
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
