package ru.trolsoft.asmext.data;

import java.util.HashMap;
import java.util.Map;

public class Procedure {
    public final String name;
    public final Map<String, Alias> uses;
    public final Map<String, Alias> args;
    public final Map<String, Constant> consts;

    public Procedure(String name) {
        this.name = name;
        this.uses = new HashMap<>();
        this.args = new HashMap<>();
        this.consts = new HashMap<>();
    }

    public void addAlias(Alias alias) {
        uses.put(alias.name, alias);
    }

    public void addArg(Alias arg) {
        args.put(arg.name, arg);
    }

    public boolean hasAlias(String name) {
        return uses.containsKey(name);
    }

    public boolean hasArg(String name) {
        return args.containsKey(name);
    }

    public boolean hasConst(String name) {
        return consts.containsKey(name);
    }

    public Alias getAlias(String name) {
        return uses.get(name);
    }

    public Alias getArg(String name) {
        return args.get(name);
    }

    public Constant getConst(String name) {
        return consts.get(name);
    }


    public String resolveVariable(String name) {
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
