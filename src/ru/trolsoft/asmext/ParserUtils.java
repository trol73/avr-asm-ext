package ru.trolsoft.asmext;

import ru.trolsoft.avr.Instructions;
import ru.trolsoft.avr.Registers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    static boolean isInBrackets(String s) {
        return s != null && s.startsWith("(") && s.endsWith(")");
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

    static boolean isConstExpression(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        int bc = 0;
        char last = ' ';
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isDigitChar(c) && " \t+-*/()".indexOf(c) < 0) {
                return false;
            }
            if (c == '(') {
                bc++;
            } else if (c == ')') {
                bc--;
            }
            if (bc < 0) {
                return false;
            }
            if ("() \t".indexOf(c) < 0) {
                last = c;
            }
        }
        if ("+-*/".indexOf(last) >= 0) {
            return false;
        }
        return bc == 0;
    }

    static boolean isSimpleMatchOperator(String s) {
        if (s == null ) {
            return false;
        }
        s = s.trim();
        if (s.length() == 1 && "+-*/".contains(s)) {
            return true;
        }
        if ("<<".equals(s) || ">>".equals(s)) {
            return true;
        }
        return false;
    }

    static void removeEmptyTokens(List<String> tokens) {
        tokens.removeIf(s -> s == null || s.trim().isEmpty());
    }

    static void mergeTokens(List<String> tokens) {
        removeEmptyTokens(tokens);
        while (true) {
            boolean found = false;
            for (int i = 0; i < tokens.size(); i++) {
                String curr = tokens.get(i);
                String prev = i > 0 ? tokens.get(i - 1) : null;
                String next = i < tokens.size() - 1 ? tokens.get(i + 1) : null;

                if (isConstExpression(prev) && isConstExpression(next) && isSimpleMatchOperator(curr)) {
                    tokens.set(i, prev + curr + next);
                    tokens.set(i-1, null);
                    tokens.set(i+1, null);
                    found = true;
                } else if ("(".equals(prev) && ")".equals(next) && isConstExpression(curr)) {
                    tokens.set(i, prev + curr + next);
                    tokens.set(i-1, null);
                    tokens.set(i+1, null);
                    found = true;
                }
            }
            if (!found) {
                break;
            }
            removeEmptyTokens(tokens);
        }
    }

}
