package ru.trolsoft.asmext;

public class NamedPair {
    public final String name;
    public final String value;

    NamedPair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return "(" + name + " = " + value + ")";
    }
}
