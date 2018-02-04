package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.data.Variable;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import static ru.trolsoft.asmext.processor.ExpressionsCompiler.CompileException;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionsCompilerTest {

    private static TokenString ta(String ... tokens) {
        return new TokenString(tokens);
    }

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    private void test(ExpressionsCompiler ec, TokenString tokens, String outVal) throws CompileException {
        OutputFile out = new OutputFile();
        ec.compile(tokens, ec.parser.buildExpression(tokens), out);
        assertEquals(outVal, out.toString().trim());
    }

//    private void test(ExpressionsCompiler ec, String s, String outVal) throws CompileException {
//        OutputFile out = new OutputFile();
//        TokenString tokenString = new TokenString(s);
//        ec.compile(tokenString, ec.parser.buildExpression(tokenString), out);
//        assertEquals(outVal, out.toString().trim());
//    }

    private void test(String s, String outVal) throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);
        OutputFile out = new OutputFile();

        TokenString tokenString = new TokenString(s);
        ec.compile(tokenString, ec.parser.buildExpression(tokenString), out);
        assertEquals(outVal, out.toString().trim());
    }

    private boolean compile(ExpressionsCompiler ec, TokenString src) throws CompileException {
        return ec.compile(src, new Expression(src), new OutputFile());
    }

    private boolean hasError(String s) {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);
        try {
            compile(ec, new TokenString(s));
            return false;
        } catch (CompileException e) {
            return true;
        }
    }

    private boolean hasError(ExpressionsCompiler ec, String s) {
        try {
            compile(ec, new TokenString(s));
            return false;
        } catch (CompileException e) {
            return true;
        }
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
        test(ec, ta("r15", "=", "1", "<<", "5"), "ldi\tr15, 1<<5");
        test(ec, ta("r15", "=", "(", "1", "<<", "5", ")", "|", "0x12"), "ldi\tr15, (1<<5)|0x12");

        test(ec, ta("r15", "=", "r20", "&", "0x0F"), "mov\tr15, r20\nandi\tr15, 0x0F");
        test(ec, ta("r15", "=", "r20", "|", "0x0F"), "mov\tr15, r20\nori\tr15, 0x0F");
        test(ec, ta("r15", "=", "r20", "&", "r0"), "mov\tr15, r20\nand\tr15, r0");
        test(ec, ta("r15", "=", "r20", "|", "r0"), "mov\tr15, r20\nor\tr15, r0");
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

        test(ec, ta("r1", "&=", "0x10"), "andi\tr1, 0x10");
        test(ec, ta("r1", "|=", "0x20"), "ori\tr1, 0x20");
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
        test(ec, ta("r2", ".", "r1", "-=", "ZH.ZL"), "sub\tr1, ZL\nsbc\tr2, ZH");
    }

    @Test
    void testPairs() throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        test(ec, ta("r25", ".", "r24", "+=", "10"), "adiw\tr24, 10");
        test(ec, ta("r25", ".", "r24", "-=", "10"), "sbiw\tr24, 10");

        test(ec, ta("r1", ".", "r0", "=", "Y"), "movw\tr0, YL");
        test(ec, ta("r10", ".", "r0", "=", "Z"), "mov\tr0, ZL\nmov\tr10, ZH");
        test(ec, ta("r25", ".", "r24", "=", "5000"), "ldi\tr24, 0x88\nldi\tr25, 0x13");

        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "-=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "sub\tr1, r0\nsbc\tr2, ZL\nsbc\tr3, ZH\nsbc\tr4, r8");
        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "+=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "add\tr1, r0\nadc\tr2, ZL\nadc\tr3, ZH\nadc\tr4, r8");
        test(ec, ta("r4", ".", "r3", ".", "r2", ".", "r1", "=", "r8", ".", "ZH", ".", "ZL", ".", "r0"),
                "mov\tr1, r0\nmov\tr2, ZL\nmov\tr3, ZH\nmov\tr4, r8");

        test(ec, ta("r17", ".", "r16", ".", "r15", "=", "0x123456"), "ldi\tr15, 0x56\nldi\tr16, 0x34\nldi\tr17, 0x12");

        assertFalse(compile(ec, ta("r23", ".", "r22", "10")));
        assertFalse(compile(ec, ta("r23", ".", "r22", "unknown")));
    }

    @Test
    void testPairMem() throws SyntaxException, CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.parseLine(".extern UART_RxHead : word");
        test(ec, ta("r31", ".", "r30", "=", "UART_RxHead"), "lds\tr31, UART_RxHead+1\nlds\tr30, UART_RxHead");
        test(ec, ta("Z", "=", "UART_RxHead"), "lds\tZH, UART_RxHead+1\nlds\tZL, UART_RxHead");
        //r31.r30 = UART_RxHead
        //Z = UART_RxHead
    }

    @Test
    void testErrors() {
        assertTrue(hasError("r0=undefined"));
        assertTrue(hasError("r0.r1 = var_not_found"));
        assertTrue(hasError("r0 = -unknown_value"));
        assertTrue(hasError("r1 = r10 + unknown"));
        assertTrue(hasError("r1 = r10 - unknown"));
        assertTrue(hasError("r1 -= unknown"));
        assertTrue(hasError("r10 += unknown"));
        assertTrue(hasError("unknown = r1"));
        assertTrue(hasError("Z += unknown"));
        assertTrue(hasError("r2.r1 -= ZH"));
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
        test(ec, ta("Y", "=", "0x1234"), "ldi\tYL, 0x34\nldi\tYH, 0x12");
        test(ec, ta("Y", "=", "pv"), "ldi\tYL, LOW(pv)\nldi\tYH, HIGH(pv)");
        test(ec, ta("Z", "=", "ppv"), "ldi\tZL, LOW(2*ppv)\nldi\tZH, HIGH(2*ppv)");
        test(ec, ta("Z", "=", "pv", "+", "2"), "ldi\tZL, LOW(pv+2)\nldi\tZH, HIGH(pv+2)");

        parser.gcc = true;
        test(ec, ta("Y", "=", "0x1234"), "ldi\tYL, 0x34\nldi\tYH, 0x12");
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


    @Test
    void testMultipleMove() throws SyntaxException, CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.parseLine(".extern var_b : byte");
        parser.parseLine(".extern var_w : word");

        test(ec, ta("var_b", "=", "r21", "=", "100"), "ldi\tr21, 100\nsts\tvar_b, r21");
        test(ec, ta("var_w", "=", "r21.r20", "=", "1000"), "ldi\tr20, 0xe8\nldi\tr21, 0x03\nsts\tvar_w+1, r21\nsts\tvar_w, r20");

        test(ec, ta("var_b", "=", "r16", "=", "var_b", "-", "1"), "lds\tr16, var_b\ndec\tr16\nsts\tvar_b, r16");

//        boolean error;
//        try {
//            test(ec, ta("=", "r21", "=", "100"), "ldi\tr21, 100\nsts\tvar_b, r21");
//            error = false;
//        } catch (CompileException e) {
//            error = true;
//            assertEquals("unexpected expression", e.getMessage());
//        }
//        assertTrue(error);
//
//        try {
//            test(ec, ta("var_b", "=", "r21", "="), "ldi\tr21, 100\nsts\tvar_b, r21");
//            error = false;
//        } catch (CompileException e) {
//            error = true;
//            assertEquals("empty expression", e.getMessage());
//        }
//        assertTrue(error);
    }

    @Test
    void testMultipleMoveError() throws SyntaxException {
        assertTrue(hasError("=r21=100"));
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        parser.parseLine(".extern var_b : byte");

        assertTrue(hasError(ec, "var_b=r21="));
    }


    @Test
    void testShifts() throws CompileException {
        Parser parser = new Parser();
        ExpressionsCompiler ec = new ExpressionsCompiler(parser);

        test(ec, ta("r1", "<<=", "1"), "lsl\tr1");
        test(ec, ta("r10", "<<=", "2"), "lsl\tr10\nlsl\tr10");
        test(ec, ta("r1", ">>=", "1"), "lsr\tr1");
        test(ec, ta("r10", ">>=", "2"), "lsr\tr10\nlsr\tr10");

        test(ec, ta("r1", ".", "r2", "<<=", "1"), "lsl\tr2\nrol\tr1");
        test(ec, ta("r1", ".", "r2", ">>=", "1"), "lsr\tr2\nror\tr1");

        test(ec, ta("r1", ".", "r2", "<<=", "2"), "lsl\tr2\nrol\tr1\nlsl\tr2\nrol\tr1");
        test(ec, ta("r1", ".", "r2", ">>=", "2"), "lsr\tr2\nror\tr1\nlsr\tr2\nror\tr1");
    }

    @Test
    void testArrayPortsRead() throws CompileException {
        test("r0 = io[PINC]", "in\tr0, PINC");
    }

    @Test
    void testArrayPortsWrite() throws CompileException {
        test("io[PORTC] = r0", "out\tPORTC, r0");
    }

    @Test
    void testArrayPortsErrors() {
        assertTrue(hasError("r0 = io[Z]"));
        assertTrue(hasError("r0 = io[PORTC++]"));
        assertTrue(hasError("r0 = io[--PORTC]"));
        assertTrue(hasError("1 = io[--PORTC]"));
        assertTrue(hasError("io[Z] = r0"));
        assertTrue(hasError("io[Z++] = r0"));
        assertTrue(hasError("io[--Z] = r0"));
        assertTrue(hasError("io[PORTC] = 1"));
    }

    @Test
    void testArrayMemRead() throws CompileException {
        test("r16 = ram[Z]", "ld\tr16, Z");
        test("r16 = ram[X]", "ld\tr16, X");
        test("r0 = ram[Z++]", "ld\tr0, Z+");
        test("r10 = ram[X++]", "ld\tr10, X+");
        test("r20 = ram[Y++]", "ld\tr20, Y+");
    }

    @Test
    void testArrayMemWrite() throws CompileException {
        test("ram[X] = r0", "st\tX, r0");
        test("ram[Y] = r0", "st\tY, r0");
        test("ram[Z] = r0", "st\tZ, r0");
        test("ram[X++] = r0", "st\tX+, r0");
        test("ram[Y++] = r0", "st\tY+, r0");
        test("ram[Z++] = r0", "st\tZ+, r0");
        test("ram[--X] = r0", "st\t-X, r0");
        test("ram[--Y] = r0", "st\t-Y, r0");
        test("ram[--Z] = r0", "st\t-Z, r0");
    }

    @Test
    void testArrayMemErrors() {
        assertTrue(hasError("r0 = ram[12]"));
        assertTrue(hasError("r10 = ram[++X]"));
        assertTrue(hasError("r20 = ram[++Y]"));
        assertTrue(hasError("r0 = ram[++Z]"));
        assertTrue(hasError("r10 = ram[Z--]"));
        assertTrue(hasError("r20 = ram[Y--]"));
        assertTrue(hasError("ram[++X] = r0"));
        assertTrue(hasError("ram[++Y] = r0"));
        assertTrue(hasError("ram[++Z] = r0"));
        assertTrue(hasError("ram[X--] = r0"));
        assertTrue(hasError("ram[Y--] = r0"));
        assertTrue(hasError("ram[Z--] = r0"));
        assertTrue(hasError("ram[Z] = 1"));
        assertTrue(hasError("ram[Z] = X"));
    }

    @Test
    void testArrayPrgRead() throws CompileException {
        test("r0 = prg[Z]", "lpm");
        test("r10 = prg[Z]", "lpm\tr10, Z");
        test("r20 = prg[Z++]", "lpm\tr20, Z+");
    }

    @Test
    void testArrayPrgErrors() {
        assertTrue(hasError("r0 = prg[1]"));
        assertTrue(hasError("r0 = prg[X]"));
        assertTrue(hasError("r0 = prg[Y]"));
        assertTrue(hasError("r0 = prg[X++]"));
        assertTrue(hasError("r0 = prg[Y++]"));
        assertTrue(hasError("r0 = prg[X--]"));
        assertTrue(hasError("r0 = prg[Y--]"));
        assertTrue(hasError("r0 = prg[--X]"));
        assertTrue(hasError("r0 = prg[--Y]"));
        assertTrue(hasError("r0 = prg[Z--]"));
        assertTrue(hasError("r0 = prg[--Z]"));
        assertTrue(hasError("r0 = prg[++Z]"));
        assertTrue(hasError("prg[Z] = r0"));
    }


    @Test
    void testArrayPortBits() throws CompileException {
        test("io[PORTA].0 = 1", "sbi\tPORTA, 0");
        test("io[DDRC].5 = 0", "cbi\tDDRC, 5");
    }

    @Test
    void testArrayPortBitErrors() {
        assertTrue(hasError("io[PORTC].1 = 2"));
        assertTrue(hasError("io[PORTC].10 = 0"));
        assertTrue(hasError("io[X].1 = 0"));
        assertTrue(hasError("io[PORTC++].1 = 0"));
//        assertTrue(hasError("io[--PORTC].1 = 0"));
    }

    @Test
    void testFlagsModify() throws CompileException {
        test("F_CARRY = 0", "clc");
        test("F_CARRY = 1", "sec");
    }

    @Test
    void testMoveIoToFlag() throws CompileException {
        test("F_CARRY = io[PINC].4", "clc\nsbic\tPINC, 4\nsec");
    }

    @Test
    void testMoveNegIoToFlag() throws CompileException {
        test("F_CARRY = !io[PINC].4", "clc\nsbis\tPINC, 4\nsec");
    }

    @Test
    void testMoveRegBitToIo() throws CompileException {
        test("io[PORTC].0 = r16[4]", "sbrs\tr16, 4\ncbi\tPORTC, 0\nsbrc\tr16, 4\nsbi\tPORTC, 0");
    }

    @Test
    void testMoveInverseRegBitToIo() throws CompileException {
        test("io[PORTC].0 = !r16[4]", "sbrs\tr16, 4\nsbi\tPORTC, 0\nsbrc\tr16, 4\ncbi\tPORTC, 0");
    }


    @Test
    void testFlagMath() throws CompileException {
//        test("r10 += r10 + F_CARRY", "adc\tr10, r0");
    }

}