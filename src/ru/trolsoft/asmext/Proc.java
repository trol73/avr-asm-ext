package ru.trolsoft.asmext;

import java.util.Map;
import java.util.TreeMap;

public class Proc {
    public final String name;
    public final Map<String, Alias> uses;

    public Proc(String name) {
        this.name = name;
        this.uses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}
