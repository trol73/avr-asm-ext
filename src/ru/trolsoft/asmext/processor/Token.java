package ru.trolsoft.asmext.processor;


public class Token {
    /**
     * Some string (type undefined)
     */
    public static final int TYPE_OTHER = 0;
    /**
     * Decimal, hex or binary number
     */
    public static final int TYPE_NUMBER = 1;
    /**
     * Register: r0-r31
     */
    public static final int TYPE_REGISTER = 2;
    /**
     * Standard registers pair: X, Y or Z
     */
    public static final int TYPE_PAIR = 3;
    /**
     * Group of registers with any size
     */
    public static final int TYPE_REGISTER_GROUP = 4;
    /**
     * Operator
     */
    public static final int TYPE_OPERATOR = 5;
    /**
     * Variable or label name
     */
    public static final int TYPE_VARIABLE = 6;
    /**
     * Recognized constant name
     */
    public static final int TYPE_CONST = 7;
    /**
     * Some const expression (perhaps with undefined value)
     */
    public static final int TYPE_CONST_EXPRESSION = 8;
    /**
     * $prg / $io / $mem array
     */
    public static final int TYPE_SYS_ARRAY = 9;
    /**
     * Keyword, etc. "if" or "goto"
     */
    public static final int TYPE_KEYWORD = 10;


    private final int type;
    private final String[] strings;

    public Token(int type, String s) {
        this.type = type;
        this.strings = new String[] {s};
    }

    public Token(int type, String[] strings) {
        this.type = type;
        this.strings = strings;
    }

    public int getType() {
        return this.type;
    }


    public String asString() {
        return strings[0];
    }

    public int asInt() {
        if (type != TYPE_NUMBER) {
            throw new RuntimeException();
        }
        return ParserUtils.parseValue(strings[0]);
    }

    @Override
    public String toString() {
        StringBuilder sb;
        switch (type) {
            case TYPE_REGISTER_GROUP:
                sb = new StringBuilder();
                for (String s : strings) {
                    sb.append(s).append('.');
                }
                sb.delete(sb.length()-1, sb.length());
                return sb.toString();
        }
        return strings[0];
    }

    public boolean isRegister() {
        return type == TYPE_REGISTER;
    }

    public boolean isOperator() {
        return type == TYPE_OPERATOR;
    }

    public boolean isVar() {
        return type == TYPE_VARIABLE;
    }

    public boolean isRegGroup() {
        return type == TYPE_REGISTER_GROUP;
    }

    public boolean isPair() {
        return type == TYPE_PAIR;
    }

    public boolean isNumber() {
        return type == TYPE_NUMBER;
    }

    public boolean isSomeString() {
        return type == TYPE_OTHER;
    }

    public boolean isConst() {
        return type == TYPE_CONST;
    }

    public boolean isAnyConst() {
        return type == TYPE_NUMBER || type == TYPE_CONST || type == TYPE_CONST_EXPRESSION;
    }

    public boolean isOperator(String operator) {
        return type == TYPE_OPERATOR && operator.equals(strings[0]);
    }

    public boolean isRegGroup(int size) {
        return (type == TYPE_REGISTER_GROUP && strings.length == size) || (type == TYPE_PAIR && size == 2);
    }

    public boolean isOperator(String ...operators) {
        if (type != TYPE_OPERATOR) {
            return false;
        }
        for (String s : operators) {
            if (s.equals(strings[0])) {
                return true;
            }
        }
        return false;
    }

    public boolean isSomeString(String... strs) {
        if (type != TYPE_OTHER) {
            return false;
        }
        for (String s : strs) {
            if (s.equals(strings[0])) {
                return true;
            }
        }
        return false;
    }

    public boolean isKeyword(String... strs) {
        if (type != TYPE_KEYWORD) {
            return false;
        }
        for (String s : strs) {
            if (s.equals(strings[0])) {
                return true;
            }
        }
        return false;
    }

    public Token getPairHigh() {
        if (type == TYPE_PAIR) {
            return new Token(TYPE_REGISTER, strings[0] + "H");
        } else if (isRegisterWord()) {
            return new Token(TYPE_REGISTER, strings[0]);
        } else if (type == TYPE_REGISTER_GROUP && strings.length == 2) {
            return new Token(TYPE_REGISTER, strings[0]);
        }
        throw new RuntimeException("not a pair (" + type + ")");

    }

    public Token getPairLow() {
        if (type == TYPE_PAIR) {
            return new Token(TYPE_REGISTER, strings[0] + "L");
        } else if (isRegisterWord()) {
            return new Token(TYPE_REGISTER, strings[1]);
        } else if (type == TYPE_REGISTER_GROUP && strings.length == 2) {
            return new Token(TYPE_REGISTER, strings[1]);
        }
        throw new RuntimeException("not a pair (" + type + ")");
    }

    public Token wrapToBrackets() {
        if (type == TYPE_NUMBER) {
            return this;
        }
        if (strings.length != 1) {
            throw new RuntimeException("wrong type");
        }
        if (strings[0].startsWith("(") && strings[0].endsWith(")")) {
            return this;
        }
        return new Token(type, "(" + strings[0] + ")");
    }

    public Token getNegativeExpr() {
        if (type == TYPE_NUMBER) {
            int val = getNumberValue();
            if (val > 0) {
                return new Token(type, '-' + strings[0]);
            } else if (val < 0) {
                return new Token(type, strings[0].substring(1));
            }
            return new Token(type, strings[0]);
        }
        if (type == TYPE_CONST_EXPRESSION) {
            Token brackets = wrapToBrackets();
            return new Token(brackets.type, '-' + brackets.strings[0]);
        }
        throw new RuntimeException("wrong type");
    }

    public int getNumberValue() {
        if (type != TYPE_NUMBER) {
            throw new RuntimeException("not a number");
        }
        return ParserUtils.parseValue(strings[0]);
    }

    public boolean isRegisterWord() {
        if (type != TYPE_REGISTER_GROUP || strings.length != 2) {
            return false;
        }
        String rh = strings[0].toLowerCase();
        String rl = strings[1].toLowerCase();
        if (rh.startsWith("r") && rl.startsWith("r")) {
            try {
                int nh = Integer.parseInt(rh.substring(1));
                int nl = Integer.parseInt(rl.substring(1));
                if (nl % 2 == 0 && nh == nl + 1) {
                    return true;
                }
            } catch (Exception ignore) {}
        }
        return false;
    }

    public int size() {
        if (type == TYPE_PAIR) {
            return 2;
        }
        return strings.length;
    }

    public Token getReg(int index) {
        if (type == TYPE_REGISTER_GROUP) {
            return new Token(TYPE_REGISTER, strings[index]);
        }
        if (type == TYPE_REGISTER && index == 0) {
            return this;
        }
        if (type == TYPE_PAIR) {
            switch (index) {
                case 0:
                    return new Token(TYPE_REGISTER, strings[0].toUpperCase() + "H");
                case 1:
                    return new Token(TYPE_REGISTER, strings[0].toUpperCase() + "L");
                default:
                    throw new RuntimeException("wrong pair index:" + index);
            }
        }
        throw new RuntimeException("internal error");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Token)) {
            return false;
        }
        Token t = (Token)obj;
        if (t.type != type) {
            return false;
        }
        switch (type) {
            case TYPE_REGISTER:
                return (strings.length == t.strings.length) && strings[0].equalsIgnoreCase(t.strings[0]);
            case TYPE_REGISTER_GROUP:
                if (strings.length != t.strings.length) {
                    return false;
                }
                for (int i = 0; i < strings.length; i++) {
                    if (!strings[i].equalsIgnoreCase(t.strings[i])) {
                        return false;
                    }
                }
                return true;
            default:
                if (strings.length != t.strings.length) {
                    return false;
                }
                for (int i = 0; i < strings.length; i++) {
                    if (!strings[i].equals(t.strings[i])) {
                        return false;
                    }
                }
                return true;
        }
    }

    // $mem / $io / $prg array
}
