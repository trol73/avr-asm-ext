package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.utils.TokenString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionTest {

    private Expression e;

    private static List<String> l(String ...args) {
        List result = new ArrayList();
        Collections.addAll(result, args);
        return result;
    }

    @Test
    void testConstructor() {
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

    @Test
    void testLabels() {
        e = new Expression(new TokenString("lbl:"));
        assertEquals("lbl", e.getFirst().asString());
        assertTrue(e.getLast().isOperator(":"));

        e = new Expression(new TokenString("@lbl2:"));
        assertEquals("@lbl2", e.getFirst().asString());
        assertTrue(e.getLast().isOperator(":"));
    }

    @Test
    void testArrays() {
        e = new Expression(new TokenString("ram[X] = 1"));
        assertEquals(3, e.size());
        assertEquals(Token.TYPE_ARRAY_RAM, e.get(0).getType());
        assertTrue(e.get(1).isOperator("="));
        assertTrue(e.get(2).isNumber());
        assertEquals(1, e.get(2).getNumberValue());
        assertTrue(e.get(0).isArray());

        e = new Expression(new TokenString("ram[X++] = 10"));
        assertEquals(3, e.size());
        assertEquals(Token.TYPE_ARRAY_RAM, e.get(0).getType());
        assertTrue(e.get(1).isOperator("="));
        assertEquals(10, e.get(2).getNumberValue());
        assertTrue(e.get(0).isArray());

        e = new Expression(new TokenString("io[PINC] |= 4"));
        assertEquals(3, e.size());
        assertEquals(Token.TYPE_ARRAY_IO, e.get(0).getType());
        assertTrue(e.get(1).isOperator("|="));
        assertEquals(4, e.get(2).getNumberValue());
        assertTrue(e.get(0).isArray());

        e = new Expression(new TokenString("r0 = prg[--Y]"));
        assertEquals(3, e.size());
        assertTrue(e.get(0).isRegister("r0"));
        assertTrue(e.get(1).isOperator("="));
        assertEquals(Token.TYPE_ARRAY_PRG, e.get(2).getType());
        assertTrue(e.get(2).isArray());
        assertTrue(!e.get(0).isArray());
    }

    @Test
    void testArraysErrors() {
        e = new Expression(new TokenString("ram[X = 1"));
        assertEquals(5, e.size());
        e = new Expression(new TokenString("ram X] = 1"));
        assertEquals(5, e.size());
        e = new Expression(new TokenString("ram ]X[ = 1"));
        assertEquals(6, e.size());

        e = new Expression(new TokenString("ram[X+] = 1"));
        assertEquals(7, e.size());
        e = new Expression(new TokenString("ram[+X] = 1"));
        assertEquals(7, e.size());
    }

    @Test
    void testArrayIndex() {
        Token.ArrayIndex ind;
        e = new Expression(new TokenString("ram[X]"));
        ind = e.getFirst().getIndex();
        assertTrue(!ind.hasModifier());
        assertTrue(ind.isPair());

        e = new Expression(new TokenString("io[Y++]"));
        ind = e.getFirst().getIndex();
        assertTrue(ind.hasModifier());
        assertTrue(ind.isPair());
        assertTrue(ind.isPostInc());

        e = new Expression(new TokenString("io[++Y]"));
        ind = e.getFirst().getIndex();
        assertTrue(ind.hasModifier());
        assertTrue(ind.isPair());
        assertTrue(ind.isPreInc());

        e = new Expression(new TokenString("prg[Z--]"));
        ind = e.getFirst().getIndex();
        assertTrue(ind.hasModifier());
        assertTrue(ind.isPair());
        assertTrue(ind.isPostDec());

        e = new Expression(new TokenString("prg[--Z]"));
        ind = e.getFirst().getIndex();
        assertTrue(ind.hasModifier());
        assertTrue(ind.isPair());
        assertTrue(ind.isPreDec());

        e = new Expression(new TokenString("prg[PINA]"));
        ind = e.getFirst().getIndex();
        assertTrue(!ind.hasModifier());
        assertTrue(!ind.isPair());
        assertEquals("PINA", ind.getName());
    }

    @Test
    void testGroups() {
        Expression e;
        e = new Expression(new TokenString(".args val(ZH.ZL.rmp)"));

        assertEquals(8, e.size());
        assertTrue(e.getFirst().isOperator("."));
        assertEquals("args", e.get(1).asString());
        assertEquals("val", e.get(2).asString());
        assertTrue(e.get(3).isOperator("("));
        assertEquals(Token.TYPE_REGISTER_GROUP, e.get(4).getType());
        assertEquals(2, e.get(4).size());
        assertEquals("ZH.ZL", e.get(4).toString());
        assertTrue(e.get(5).isOperator("."));
        assertEquals("rmp", e.get(6).asString());
        assertTrue(e.get(7).isOperator(")"));
    }
}
