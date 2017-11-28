package ru.trolsoft.asmext;

public class Alias {
    public final String name;
    final String register;

    Alias(String name, String register) {
        this.name = name;
        this.register = register;
    }

    @Override
    public String toString() {
        return "alias (" + name + " -> " + register +")";
    }
}
