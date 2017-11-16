package ru.trolsoft.asmext;

import java.util.Map;
import java.util.TreeMap;

public class Procedure {
    public final String name;
    public final Map<String, Alias> uses;
    public final Map<String, Alias> args;

    Procedure(String name) {
        this.name = name;
        this.uses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.args = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    void addAlias(Alias alias) {
        uses.put(alias.name, alias);
    }

    void addArg(Alias arg) {
        args.put(arg.name, arg);
    }

    boolean hasAlias(String name) {
        return uses.keySet().contains(name);
    }

    boolean hasArg(String name) {
        return args.keySet().contains(name);
    }

    @Override
    public String toString() {
        return "Procedure " + name + "(" + args + ") uses " + uses;
    }
}
