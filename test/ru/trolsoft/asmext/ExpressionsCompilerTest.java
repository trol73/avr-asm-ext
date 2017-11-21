package ru.trolsoft.asmext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ru.trolsoft.asmext.ExpressionsCompiler.compile;
import static ru.trolsoft.asmext.ExpressionsCompiler.CompileException;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionsCompilerTest {

    private static String[] ta(String ... tokens) {
        return tokens;
    }

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testAssign() throws CompileException {
        StringBuilder out;

        out = new StringBuilder();
        compile(ta("r1", "  ", "=", "12"), out);
        assertEquals(out.toString().trim(), "ldi\tr1, 12");

        out = new StringBuilder();
        compile(ta("r10", "  ", "=", "r0"), out);
        assertEquals(out.toString().trim(), "mov\tr10, r0");

        out = new StringBuilder();
        compile(ta("r20", "=", "0"), out);
        assertEquals(out.toString().trim(), "clr\tr20");

        out = new StringBuilder();
        compile(ta("r20", "=", "-", "1"), out);
        assertEquals(out.toString().trim(), "ldi\tr20, -1");

        out = new StringBuilder();
        compile(ta("r5", "=", "-", "r4"), out);
        assertEquals(out.toString().trim(), "mov\tr5, r4\nneg\tr5");

        out = new StringBuilder();
        compile(ta("r5", "=", "-", "r4", "+", "10"), out);
        assertEquals(out.toString().trim(), "mov\tr5, r4\nneg\tr5\nsubi\tr5, -10");
    }

    @Test
    void testIncDec() throws CompileException {
        StringBuilder out;

        out = new StringBuilder();
        compile(ta("r1", "++"), out);
        assertEquals(out.toString().trim(), "inc\tr1");

        out = new StringBuilder();
        compile(ta("r10", "\t", "--"), out);
        assertEquals(out.toString().trim(), "dec\tr10");
    }


    @Test
    void testAdd() throws CompileException {
        StringBuilder out;

        out = new StringBuilder();
        compile(ta("r1", "+=", " ", "10"), out);
        assertEquals(out.toString().trim(), "subi\tr1, -10");

        out = new StringBuilder();
        compile(ta("r7", "-=", " ", "50"), out);
        assertEquals(out.toString().trim(), "subi\tr7, 50");

        out = new StringBuilder();
        compile(ta("r20", "+", "=", " ", "r24"), out);
        assertEquals(out.toString().trim(), "add\tr20, r24");


    }

}