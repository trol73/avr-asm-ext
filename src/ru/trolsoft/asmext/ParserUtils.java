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

    static boolean isComment(String token) {
        return token.startsWith(";") || token.startsWith("//");
    }

    static int parseValue(String s) {
        int sign;
        if (s.charAt(0) == '-') {
            s = s.substring(1);
            sign = -1;
        } else {
            sign = 1;
        }
        if (s.startsWith("0") && s.length() > 2) {
            char c2 = s.charAt(1);
            if (c2 == 'x' || c2 == 'X') {
                return sign*Integer.parseInt(s.substring(2), 16);
            } else if (c2 == 'b' || c2 == 'B') {
                return sign*Integer.parseInt(s.substring(2), 2);
            }
        }
        return Integer.parseInt(s);
    }

    static boolean isNumber(String str) {
        try {
            parseValue(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static int getTypeSize(String type) {
        switch (type) {
            case "byte":
                return 1;
            case "word":
                return 2;
            case "dword":
                return 4;
            default:
                return -1;
        }
    }

}
