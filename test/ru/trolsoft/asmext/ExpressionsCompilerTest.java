package ru.trolsoft.asmext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    void test(ExpressionsCompiler ec, String[] tokens, String outVal) throws CompileException {
        StringBuilder out = new StringBuilder();
        ec.compile(tokens, out);
        assertEquals(out.toString().trim(), outVal);
    }

    @Test
    void testAssign() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(null);

        test(ec, ta("r1", "  ", "=", "12"), "ldi\tr1, 12");
        test(ec, ta("r1", "  ", "=", "12"), "ldi\tr1, 12");
        test(ec, ta("r10", "  ", "=", "r0"), "mov\tr10, r0");
        test(ec, ta("r20", "=", "0"), "clr\tr20");
        test(ec, ta("r20", "=", "-", "1"), "ldi\tr20, -1");
        test(ec, ta("r20", "=", "-", "1", "+", "1"), "ldi\tr20, -(1+1)");
        test(ec, ta("r5", "=", "-", "r4"), "mov\tr5, r4\nneg\tr5");
        test(ec, ta("r5", "=", "-", "r4", "+", "10"), "mov\tr5, r4\nneg\tr5\nsubi\tr5, -10");
        test(ec, ta("r5", "=", "-", "r4", "+", "10", "-", "1"), "mov\tr5, r4\nneg\tr5\nsubi\tr5, -(10-1)");
    }

    @Test
    void testIncDec() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(null);

        test(ec, ta("r1", "++"), "inc\tr1");
        test(ec, ta("r10", "\t", "--"), "dec\tr10");
        test(ec, ta("Z", "\t", "++"), "adiw\tZL, 1");
        test(ec, ta("X", "\t", "--"), "sbiw\tXL, 1");
        test(ec, ta("Y", "\t", "-=", "2"), "sbiw\tYL, 2");
        test(ec, ta("Z", "\t", "+=", "10"), "adiw\tZL, 10");
        test(ec, ta("Z", "\t", "+=", "1", "+", "1"), "adiw\tZL, (1+1)");
    }


    @Test
    void testAdd() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(null);

        test(ec, ta("r1", "+=", " ", "10"), "subi\tr1, -10");
        test(ec, ta("r7", "-=", " ", "50"), "subi\tr7, 50");
        test(ec, ta("r20", "+", "=", " ", "r24"), "add\tr20, r24");

        test(ec, ta("r1", "-=", " ", "10", "+", "1"), "subi\tr1, (10+1)");
        test(ec, ta("r1", "+=", " ", "10", "+", " ", "1"), "subi\tr1, -(10+1)");

        test(ec, ta("r24", "=", "r24", "+", "r2", "+", "r1"), "add\tr24, r2\nadd\tr24, r1");
        test(ec, ta("r24", "+=", "r2", "+", "r1"), "add\tr24, r2\nadd\tr24, r1");
        //width = width + r24 + delta	; TODO +=
    }


    @Test
    void testVars() throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.variables.put("var_b", new Variable("var_b", 1));
        parser.variables.put("var_w", new Variable("var_w", 2));

        test(ec, ta("var_b", "=", "r0"), "sts\tvar_b, r0");
        test(ec, ta("var_w", "=", "r0", ".", "r1"), "sts\tvar_w+1, r0\nsts\tvar_w, r1");
        test(ec, ta("r0", "=", "var_b"), "lds\tr0, var_b");
        test(ec, ta("r0", ".", "r1", "=", "var_w"), "lds\tr0, var_w+1\nlds\tr1, var_w");
    }

}