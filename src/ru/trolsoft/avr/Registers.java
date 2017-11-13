package ru.trolsoft.avr;

import java.util.HashSet;
import java.util.Set;

public class Registers {

    private static final Set<String> REGISTERS;

    static {
        REGISTERS = new HashSet<>();
        for (int i = 0; i <= 31; i++) {
            REGISTERS.add("r" + i);
        }
        REGISTERS.add("xl");
        REGISTERS.add("xh");
        REGISTERS.add("yl");
        REGISTERS.add("yh");
        REGISTERS.add("zl");
        REGISTERS.add("zh");
    }

    public static boolean isRegister(String name) {
        return name != null && REGISTERS.contains(name.toLowerCase());
    }
}
