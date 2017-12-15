package ru.trolsoft.asmext.utils;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TokenStringTest {

    private static List<String> a(String ... strs) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, strs);
        return result;
    }

    private static List<String> as(String str) {
        return new TokenString(str).getTokens();
    }

    @Test
    void tokensTest() {
        assertEquals(as("x=1"), a("x", "=", "1"));
        assertEquals(as("x =\t1"), a("x", " ", "=", "\t", "1"));
        assertEquals(as("x=123"), a("x", "=", "123"));
        assertEquals(as("x=0x123"), a("x", "=", "0x123"));
        assertEquals(as("x=0b101"), a("x", "=", "0b101"));
        assertEquals(as("x=0xABC"), a("x", "=", "0xABC"));
        assertEquals(as("x='1'"), a("x", "=", "'1'"));
        assertEquals(as("x=' '"), a("x", "=", "' '"));
        assertEquals(as("x='\t'"), a("x", "=", "'\t'"));
        assertEquals(as("x='='"), a("x", "=", "'='"));
        assertEquals(as("x=';';comment"), a("x", "=", "';'"));
        assertEquals(as(".proc abc"), a(".", "proc", " ", "abc"));
        assertEquals(as("label: ;cmt"), a("label", ":", " "));
        assertEquals(as("x=';';comment ; comment // comment"), a("x", "=", "';'"));
        assertEquals(as(" \t .proc abc"), a(".", "proc", " ", "abc"));

        assertEquals(as("x <= 10"), a("x", " ", "<=", " ", "10"));
        assertEquals(as("x<=10"), a("x", "<=", "10"));
        assertEquals(as("x>=1"), a("x", ">=", "1"));
        assertEquals(as("x==100"), a("x", "==", "100"));
        assertEquals(as("x == 100"), a("x", " ", "==", " ", "100"));
        assertEquals(as("x!=100"), a("x", "!=", "100"));
        assertEquals(as("x>>1"), a("x", ">>", "1"));

        assertEquals(as("x+=100"), a("x", "+=", "100"));
        assertEquals(as("x -= 100"), a("x", " ", "-=", " ", "100"));
    }

    @Test
    void otherTests() {
        TokenString ts;

        ts = new TokenString(" x = y; comment");
        assertEquals(ts.pure(), "x = y");
        assertEquals(ts.getIndent(), " ");
        assertEquals(ts.size(),5);
        assertEquals(ts.getIndent(), " ");
        assertEquals(ts.getComment(), "; comment");
        assertTrue(!ts.isEmpty());
        assertEquals(ts.getFirstToken(), "x");
        ts = new TokenString("x = y");
        assertTrue(ts.startsWith("x"));
        assertTrue(ts.endsWith("y"));
        assertTrue(ts.contains("="));
        assertTrue(!ts.contains("!"));
        assertEquals(ts.toLowerCase(), ts.toString());
        assertEquals(ts.toUpperCase(), "X = Y");
        assertEquals(ts.toLowerCase(Locale.getDefault()), ts.toString());
        assertEquals(ts.toUpperCase(Locale.getDefault()), "X = Y");

        assertEquals(ts.getToken(0), "x");
        ts.modifyToken(0, "xx");
        assertEquals(ts.getToken(0), "xx");
        assertEquals(ts.getTokens().get(0), "xx");

        assertTrue(ts.size() == ts.getTokens().size());

        ts = new TokenString("\t x=';';comment ; comment // comment");
        assertEquals(ts.getComment(), ";comment ; comment // comment");
        assertEquals(ts.getIndent(), "\t ");
    }

    @Test
    void testProxyMethods() {
        String s = "x + y / 2";
        TokenString ts = new TokenString(s);

        Iterator<String> i1 = ts.getTokens().iterator();
        Iterator<String> i2 = ts.iterator();
        while (true) {
            assertTrue(i1.hasNext() == i2.hasNext());
            if (!i1.hasNext()) {
                break;
            }
            String n1 = i1.next();
            String n2 = i2.next();
            assertEquals(n1, n2);
        }

        //assertEquals(ts.spliterator(), ts.getTokens().spliterator());
        assertEquals(ts.length(), s.length());
        assertEquals(ts.size(), ts.getTokens().size());
        for (int i = 0; i < ts.length(); i++) {
            assertEquals(ts.charAt(i), s.charAt(i));
        }
        for (int i = 0; i < ts.length()-1; i++) {
            for (int j = i; j < ts.length()-1; j++) {
                assertEquals(ts.subSequence(i, j), s.subSequence(i, j));
            }
        }
        assertArrayEquals(ts.chars().toArray(), s.chars().toArray());
        assertArrayEquals(ts.codePoints().toArray(), s.codePoints().toArray());
        String s1 = "123";
        assertEquals(s.compareTo(s1), ts.compareTo(new TokenString(s1)));
        assertTrue(ts.toString().equals(s));
        assertTrue(s.equals(ts.toString()));
        assertTrue(s.hashCode() == ts.hashCode());
        assertTrue(s.length() == ts.length());

    }


    @Test
    void testConstruct() {
        String[] tokens = new String[] {"x", " ", "+", " ", "1", " ", "=", " ", "A"};
        TokenString ts1 = new TokenString("x + 1 = A");
        TokenString ts2 = new TokenString(tokens);
        assertEquals(ts1, ts2);
        assertEquals(ts1.size(), ts2.size());
        assertEquals(ts1.hashCode(), ts2.hashCode());
    }

}
