package ru.trolsoft.asmext.processor;




import ru.trolsoft.asmext.utils.TokenString;

import java.util.*;
import java.util.function.Consumer;

public class Expression implements Iterable<Token> {

    private static final Set<String> OPERATORS;
    private static final Set<String> KEYWORDS;
    private static final Set<String> SREG_FLAGS;

    static {
        String[] operators = {
                "=", "==", "!=", ">=", "<=", "+", "-", "&", "|", ":", ",", "<<", ">>",
                "+=", "-=", "&=", "|=", "<<=", ">>=", "(", ")", "++", "--", ".", "!"
        };
        OPERATORS = new HashSet<>(Arrays.asList(operators));
        String[] keywords = {
                "if", "goto"
        };
        KEYWORDS = new HashSet<>(Arrays.asList(keywords));
        String[] flags = {
                "F_GLOBAL_INT", "F_BIT_COPY", "F_HALF_CARRY", "F_SIGN", "F_TCO", "F_NEG", "F_ZERO", "F_CARRY"
        };
        SREG_FLAGS = new HashSet<>(Arrays.asList(flags));
    }

    private final List<Token> list = new ArrayList<>();

    public Expression() {

    }

    Expression(TokenString src) {
        this(mergeTokens(src.getTokens()));
    }

    Expression(List<String> tokens) {
        int i = 0;
        final int n = tokens.size();
        while (i < n) {
            String s = tokens.get(i++);
            String next = i < n ? tokens.get(i) : null;
            if (ParserUtils.isRegister(s)) {

                if (".".equals(next)) {
                    i = tryToParseGroupAndReturnIndex(tokens, i);
                } else if ("[".equals(next)) {
                    i = tryToParseRegisterBitAndReturnIndex(tokens, i);
                } else { // group
                    Token reg = new Token(Token.TYPE_REGISTER, s);
                    list.add(reg);
                }
            } else if (OPERATORS.contains(s)) {         // register
                Token operator = new Token(Token.TYPE_OPERATOR, s);
                list.add(operator);
            } else if (KEYWORDS.contains(s)) {
                Token keyword = new Token(Token.TYPE_KEYWORD, s);
                list.add(keyword);
            } else if (SREG_FLAGS.contains(s)) {
                Token keyword = new Token(Token.TYPE_SREG_FLAG, s);
                list.add(keyword);
            } else if (ParserUtils.isPair(s)) {
                Token pair = new Token(Token.TYPE_PAIR, s);
                list.add(pair);
            } else if (ParserUtils.isNumber(s)) {
                Token number = new Token(Token.TYPE_NUMBER, s);
                list.add(number);
            } else if (ParserUtils.isConstExpression(s)) {
                Token constExp = new Token(Token.TYPE_CONST_EXPRESSION, s);
                list.add(constExp);
            } else if (getArrayTokenType(s) >= 0) {
                i = tryToParseArrayAndReturnIndex(s, tokens, i);
            } else {
                Token other = new Token(Token.TYPE_OTHER, s);
                list.add(other);
            }
            // TODO
//            public static final int TYPE_VARIABLE = 6;

        }
    }


    private int tryToParseGroupAndReturnIndex(List<String> tokens, int i) {
        final int n = tokens.size();
        int cnt = 2;
        i++;
        while (i < n) {
            String s = tokens.get(i);
            if (ParserUtils.isRegister(s)) {
                cnt++;
                i++;
            } else {
                break;
            }
            if (i >= n) {
                break;
            }
            s = tokens.get(i);
            if (".".equals(s)) {
                cnt++;
                i++;
            }
        }
        if (cnt % 2 != 0) {
            String[] regs = new String[cnt / 2 + 1];
            int index = 0;
            for (int j = i - cnt; j < i; j += 2) {
                regs[index++] = tokens.get(j);
            }
            Token group = new Token(Token.TYPE_REGISTER_GROUP, regs);
            list.add(group);
        } else if (cnt == 2) {
            Token t = new Token(Token.TYPE_REGISTER, tokens.get(i - cnt));
            list.add(t);
            t = new Token(Token.TYPE_OTHER, tokens.get(i - cnt + 1));
            list.add(t);
        } else {
            String[] regs = new String[cnt / 2];
            int index = 0;
            for (int j = i - cnt; j < i; j += 2) {
                if (index >= regs.length) {
                    break;
                }
                regs[index++] = tokens.get(j);
            }
            Token group = new Token(Token.TYPE_REGISTER_GROUP, regs);
            list.add(group);
            return i - 1;
        }
        return i;
    }

    private int tryToParseRegisterBitAndReturnIndex(List<String> tokens, int i) {
        String regName = tokens.get(i-1);
        if (i+2 < tokens.size() && "]".equals(tokens.get(i+2))) {
            String bitIndex = tokens.get(i+1);
            Token regBit = new Token(Token.TYPE_REGISTER_BIT, new String[] {regName, bitIndex});
            list.add(regBit);
            return i + 3;
        }
        Token reg = new Token(Token.TYPE_REGISTER, regName);
        list.add(reg);
        return i;
    }

    private int tryToParseArrayAndReturnIndex(String arrayName, List<String> tokens, int i) {
        String s = tokens.get(i);
        if (i < tokens.size() - 2 && "[".equals(s)) {
            s = tokens.get(i+1);
            if (isIncOrDec(s) && i < tokens.size() - 3) {
                String pair = tokens.get(i+2);
                if (ParserUtils.isPair(pair) && "]".equals(tokens.get(i+3))) {
                    String[] strings = new String[] {s, pair};
                    Token t = new Token(getArrayTokenType(arrayName), strings);
                    list.add(t);
                    return i + 4;
                }
            } else { //if (ParserUtils.isPair(s)) {
                if ("]".equals(tokens.get(i+2))) {
                    Token t = new Token(getArrayTokenType(arrayName), s);
                    list.add(t);
                    return i + 3;
                } else if (i < tokens.size() - 3 && isIncOrDec(tokens.get(i+2)) && "]".equals(tokens.get(i+3))) {
                    String postOp = tokens.get(i+2);
                    String[] strings = new String[] {s, postOp};
                    Token t = new Token(getArrayTokenType(arrayName), strings);
                    list.add(t);
                    return i + 4;
                }
            }
        }
        Token t = new Token(Token.TYPE_OTHER, arrayName);
        list.add(t);
        return i;
    }


    private int getArrayTokenType(String name) {
        if ("ram".equals(name)) {
            return Token.TYPE_ARRAY_RAM;
        } else if ("prg".equals(name)) {
            return Token.TYPE_ARRAY_PRG;
        } else if ("io".equals(name)) {
            return Token.TYPE_ARRAY_IO;
        }
        return -1;
    }

    private boolean isIncOrDec(String s) {
        return "++".equals(s) || "--".equals(s);
    }

    @Override
    public Iterator<Token> iterator() {
        return list.iterator();
    }

    @Override
    public void forEach(Consumer<? super Token> action) {
        list.forEach(action);
    }

    @Override
    public Spliterator<Token> spliterator() {
        return list.spliterator();
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public Token get(int index) {
        return list.get(index);
    }

    public Token set(int index, Token t) {
        return list.set(index, t);
    }

    public void add(int index, Token t) {
        list.add(index, t);
    }

    public void add(Token t) {
        list.add(t);
    }

    public void clear() {
        list.clear();
    }

    public Token remove(int index) {
        return list.remove(index);
    }

    public Token removeLast() {
        return list.remove(list.size()-1);
    }

    public void removeLast(int count) {
        for (int i = 0; i < count; i++) {
            removeLast();
        }
    }

    public Token removeFirst() {
        return list.remove(0);
    }

    public void removeFirst(int count) {
        for (int i = 0; i < count; i++) {
            removeFirst();
        }
    }

    static List<String> mergeTokens(List<String> tokens) {
        ParserUtils.mergeTokens(tokens);
        return tokens;
    }

    public int operatorsCount(String ...operators) {
        int result = 0;
        for (Token t : list) {
            if (t.isOperator(operators)) {
                result++;
            }
        }
        return result;
    }

    public Token getFirst() {
        return list.get(0);
    }

    public Token getLast() {
        return list.get(list.size() - 1);
    }

    public Token getLast(int index) {
        return list.get(list.size() - 1 - index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Token t : list) {
            sb.append(t);
        }
        return sb.toString();
    }
}
