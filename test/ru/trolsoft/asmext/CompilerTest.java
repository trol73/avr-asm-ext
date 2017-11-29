package ru.trolsoft.asmext;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {
    Parser parser = new Parser();
    Compiler compiler = new Compiler(parser);


    private static String strip(String s) {
        return s.replace('\t', ' ').replace("  ", " ").trim();
    }

    void testLine(String src, String res) throws SyntaxException {
        StringBuilder out = new StringBuilder();
        compiler.compile(src, parser.splitToTokens(src), out);

        if (out.length() > 0 && out.charAt(0) == ';') {
            while (out.length() > 0) {
                char ch = out.charAt(0);
                out.deleteCharAt(0);
                if (ch == '\n') {
                    break;
                }
            }
        }
        String outStr = strip(out.toString());
        res = strip(res);
//        System.out.println(outStr);
//        System.out.println(res);
        assertEquals(outStr, res);
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

        testLine("if (r1 == 0) goto lbl ; comment", "tst\tr1\nbreq\tlbl");
        testLine("if (r1 == 0) goto lbl // comment", "tst\tr1\nbreq\tlbl");
    }

    @Test
    void testProcCalls() throws SyntaxException {
        parser = new Parser();
        compiler = new Compiler(parser);

        Procedure proc = new Procedure("my_proc");
        proc.addArg(new Alias("x", "r24"));
        proc.addArg(new Alias("y", "r22"));
        parser.procedures.put(proc.name, proc);

        testLine("rcall my_proc (x: 1, y: r0) // comment", "ldi\tr24, 1\t ; x = 1\nmov\tr22, r0\t ; y = r0\nrcall\tmy_proc");
        testLine("rjmp my_proc (x: r24, y: r0) ; comment", "; x = r24\nmov\tr22, r0\t ; y = r0\nrjmp\tmy_proc");
        testLine("rjmp my_proc (x: r24 + 1, y: r0) ; comment", "inc r24 ; x = r24+1\nmov\tr22, r0\t ; y = r0\nrjmp\tmy_proc");
        testLine("rjmp my_proc (x: r24 + 1*2, y: r0) ; comment", "subi r24, -(1*2) ; x = r24+1*2\nmov\tr22, r0\t ; y = r0\nrjmp\tmy_proc");

        proc = new Procedure("my_proc");
        parser.procedures.clear();
        proc.addArg(new Alias("x", "r24"));
        parser.procedures.put(proc.name, proc);
        testLine("rcall my_proc (10) // comment", "ldi\tr24, 10\t ; x = 10\nrcall\tmy_proc");
        testLine("rcall my_proc (xl)", "mov\tr24, xl\t ; x = xl\nrcall\tmy_proc");

        parser.parseLine(".extern ext_proc (var: r24)");
        testLine("rcall ext_proc (r1)", "mov\tr24, r1\t ; var = r1\nrcall\text_proc");

        parser.parseLine(".extern ext_var : byte");
        testLine("rcall ext_proc (ext_var)", "lds\tr24, ext_var\t ; var = ext_var\nrcall\text_proc");
    }

}
