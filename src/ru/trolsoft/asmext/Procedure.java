package ru.trolsoft.asmext;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Procedure {
    public final String name;
    final Map<String, Alias> uses;
    final Map<String, Alias> args;
    final Map<String, Constant> consts;

    Procedure(String name) {
        this.name = name;
        this.uses = new HashMap<>();
        this.args = new HashMap<>();
        this.consts = new HashMap<>();
    }

    void addAlias(Alias alias) {
        uses.put(alias.name, alias);
    }

    void addArg(Alias arg) {
        args.put(arg.name, arg);
    }

    boolean hasAlias(String name) {
        return uses.containsKey(name);
    }

    boolean hasArg(String name) {
        return args.containsKey(name);
    }

    boolean hasConst(String name) {
        return consts.containsKey(name);
    }

    Alias getAlias(String name) {
        return uses.get(name);
    }

    Alias getArg(String name) {
        return args.get(name);
    }

    Constant getConst(String name) {
        return consts.get(name);
    }


    String resolveVariable(String name) {
        Alias alias = uses.get(name);
        if (alias != null) {
            return alias.register;
        }
        alias = args.get(name);
        return alias != null ? alias.register : null;
    }

    @Override
    public String toString() {
        return "Procedure " + name + "(" + args + ") uses " + uses;
    }
}
