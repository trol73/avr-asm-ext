package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.data.Alias;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    Parser parser = new Parser();
    Compiler compiler = new Compiler(parser);


    private static String strip(String s) {
        return s.replace('\t', ' ').replace("  ", " ").trim();
    }

    void testLine(String src, String res) throws SyntaxException {
        OutputFile out = new OutputFile();
        compiler.compile(new TokenString(src), out);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            String s = out.get(i).trim();
            if (s.startsWith(";") || s.startsWith("//")) {
                continue;
            }
            sb.append(s).append("\n");
        }

        String outStr = strip(sb.toString());
        res = strip(res);
//        System.out.println(outStr);
//        System.out.println(res);
        assertEquals(res, outStr);
    }


    @Test
    void testIfGoto() throws SyntaxException {
        testLine("if (r21 == 0) goto lbl", "tst\tr21\nbreq\tlbl");
        testLine("if (r30 != 0) goto lbl", "tst\tr30\nbrne\tlbl");
        testLine("if (r21 == 10) goto lbl", "cpi\tr21, 10\nbreq\tlbl");
        testLine("if (r21 != 10) goto lbl", "cpi\tr21, 10\nbrne\tlbl");
        testLine("if (r21 != 10) goto lbl", "cpi\tr21, 10\nbrne\tlbl");
        testLine("if (r21 != r22) goto lbl", "cp\tr21, r22\nbrne\tlbl");
        testLine("if (r2 == r22) goto lbl", "cp\tr2, r22\nbreq\tlbl");

        testLine("if (r1 < r2) goto lbl", "cp\tr1, r2\nbrlo\tlbl");
        testLine("if (r1 < 10) goto lbl", "cpi\tr1, 10\nbrlo\tlbl");
        testLine("if s(r1 < 10) goto lbl", "cpi\tr1, 10\nbrlt\tlbl");
        testLine("if u(r1 < r2) goto lbl", "cp\tr1, r2\nbrlo\tlbl");
        testLine("if s(r1 < r2) goto lbl", "cp\tr1, r2\nbrlt\tlbl");

        testLine("if (r1 > r2) goto lbl", "cp\tr2, r1\nbrlo\tlbl");
        testLine("if (r1 > 10) goto lbl", "cpi\tr1, 10+1\nbrsh\tlbl");
        testLine("if s(r1 > 10) goto lbl", "cpi\tr1, 10+1\nbrge\tlbl");
        testLine("if u(r1 > r2) goto lbl", "cp\tr2, r1\nbrlo\tlbl");
        testLine("if s(r1 > r2) goto lbl", "cp\tr2, r1\nbrlt\tlbl");

        testLine("if (r1 >= r2) goto lbl", "cp\tr1, r2\nbrsh\tlbl");
        testLine("if (r1 >= 10) goto lbl", "cpi\tr1, 10\nbrsh\tlbl");
        testLine("if s(r1 >= 10) goto lbl", "cpi\tr1, 10\nbrge\tlbl");
        testLine("if u(r1 >= r2) goto lbl", "cp\tr1, r2\nbrsh\tlbl");
        testLine("if s(r1 >= r2) goto lbl", "cp\tr1, r2\nbrge\tlbl");

        testLine("if (r1 <= r2) goto lbl", "cp\tr2, r1\nbrsh\tlbl");
        testLine("if (r1 <= 10) goto lbl", "cpi\tr1, 10-1\nbrlo\tlbl");
        testLine("if s(r1 <= 10) goto lbl", "cpi\tr1, 10-1\nbrlt\tlbl");
        testLine("if u(r1 <= r2) goto lbl", "cp\tr2, r1\nbrsh\tlbl");
        testLine("if s(r1 <= r2) goto lbl", "cp\tr2, r1\nbrge\tlbl");

        testLine("if (r1 == 0) goto lbl ; comment", "tst\tr1\t\t; comment\nbreq\tlbl\t\t; comment");
        testLine("if (r1 == 0) goto lbl // comment", "tst\tr1\t\t// comment\nbreq\tlbl\t\t// comment");

        testLine("if (r1 < '9'+1) goto lbl", "cpi\tr1, '9'+1\nbrlo\tlbl");
    }

    @Test
    void testIfGroupGoto() throws SyntaxException {
        testLine("if (r21.r20 < r23.r22) goto lbl", "cp\tr20, r22\ncpc\tr21, r23\nbrlo\tlbl");
        testLine("if (r25.r24 == ZH.ZL) goto lbl", "cp\tr24, ZL\ncpc\tr25, ZH\nbreq\tlbl");
        testLine("if (r25.r24 == Z) goto lbl", "cp\tr24, ZL\ncpc\tr25, ZH\nbreq\tlbl");
    }


    @Test
    void testProcCalls() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        Procedure proc = new Procedure("my_proc");
        proc.addArg(new Alias("x", new Token(Token.TYPE_REGISTER, "r24")));
        proc.addArg(new Alias("y", new Token(Token.TYPE_REGISTER, "r22")));
        parser.procedures.put(proc.name, proc);

        testLine("rcall my_proc (x: 1, y: r0) // comment", "ldi\tr24, 1\nmov\tr22, r0\nrcall\tmy_proc\t\t// comment");
        testLine("rjmp my_proc (x: r24, y: r0) ; comment", "mov\tr22, r0\nrjmp\tmy_proc\t\t; comment");
        testLine("rjmp my_proc (x: r24 + 1, y: r0) ; comment", "inc r24\nmov\tr22, r0\nrjmp\tmy_proc\t\t; comment");
        testLine("rjmp my_proc (x: r24 + 1*2, y: r0) ; comment", "subi r24, -(1*2)\nmov\tr22, r0\nrjmp\tmy_proc\t\t; comment");

        testLine("rjmp my_proc ; comment", "rjmp\tmy_proc\t\t; comment");
        testLine("rcall my_proc // comment", "rcall\tmy_proc\t\t// comment");

        proc = new Procedure("my_proc");
        parser.procedures.clear();
        proc.addArg(new Alias("x", new Token(Token.TYPE_REGISTER, "r24")));
        parser.procedures.put(proc.name, proc);
        testLine("rcall my_proc (10) // comment", "ldi\tr24, 10\nrcall\tmy_proc\t\t// comment");
        testLine("rcall my_proc (xl)", "mov\tr24, xl\nrcall\tmy_proc");

        parser.parseLine(".extern ext_proc (var: r24)");
        testLine("rcall ext_proc (r1)", "mov\tr24, r1\nrcall\text_proc");

        parser.parseLine(".extern ext_var : byte");
        testLine("rcall ext_proc (ext_var)", "lds\tr24, ext_var\nrcall\text_proc");

        parser = new Parser();
        parser.gcc = false;
        parser.preloadLine(".def rmp=r24");
        parser.parseLine(".proc my_proc");
        parser.parseLine(".args val(rmp)");
        parser.parseLine(".endproc");
        parser.parseLine("rcall my_proc(0x03)");
        assertTrue(parser.getOutput().get(5).contains("ldi\tr24, 0x03"));
        parser.parseLine("rcall my_proc('=')");
    }

    @Test
    void testIfGotoFlags() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        testLine("if (F_CARRY) goto CalcPwO", "brcs\tCalcPwO");
        testLine("if (!F_CARRY) goto CalcPwO", "brcc\tCalcPwO");
    }

    @Test
    void testIfRegisterBitSet() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        testLine("if (r16[2]) goto loc_43", "sbrc\tr16, 2\nrjmp\tloc_43");
        testLine("if (r1[OFFSET]) goto loc_43", "sbrc\tr1, OFFSET\nrjmp\tloc_43");

        testLine("if (r16[2]) rjmp loc_43", "sbrc\tr16, 2\nrjmp\tloc_43");

        testLine("if (r16[2]) rcall proc", "sbrc\tr16, 2\nrcall\tproc");

        testLine("if (r16[2]) ret", "sbrc\tr16, 2\nret");
    }

    @Test
    void testIfRegisterBitClear() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        testLine("if (!r16[2]) goto loc_44", "sbrs\tr16, 2\nrjmp\tloc_44");
        testLine("if (!r16[OFFSET]) goto loc_44", "sbrs\tr16, OFFSET\nrjmp\tloc_44");

        testLine("if (!r16[2]) rjmp loc_44", "sbrs\tr16, 2\nrjmp\tloc_44");

        testLine("if (!r16[2]) rcall proc", "sbrs\tr16, 2\nrcall\tproc");

        testLine("if (!r16[2]) reti", "sbrs\tr16, 2\nreti");
    }

    @Test
    void testIfRegisterBitClearContinueLoop() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        parser.parseLine("loop (r20 = 1) {");
        testLine("if (!r16[2]) continue", "sbrs\tr16, 2\nrjmp\t" + parser.getLastBlock().getLabelStart());
    }

    @Test
    void testIfRegisterBitClearBreakLoop() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        parser.parseLine("loop (r20 = 1) {");
        testLine("if (!r16[2]) break", "sbrs\tr16, 2\nrjmp\t" + parser.getLastBlock().buildEndLabel());
    }

    @Test
    void testIfRegisterBitSetExpression() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        testLine("if (r16[2]) Z++", "sbrc\tr16, 2\nadiw\tZL, 1");
        testLine("if (r16[2]) r16 |= 0x10", "sbrc\tr16, 2\nori\tr16, 0x10");
    }

    @Test
    void testIfRegisterBitSetError() {
        parser = new Parser();
        compiler = new Compiler(parser);

        boolean error;
        try {
            testLine("if (r16[8]) goto loc_43", "sbrc\tr16, 8\nrjmp\tloc_43");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testIfIoBitIsSet() throws SyntaxException {
        testLine("if (io[SPSR].SPIF) goto loop", "sbic\tSPSR, SPIF\nrjmp\tloop");
    }

    @Test
    void testIfIoBitIsClear() throws SyntaxException {
        testLine("if (!io[SPSR].SPIF) goto loop", "sbis\tSPSR, SPIF\nrjmp\tloop");
    }

    @Test
    void testRegisterBitConst() throws SyntaxException {
        parser = new Parser();
        parser.parseLine(".EQU bEdge = 4");
        compiler = new Compiler(parser);
        testLine("r10[bEdge] = 1", "sbr\tr10, 1<<bEdge");
    }

    @Test
    void testRegBitWrongOperation() {
        boolean error;
        try {
            testLine("r10[0] |= 1", "");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testEqu() throws SyntaxException {
        parser = new Parser(false);
        parser.parseLine(".EQU PORT_VAL = 1<<4");
        compiler = new Compiler(parser);
        testLine("r16 = PORT_VAL", "ldi\tr16, PORT_VAL");
    }


//    @Test
//    void testLoopIfBreak() throws SyntaxException {
//        parser = new Parser();
//        compiler = new Compiler(parser);
//
//        parser.parseLine(".loop");
//
//    }

}
