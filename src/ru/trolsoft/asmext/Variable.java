package ru.trolsoft.asmext;

public class Variable {
    public final String name;
    final int size;

    Variable(String name, int size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public String toString() {
        return "var " + name + "(" + size + ")";
    }
}
