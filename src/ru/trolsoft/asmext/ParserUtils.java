package ru.trolsoft.asmext;

import ru.trolsoft.avr.Instructions;
import ru.trolsoft.avr.Registers;

public class ParserUtils {

    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        char firstChar = name.charAt(0);
        if (!isLetter(firstChar)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isLetter(c) && !isDigitChar(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigitChar(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    static boolean isRegister(String name) {
        return Registers.isRegister(name);
    }

    static boolean isInstruction(String name) {
        return Instructions.isInstruction(name);
    }


}
