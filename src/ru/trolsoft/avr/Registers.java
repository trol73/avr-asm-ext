package ru.trolsoft.avr;

import java.util.HashSet;
import java.util.Set;

public class Registers {

    private static final Set<String> REGISTERS;
    private static final Set<String> REGISTERS_EVEN;
    private static final Set<String> REGISTERS_HIGH;
    private static final Set<String> REGISTERS_PAIR_LOW;

    static {
        REGISTERS = new HashSet<>();
        REGISTERS_EVEN = new HashSet<>();
        REGISTERS_HIGH = new HashSet<>();
        for (int i = 0; i <= 31; i++) {
            String reg = "r" + i;
            REGISTERS.add(reg);
            if (i % 2 == 0) {
                REGISTERS_EVEN.add(reg);
            }
            if (i >= 16) {
                REGISTERS_HIGH.add("r" + i);
            }
        }
        REGISTERS.add("xl");
        REGISTERS.add("xh");
        REGISTERS.add("yl");
        REGISTERS.add("yh");
        REGISTERS.add("zl");
        REGISTERS.add("zh");

        REGISTERS_HIGH.add("xl");
        REGISTERS_HIGH.add("xh");
        REGISTERS_HIGH.add("yl");
        REGISTERS_HIGH.add("yh");
        REGISTERS_HIGH.add("zl");
        REGISTERS_HIGH.add("zh");

        REGISTERS_PAIR_LOW = new HashSet<>();
        REGISTERS_PAIR_LOW.add("r24");
        REGISTERS_PAIR_LOW.add("r26");
        REGISTERS_PAIR_LOW.add("r28");
        REGISTERS_PAIR_LOW.add("r30");
        REGISTERS_PAIR_LOW.add("xl");
        REGISTERS_PAIR_LOW.add("yl");
        REGISTERS_PAIR_LOW.add("zl");

        REGISTERS_EVEN.add("xl");
        REGISTERS_EVEN.add("yl");
        REGISTERS_EVEN.add("zl");
    }

    public static boolean isRegister(String name) {
        return name != null && REGISTERS.contains(name.toLowerCase());
    }

    public static boolean isHighRegister(String name) {
        return name != null && REGISTERS_HIGH.contains(name.toLowerCase());
    }

    public static boolean isPair(String name) {
        return "X".equals(name) || "Y".equals(name) || "Z".equals(name);
    }

    public static boolean isPairLow(String name) {
        return name != null && REGISTERS_PAIR_LOW.contains(name.toLowerCase());
    }

    public static boolean isEven(String name) {
        return name != null && REGISTERS_EVEN.contains(name.toLowerCase());
    }
}
