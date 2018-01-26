package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.data.Alias;
import ru.trolsoft.asmext.data.Procedure;
import ru.trolsoft.asmext.files.OutputFile;
import ru.trolsoft.asmext.utils.TokenString;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    Parser parser = new Parser();
    ru.trolsoft.asmext.processor.Compiler compiler = new ru.trolsoft.asmext.processor.Compiler(parser);


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
        compiler = new ru.trolsoft.asmext.processor.Compiler(parser);

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

}
