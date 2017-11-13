package ru.trolsoft.asmext;

public class Alias {
    public final String name;
    public final String register;

    public Alias(String name, String register) {
        this.name = name;
        this.register = register;
    }

    @Override
    public String toString() {
        return "alias (" + name + " -> " + register +")";
    }
}
