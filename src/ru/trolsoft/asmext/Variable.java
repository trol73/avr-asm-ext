package ru.trolsoft.asmext;

public class Variable {
    public final String name;
    final Type type;

    public enum Type {
        BYTE(1),
        WORD(2),
        DWORD(4),
        POINTER(2);

        int size;
        Type(int size) {
            this.size = size;
        }
    }

    Variable(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    int getSize() {
        return type.size;
    }

    @Override
    public String toString() {
        return "var " + name + "(" + type + ")";
    }
}
