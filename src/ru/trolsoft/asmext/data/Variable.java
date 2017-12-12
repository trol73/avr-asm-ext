package ru.trolsoft.asmext.data;

public class Variable {
    public final String name;
    public final Type type;

    public enum Type {
        BYTE(1),
        WORD(2),
        DWORD(4),
        POINTER(2),
        PRGPTR(2);

        int size;
        Type(int size) {
            this.size = size;
        }
    }

    public Variable(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public int getSize() {
        return type.size;
    }

    public boolean isPointer() {
        return type == Type.POINTER || type == Type.PRGPTR;
    }

    @Override
    public String toString() {
        return "var " + name + "(" + type + ")";
    }
}
