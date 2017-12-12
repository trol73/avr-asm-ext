package ru.trolsoft.asmext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.data.Variable;

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

    private void test(ExpressionsCompiler ec, String[] tokens, String outVal) throws CompileException {
        StringBuilder out = new StringBuilder();
        ec.compile(tokens, out);
        assertEquals(out.toString().trim(), outVal);
    }

    @Test
    void testAssign() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(new Parser());

        test(ec, ta("r1", "  ", "=", "12"), "ldi\tr1, 12");
        test(ec, ta("r1", "  ", "=", "12"), "ldi\tr1, 12");
        test(ec, ta("r10", "  ", "=", "r0"), "mov\tr10, r0");
        test(ec, ta("r20", "=", "0"), "clr\tr20");
        test(ec, ta("r20", "=", "-", "1"), "ldi\tr20, -1");
        test(ec, ta("r20", "=", "-", "1", "+", "1"), "ldi\tr20, -1+1");
        test(ec, ta("r5", "=", "-", "r4"), "mov\tr5, r4\nneg\tr5");
        test(ec, ta("r5", "=", "-", "r4", "+", "10"), "mov\tr5, r4\nneg\tr5\nsubi\tr5, -10");
        test(ec, ta("r5", "=", "-", "r4", "+", "10", "-", "1"), "mov\tr5, r4\nneg\tr5\nsubi\tr5, -(10-1)");
        test(ec, ta("r5", "=", "0b00000001"), "ldi\tr5, 0b00000001");
        test(ec, ta("r5", "=", "0xAB"), "ldi\tr5, 0xAB");
    }

    @Test
    void testIncDec() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(new Parser());

        test(ec, ta("r1", "++"), "inc\tr1");
        test(ec, ta("r10", "\t", "--"), "dec\tr10");
        test(ec, ta("Z", "\t", "++"), "adiw\tZL, 1");
        test(ec, ta("X", "\t", "--"), "sbiw\tXL, 1");
        test(ec, ta("Y", "\t", "-=", "2"), "sbiw\tYL, 2");
        test(ec, ta("Z", "\t", "+=", "10"), "adiw\tZL, 10");
        test(ec, ta("Z", "\t", "+=", "1", "+", "1"), "adiw\tZL, (1+1)");
        test(ec, ta("Z", "\t", "+=", "(", "1", "+", "1", ")"), "adiw\tZL, (1+1)");
        test(ec, ta("X", "\t", "+=", "2", "+", "1"), "adiw\tXL, (2+1)");
        test(ec, ta("Z", "\t", "+=", "r21", ".", "r24"), "add\tZL, r24\nadc\tZH, r21");
        test(ec, ta("Y", "\t", "-=", "r21", ".", "r24"), "sub\tYL, r24\nsbc\tYH, r21");
    }


    @Test
    void testAdd() throws CompileException {
        ExpressionsCompiler ec = new ExpressionsCompiler(new Parser());

        test(ec, ta("r1", "+=", " ", "10"), "subi\tr1, -10");
        test(ec, ta("r7", "-=", " ", "50"), "subi\tr7, 50");
        test(ec, ta("r20", "+", "=", " ", "r24"), "add\tr20, r24");

        test(ec, ta("r1", "-=", " ", "10", "+", "1"), "subi\tr1, (10+1)");
        test(ec, ta("r1", "+=", " ", "10", "+", " ", "1"), "subi\tr1, -(10+1)");

        test(ec, ta("r24", "=", "r24", "+", "r2", "+", "r1"), "add\tr24, r2\nadd\tr24, r1");
        test(ec, ta("r24", "+=", "r2", "+", "r1"), "add\tr24, r2\nadd\tr24, r1");
        test(ec, ta("r24", "+", "=", "r2", "+", "r1"), "add\tr24, r2\nadd\tr24, r1");
    }


    @Test
    void testVars() throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.variables.put("var_b", new Variable("var_b", Variable.Type.BYTE));
        parser.variables.put("var_w", new Variable("var_w", Variable.Type.WORD));
        parser.variables.put("var_prgptr", new Variable("var_prgptr", Variable.Type.PRGPTR));
        parser.variables.put("var_ptr", new Variable("var_ptr", Variable.Type.POINTER));

        test(ec, ta("var_b", "=", "r0"), "sts\tvar_b, r0");
        test(ec, ta("var_w", "=", "r0", ".", "r1"), "sts\tvar_w+1, r0\nsts\tvar_w, r1");
        test(ec, ta("r0", "=", "var_b"), "lds\tr0, var_b");
        test(ec, ta("r0", ".", "r1", "=", "var_w"), "lds\tr0, var_w+1\nlds\tr1, var_w");

        parser.gcc = true;
        test(ec, ta("r21", ".", "r20", "+=", "var_prgptr"), "subi\tr20, lo8(-(var_prgptr))\nsbci\tr21, hi8(-(var_prgptr))");
        test(ec, ta("r21", ".", "r20", "-=", "var_prgptr"), "subi\tr20, lo8(var_prgptr)\nsbci\tr21, hi8(var_prgptr)");
        test(ec, ta("r21", ".", "r20", "+=", "var_ptr"), "subi\tr20, lo8(-(var_ptr))\nsbci\tr21, hi8(-(var_ptr))");
        test(ec, ta("r21", ".", "r20", "-=", "var_ptr"), "subi\tr20, lo8(var_ptr)\nsbci\tr21, hi8(var_ptr)");

        parser.gcc = false;
        test(ec, ta("r21", ".", "r20", "+=", "var_prgptr"), "subi\tr20, -LOW(2*var_prgptr)\nsbci\tr21, -HIGH(2*var_prgptr)");
        test(ec, ta("r21", ".", "r20", "-=", "var_prgptr"), "subi\tr20, LOW(2*var_prgptr)\nsbci\tr21, HIGH(2*var_prgptr)");
        test(ec, ta("r21", ".", "r20", "+=", "var_ptr"), "subi\tr20, -LOW(var_ptr)\nsbci\tr21, -HIGH(var_ptr)");
        test(ec, ta("r21", ".", "r20", "-=", "var_ptr"), "subi\tr20, LOW(var_ptr)\nsbci\tr21, HIGH(var_ptr)");
        test(ec, ta("r2", ".", "r1", "-=", "Z"), "sub\tr1, ZL\nsbc\tr2, ZH");
    }

    @Test
    void testPairs() throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        test(ec, ta("r25", ".", "r24", "+=", "10"), "adiw\tr24, 10");
        test(ec, ta("r25", ".", "r24", "-=", "10"), "sbiw\tr24, 10");

        test(ec, ta("r1", ".", "r0", "=", "Y"), "movw\tr0, YL");
        test(ec, ta("r10", ".", "r0", "=", "Z"), "mov\tr0, ZL\nmov\tr10, ZH");
        test(ec, ta("r25", ".", "r24", "=", "5000"), "ldi\tr25, 0x13\nldi\tr24, 0x88");

        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "-=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "sub\tr1, r0\nsbc\tr2, ZL\nsbc\tr3, ZH\nsbc\tr4, r8");
        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "+=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "add\tr1, r0\nadc\tr2, ZL\nadc\tr3, ZH\nadc\tr4, r8");
        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "mov\tr1, r0\nmov\tr2, ZL\nmov\tr3, ZH\nmov\tr4, r8");

        boolean error;
        try {
            ec.compile(ta("r23", ".", "r22", "10"), new StringBuilder());
            error = false;
        } catch (CompileException e) {
            error = true;
        }
        assertTrue(error);
        try {
            ec.compile(ta("r23", ".", "r22", "unknown"), new StringBuilder());
            error = false;
        } catch (CompileException e) {
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testErrors() {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);
        StringBuilder out = new StringBuilder();
        boolean error;

        try {
            ec.compile(ta("r0", "=", "undefined"), out);
            error = false;
        } catch (CompileException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r0.r1 = var_not_found");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r0 = -unknown_value");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r1 = r10 + unknown");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r1 = r10 - unknown");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r1 -= unknown");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r10 += unknown");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        parser = new Parser();
        try {
            parser.parseLine("unknown = r1");
        } catch (SyntaxException ignore) {}
        assertTrue(parser.getOutput().size() == 1);
        assertEquals(parser.getOutput().get(0), "unknown = r1");

        try {
            parser.parseLine("Z += unknown");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine("r2.r1 -= ZH");
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);
    }


    @Test
    void testXYZ() throws CompileException, SyntaxException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.parseLine(".extern pv : ptr");
        parser.parseLine(".extern ppv : prgptr");


        test(ec, ta("Z", "+=", "12"), "adiw\tZL, 12");

        test(ec, ta("Z", "+=", "Z"), "add\tZL, ZL\nadc\tZH, ZH");
        test(ec, ta("X", "-=", "Z"), "sub\tXL, ZL\nsbc\tXH, ZH");

        parser.gcc = false;
        test(ec, ta("Y", "=", "0x1234"), "ldi\tYL, LOW(0x1234)\nldi\tYH, HIGH(0x1234)");
        test(ec, ta("Y", "=", "pv"), "ldi\tYL, LOW(pv)\nldi\tYH, HIGH(pv)");
        test(ec, ta("Z", "=", "ppv"), "ldi\tZL, LOW(2*ppv)\nldi\tZH, HIGH(2*ppv)");

        parser.gcc = true;
        test(ec, ta("Y", "=", "0x1234"), "ldi\tYL, (0x1234 & 0xFF)\nldi\tYH, (0x1234 >> 8)");
        test(ec, ta("Y", "=", "pv"), "ldi\tYL, lo8(pv)\nldi\tYH, hi8(pv)");
        test(ec, ta("Z", "=", "ppv"), "ldi\tZL, lo8(ppv)\nldi\tZH, hi8(ppv)");

        test(ec, ta("Y", "=", "r1", ".", "r0"), "movw\tYL, r0");
        test(ec, ta("Y", "=", "r10", ".", "r1"), "mov\tYL, r1\nmov\tYH, r10");
        test(ec, ta("Z", "=", "r10", ".", "r11"), "mov\tZL, r11\nmov\tZH, r10");

        parser.gcc = true;
        test(ec, ta("Z", "+=", "pv"), "subi\tZL, lo8(-(pv))\n" +
                "sbci\tZH, hi8(-(pv))");
        test(ec, ta("Z", "-=", "pv"), "subi\tZL, lo8(pv)\n" +
                "sbci\tZH, hi8(pv)");
        parser.gcc = false;
        test(ec, ta("Z", "+=", "pv"), "subi\tZL, -LOW(pv)\n" +
                "sbci\tZH, -HIGH(pv)");
        test(ec, ta("Z", "-=", "pv"), "subi\tZL, LOW(pv)\n" +
                "sbci\tZH, HIGH(pv)");

        parser.gcc = true;
        test(ec, ta("Z", "+=", "ppv"), "subi\tZL, lo8(-(ppv))\n" +
                "sbci\tZH, hi8(-(ppv))");
        test(ec, ta("Z", "-=", "ppv"), "subi\tZL, lo8(ppv)\n" +
                "sbci\tZH, hi8(ppv)");
        parser.gcc = false;
        test(ec, ta("Z", "+=", "ppv"), "subi\tZL, -LOW(2*ppv)\n" +
                "sbci\tZH, -HIGH(2*ppv)");
        test(ec, ta("Z", "-=", "ppv"), "subi\tZL, LOW(2*ppv)\n" +
                "sbci\tZH, HIGH(2*ppv)");

    }


}