package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionTest {

    private static List<String> l(String ...args) {
        List result = new ArrayList();
        Collections.addAll(result, args);
        return result;
    }

    @Test
    void testConstructor() {
        Expression e;

        e = new Expression(l("r22", ".", "r21", "=", "r20", ".", "r19"));
        assertEquals(3, e.size());
        assertEquals("r22.r21", e.get(0).toString());
        assertEquals("=", e.get(1).toString());
        assertEquals("r20.r19", e.get(2).toString());
        assertTrue(e.get(0).getType() == Token.TYPE_REGISTER_GROUP);
        assertTrue(e.get(1).getType() == Token.TYPE_OPERATOR);
        assertTrue(e.get(2).getType() == Token.TYPE_REGISTER_GROUP);

        e = new Expression(l("r1", "+=", "r21"));
        assertEquals(3, e.size());
        assertEquals("r1", e.get(0).toString());
        assertEquals("+=", e.get(1).toString());
        assertEquals("r21", e.get(2).toString());
        assertTrue(e.get(0).getType() == Token.TYPE_REGISTER);
        assertTrue(e.get(1).getType() == Token.TYPE_OPERATOR);
        assertTrue(e.get(2).getType() == Token.TYPE_REGISTER);

    }
}
