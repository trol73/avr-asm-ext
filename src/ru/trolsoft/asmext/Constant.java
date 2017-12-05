package ru.trolsoft.asmext;

public class Constant {

    public enum Type {
        EQU, DEFINE;
    }
    public final String name;
    public final String value;
    public final Type type;

    public Constant(String name, String value, Type type) {
        this.name = name;
        this.value = value;
        this.type = type;
    }

    @Override
    public String toString() {
        return "var " + name + " = " + value + "(" + type + ")";
    }
}
