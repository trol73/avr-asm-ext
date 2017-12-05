package ru.trolsoft.asmext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Parser parser;

    private static String[] a(String ... strs) {
        return strs;
    }

    private void test(String[] src, String[] out) throws SyntaxException {
        parser.getOutput().clear();
        for (String s : src) {
            parser.parseLine(s);
        }
        List<String> o = new ArrayList<>();
        for (String line : parser.getOutput()) {
            line = line.trim();
            String[] split = line.split("\n");
            for (String s : split) {
                o.add(s.trim());
            }
        }
        //System.out.println(o);
        //System.out.println(o.size() + "  |  " + out.length);
        assertTrue(o.size() == out.length);
        int i = 0;
        for (String s : o) {
            int index = s.indexOf(";");
            if (index >= 0) {
                s = s.substring(0, index);
            }
            s = s.trim();
            s = s.replace('\t', ' ');
            //System.out.println("? " + s + "|" + out[i]);
            assertEquals(s, out[i++]);
        }
    }


    @Test
    void testPush() throws SyntaxException {
        parser = new Parser();
        test(a(
                "push r2 r1",
                "pop r1\tr2"
                ), a (
                "push r2",
                "push r1",
                "pop r1",
                "pop r2"
                )
        );

        parser = new Parser();
        parser.currentProcedure = new Procedure("proc");
        parser.currentProcedure.addArg(new Alias("arg", "r24"));
        parser.currentProcedure.addAlias(new Alias("alias", "r22"));
        test(a(
                "push arg alias"
                ), a (
                "push r24",
                "push r22"
                )
        );
    }

    @Test
    void testAliases() throws SyntaxException {
        Parser parser = new Parser();
        parser.currentProcedure = new Procedure("proc");
        parser.currentProcedure.addAlias(new Alias("y", "r22"));

        parser.parseLine("ld r24, Y+");
        assertEquals(parser.getOutput().get(0), "ld r24, Y+");
        parser.getOutput().clear();

        parser.parseLine("ld y, Y+");
        assertEquals(parser.getOutput().get(0), "ld r22, Y+");
        parser.getOutput().clear();

    }

    @Test
    void testLoop() throws SyntaxException {
        parser = new Parser();
        test(a(
                ".loop(r24 = 1):",
                ".endloop"
                ), a (
                "ldi r24, 1",
                    "__r24_1:",
                    "dec r24",
                    "brne __r24_1"
                )
        );
        parser = new Parser();
        test(a(
                ".loop(r24 = r0):",
                ".endloop"
                ), a (
                "mov r24, r0",
                "__r24_1:",
                "dec r24",
                "brne __r24_1"
                )
        );
        parser = new Parser();
        test(a(
                ".loop(r24 = 1+(1+1)):",
                ".endloop"
                ), a (
                "ldi r24, 1+(1+1)",
                "__r24_1:",
                "dec r24",
                "brne __r24_1"
                )
        );
        parser = new Parser();
        boolean error;
        try {
            test(a(".endloop"), a(""));
            error = false;
        } catch (SyntaxException e) {
            assertEquals(e.getMessage(), ".loop not found");
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testProc() throws SyntaxException {
        parser = new Parser();
        test(a(
                ".proc my_proc", ".endproc"), a("my_proc:", "", ""));
        assertTrue(parser.procedures.containsKey("my_proc"));

        boolean error;
        try {
            test(a(".endproc"), a(""));
            error = false;
        } catch (SyntaxException e) {
            assertEquals(e.getMessage(), "start .proc directive not found");
            error = true;
        }
        assertTrue(error);
    }


    @Test
    void testExternProc() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".extern fillCharXY (x:r24, y:r22, char:r20)");
        assertTrue(parser.procedures.size() == 1);

        assertEquals(parser.procedures.get("fillCharXY").name, "fillCharXY");
        assertTrue(parser.procedures.get("fillCharXY").args.size() == 3);
        assertEquals(parser.procedures.get("fillCharXY").args.get("x").register, "r24");
        assertEquals(parser.procedures.get("fillCharXY").args.get("y").register, "r22");
        assertEquals(parser.procedures.get("fillCharXY").args.get("char").register, "r20");

        parser.parseLine(".extern cmd_color : word");
        assertTrue(parser.variables.size() == 1);
        assertTrue(parser.variables.get("cmd_color").size == 2);

        parser.parseLine(".extern cmd_x : byte");
        assertTrue(parser.variables.size() == 2);
        assertTrue(parser.variables.get("cmd_x").size == 1);
    }

    @Test
    void testLabels() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine("@lbl:");
        parser.parseLine("rjmp @lbl");
        parser.parseLine(".endproc ; my_proc");

        assertTrue(parser.getOutput().size() == 5);
        assertEquals(parser.getOutput().get(0), "my_proc:");
        assertEquals(parser.getOutput().get(1), ";.proc my_proc");
        assertEquals(parser.getOutput().get(2), "my_proc__lbl:");
        assertEquals(parser.getOutput().get(3), "rjmp my_proc__lbl");
        assertEquals(parser.getOutput().get(4), ";.endproc ; my_proc");
    }

    @Test
    void testArgs() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine(".args x(r24), y(r22)");
        assertEquals(parser.currentProcedure.name, "my_proc");
        assertTrue(parser.currentProcedure.args.size() == 2);
        assertTrue(parser.currentProcedure.args.containsKey("x"));
        assertTrue(parser.currentProcedure.args.containsKey("y"));
        assertEquals(parser.currentProcedure.args.get("x").register, "r24");
        assertEquals(parser.currentProcedure.args.get("y").register, "r22");
        parser.parseLine(".endproc ; my_proc");
        assertTrue(parser.currentProcedure == null);


        parser = new Parser();
        boolean error;
        try {
            parser.parseLine(".args x(r24), y(r22)");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testEqu() throws SyntaxException {
        Parser parser = new Parser(false);
        parser.parseLine(".equ abc = 123 ; comment");
        assertTrue(parser.constants.containsKey("abc"));
        assertEquals(parser.constants.get("abc").value, "123");
        assertEquals(parser.constants.get("abc").type, Constant.Type.EQU);
        assertTrue(parser.getOutput().size() == 1);
        parser.parseLine("r0 = abc");
        assertTrue(parser.getOutput().get(1).endsWith("ldi\tr0, abc"));

        parser = new Parser();
        parser.gcc = true;
        parser.parseLine(".equ abc = 123 ; comment");
        assertTrue(parser.constants.containsKey("abc"));
        assertEquals(parser.constants.get("abc").value, "123");
        assertEquals(parser.constants.get("abc").type, Constant.Type.EQU);
        assertTrue(parser.getOutput().size() == 0);
        parser.parseLine("r0 = abc");
        assertTrue(parser.getOutput().get(0).endsWith("ldi\tr0, 123"));

        parser.parseLine("#define CONST_X\t10");
        assertTrue(parser.constants.containsKey("CONST_X"));
        assertEquals(parser.constants.get("CONST_X").value, "10");
        assertEquals(parser.constants.get("CONST_X").type, Constant.Type.DEFINE);

        parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine(".equ local = 0x123");
        assertTrue(parser.currentProcedure.hasConst("local"));
        assertTrue(parser.isConstant("local"));
        parser.parseLine(".endproc");
        assertTrue(!parser.isConstant("local"));
    }

    @Test
    void testNestedGccEqu() throws SyntaxException {
        Parser parser = new Parser(true);
        parser.parseLine(".equ io_offset = 0x23");
        parser.parseLine(".equ porta = io_offset + 2");
        parser.parseLine("r0 = porta");
        assertTrue(parser.getOutput().get(0).endsWith("di\tr0, (0x23 + 2)"));

        parser.parseLine(".equ portb = ((porta) + 1)");
        parser.parseLine("r1 = portb");
        assertTrue(parser.getOutput().get(1).endsWith("ldi\tr1, (((0x23 + 2)) + 1)"));

        parser = new Parser(true);
        parser.parseLine(".equ a = 12");
        parser.parseLine(".equ b = a + 7");
        parser.parseLine("r20 = (a + b)");
        assertTrue(parser.getOutput().get(0).contains("ldi\tr20, (12+(12 + 7))"));

        parser = new Parser(true);
        parser.parseLine(".equ a = 12");
        parser.parseLine(".equ b = a + 7");
        parser.parseLine("r20 = r1 + (a - b)");
        assertTrue(parser.getOutput().get(0).contains("subi\tr20, -(12-(12 + 7))"));
    }

}
