package ru.trolsoft.asmext.processor;

import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.data.Alias;
import ru.trolsoft.asmext.data.Constant;
import ru.trolsoft.asmext.data.Procedure;

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
        for (int i = 0 ; i < parser.getOutput().size(); i++) {
            String line = parser.getOutput().get(i).trim();
            String[] split = line.split("\n");
            for (String s : split) {
                o.add(s.trim());
            }
        }
//        System.out.println(o);
//        System.out.println(o.size() + "  |  " + out.length);
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
            assertEquals(out[i++], s);
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
        parser.currentProcedure.addArg(new Alias("arg", new Token(Token.TYPE_REGISTER, "r24")));
        parser.currentProcedure.addAlias(new Alias("alias", new Token(Token.TYPE_REGISTER, "r22")));
        test(a(
                "push arg alias"
                ), a (
                "push r24",
                "push r22"
                )
        );

        parser = new Parser();
        test(a("push r1"), a("push r1"));
    }

    @Test
    void testAliases() throws SyntaxException {
        Parser parser = new Parser();
        parser.currentProcedure = new Procedure("proc");
        parser.currentProcedure.addAlias(new Alias("y", new Token(Token.TYPE_REGISTER, "r22")));

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
                "loop(r24 = 1) {",
                "}"
                ), a (
                "ldi r24, 1",
                    "__loop_r24_1:",
                    "dec r24",
                    "brne __loop_r24_1"
                )
        );
        parser = new Parser();
        test(a(
                "loop(r24 = r0) {",
                "}"
                ), a (
                "mov r24, r0",
                "__loop_r24_1:",
                "dec r24",
                "brne __loop_r24_1"
                )
        );
        parser = new Parser();
        test(a(
                "loop(r24 = 1+(1+1)) {",
                "}"
                ), a (
                "ldi r24, 1+(1+1)",
                "__loop_r24_1:",
                "dec r24",
                "brne __loop_r24_1"
                )
        );

        parser = new Parser();
        test(a(
                ".use r24 as rmp",
                "loop(rmp = 1+(1+1)) {",
                "}"
                ), a (
                 "",
                "ldi r24, 1+(1+1)",
                "__loop_r24_2:",
                "dec r24",
                "brne __loop_r24_2"
                )
        );

        parser = new Parser(true);
        parser.parseLine("#define FONT_SMALL_HEIGHT 10");
        test(a(
                "loop(r21 = FONT_SMALL_HEIGHT+1) {",
                "} ; r21"
                ), a (
                "ldi r21, 10+1",
                "__loop_r21_2:",
                "dec r21",
                "brne __loop_r21_2"
                )
        );
    }

    @Test
    void testWrongBlockrror() {
        parser = new Parser();
        boolean error;
        try {
            test(a("}"), a(""));
            error = false;
        } catch (SyntaxException e) {
            assertEquals(e.getMessage(), "open bracket not found: '{'");
            error = true;
        }
        assertTrue(error);
    }

    @Test
    void testLoopContinue() throws SyntaxException {
        parser = new Parser();
        test(a(
                "loop(r24 = 10) {",
                "continue",
                "}"
            ), a (
                "ldi r24, 10",
                "__loop_r24_1:",
                "rjmp __loop_r24_1",
                "dec r24",
                "brne __loop_r24_1"
                )
        );
    }

    @Test
    void testInfiniteLoop() throws SyntaxException {
        parser = new Parser();
        test(a(
                "loop {",
                "break",
                "}"
            ), a (
                "__loop_1:",
                "rjmp __loop_1_end",
                "rjmp __loop_1",
                "__loop_1_end:"
            )
        );
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
    void testProcWitchArgs() throws SyntaxException {
        parser = new Parser();
        test(a(
                ".proc my_proc(x: r22)", ".endproc"), a("my_proc:", "", ""));
        assertTrue(parser.procedures.containsKey("my_proc"));
        assertEquals(1, parser.procedures.get("my_proc").args.size());
        assertTrue(parser.procedures.get("my_proc").args.containsKey("x"));
        assertTrue(parser.procedures.get("my_proc").args.get("x").register.isRegister("r22"));

        test(a("rcall my_proc (3)"), a("", "ldi r22, 3", "rcall my_proc"));
    }

    @Test
    void testProcWitchUserArg() throws SyntaxException {
        parser = new Parser();

        parser.preloadLine(".DEF rmp = R16");
        parser.parseLine(".proc my_proc_2 (val: ZH.ZL.rmp)");
        assertEquals("my_proc_2", parser.currentProcedure.name);
        assertTrue(parser.currentProcedure.args.size() == 1);
//        assertTrue(parser.currentProcedure.args.containsKey("x"));
//        assertEquals("r24", parser.currentProcedure.args.get("x").register);
//        assertEquals("r22", parser.currentProcedure.args.get("y").register);
        parser.parseLine(".endproc");

    }



    @Test
    void testExternProc() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".extern fillCharXY (x:r24, y:r22, char:r20)");
        assertTrue(parser.procedures.size() == 1);

        assertEquals(parser.procedures.get("fillCharXY").name, "fillCharXY");
        assertTrue(parser.procedures.get("fillCharXY").args.size() == 3);
        assertEquals("r24", parser.procedures.get("fillCharXY").args.get("x").register.asString());
        assertEquals("r22", parser.procedures.get("fillCharXY").args.get("y").register.asString());
        assertEquals("r20", parser.procedures.get("fillCharXY").args.get("char").register.asString());

        parser.parseLine(".extern cmd_color : word");
        assertTrue(parser.variables.size() == 1);
        assertTrue(parser.variables.get("cmd_color").getSize() == 2);

        parser.parseLine(".extern cmd_x : byte");
        assertTrue(parser.variables.size() == 2);
        assertTrue(parser.variables.get("cmd_x").getSize() == 1);

        parser.parseLine(".extern data_ptr: prgptr");
        parser.parseLine("data_ptr:");
        parser.parseLine("\t.db 1");

        parser.parseLine(".extern data_ptr1, data_ptr2: prgptr");
        assertNotNull(parser.getVariable("data_ptr"));
        assertNotNull(parser.getVariable("data_ptr1"));
        assertNotNull(parser.getVariable("data_ptr2"));


        boolean error;
        try {
            parser.parseLine(".extern = : ptr");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine(".extern 123 : ptr");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

        try {
            parser.parseLine(".extern r23 : byte");
            error = false;
        } catch (SyntaxException e) {
            error = true;
        }
        assertTrue(error);

    }

    @Test
    void testLabels() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine("@lbl:");
        parser.parseLine("rjmp @lbl");
        parser.parseLine(".endproc ; my_proc");

        assertTrue(parser.getOutput().size() == 5);
        assertEquals("my_proc:", parser.getOutput().get(0));
        assertEquals("; .proc my_proc", parser.getOutput().get(1));
        assertEquals("my_proc__lbl:", parser.getOutput().get(2));
        assertEquals("rjmp\tmy_proc__lbl", parser.getOutput().get(3));
        assertEquals("; .endproc ; my_proc", parser.getOutput().get(4));
    }

    @Test
    void testArgs() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine(".args x(r24), y(r22)");
        assertEquals("my_proc", parser.currentProcedure.name);
        assertTrue(parser.currentProcedure.args.size() == 2);
        assertTrue(parser.currentProcedure.args.containsKey("x"));
        assertTrue(parser.currentProcedure.args.containsKey("y"));
        assertEquals("r24", parser.currentProcedure.args.get("x").register.asString());
        assertEquals(Token.TYPE_REGISTER, parser.currentProcedure.args.get("x").register.getType());
        assertEquals("r22", parser.currentProcedure.args.get("y").register.asString());
        assertEquals(Token.TYPE_REGISTER, parser.currentProcedure.args.get("y").register.getType());
        parser.parseLine(".endproc ; my_proc");
        assertTrue(parser.currentProcedure == null);


        parser = new Parser();
        boolean error;
        try {
            parser.parseLine(".args x(r24), y(r22)");
            error = false;
        } catch (SyntaxException e) {
            assertTrue(e.getMessage().contains(".args can be defined in .proc block only"));
            error = true;
        }
        assertTrue(error);

        parser = new Parser();
        try {
            parser.parseLine(".proc name");
            parser.parseLine(".args r11(x)");
            error = false;
        } catch (SyntaxException e) {
            assertTrue(e.getMessage().contains("wrong name: 'r11' (register name)"));
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
        parser.parseLine("r20 = abc");
        parser.getOutput().startNewLine();
        assertTrue(parser.getOutput().get(1).endsWith("ldi\tr20, 123"));

        parser = new Parser();
        parser.gcc = true;
        parser.parseLine(".equ abc = 123 ; comment");
        assertTrue(parser.constants.containsKey("abc"));
        assertEquals(parser.constants.get("abc").value, "123");
        assertEquals(parser.constants.get("abc").type, Constant.Type.EQU);
        assertTrue(parser.getOutput().size() == 0);
        parser.parseLine("r20 = abc");
        parser.getOutput().startNewLine();
        assertTrue(parser.getOutput().get(0).endsWith("ldi\tr20, 123"));

        parser = new Parser();
        parser.gcc = true;
        parser.parseLine(" .EQU aBc = 123");
        assertTrue(parser.constants.containsKey("aBc"));
        assertEquals(parser.constants.get("aBc").value, "123");
        assertEquals(parser.constants.get("aBc").type, Constant.Type.EQU);
        assertTrue(parser.getOutput().size() == 0);
        parser.parseLine("r20 = aBc");
        parser.getOutput().startNewLine();
        assertTrue(parser.getOutput().get(0).endsWith("ldi\tr20, 123"));

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
        parser.parseLine("r20 = porta");
        assertEquals("ldi\tr20, (0x23+2)", parser.getOutput().getLastLine());

        parser.parseLine(".equ portb = ((porta) + 1)");
        parser.parseLine("r21 = portb");
        assertEquals("ldi\tr21, ((0x23+2)+1)", parser.getOutput().getLastLine());

        parser = new Parser(true);
        parser.parseLine(".equ a = 12");
        parser.parseLine(".equ b = a + 7");
        parser.parseLine("r20 = (a + b)");
        assertEquals("ldi\tr20, (12+(12+7))", parser.getOutput().getLastLine());

        parser = new Parser(true);
        parser.parseLine(".equ a = 12");
        parser.parseLine(".equ b = a + 7");
        parser.parseLine("r20 = r1 + (a - b)");
        assertEquals("subi\tr20, -(12-(12+7))", parser.getOutput().getLastLine());
    }


    @Test
    void testUse() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".use r16 as rmp");
        assertTrue(parser.globalAliases.containsKey("rmp"));
        assertEquals(parser.globalAliases.get("rmp").register.toString(), "r16");
        assertEquals(parser.globalAliases.get("rmp").register.getType(), Token.TYPE_REGISTER);
        parser.parseLine("rmp = 0");
        assertTrue(parser.getOutput().getLastLine().contains("clr\tr16"));
        parser.parseLine("rmp = 'a'");
        assertTrue(parser.getOutput().getLastLine().contains("ldi\tr16, 'a'"));
        parser.parseLine("ldi\trmp, '0'");
        assertTrue(parser.getOutput().getLastLine().contains("ldi\tr16, '0'"));
        parser.parseLine("rmp = ' '");
        assertTrue(parser.getOutput().getLastLine().contains("ldi\tr16, ' '"));

        parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine(".use r16 as x");
        assertTrue(parser.currentProcedure.uses.containsKey("x"));
        assertEquals("r16", parser.currentProcedure.getAlias("x").register.asString());
        parser.parseLine(".endproc");

        parser = new Parser();
        parser.gcc = false;
        parser.preloadLine(".DEF rmp = R16 ; comment");
        assertTrue(parser.globalAliases.containsKey("rmp"));
        assertEquals("R16", parser.globalAliases.get("rmp").register.asString());
        parser.parseLine("rmp = 0x1B");
        assertTrue(parser.getOutput().getLastLine().contains("ldi\tR16, 0x1B"));
        parser.parseLine("rmp = ' '");
        assertTrue(parser.getOutput().getLastLine().contains("ldi\tR16, ' '"));

        parser = new Parser();
        parser.parseLine(".use r16 as x1, r17 as x2");
        assertTrue(parser.globalAliases.containsKey("x1"));
        assertTrue(parser.globalAliases.containsKey("x2"));
        assertEquals("r16", parser.globalAliases.get("x1").register.asString());
        assertEquals("r17", parser.globalAliases.get("x2").register.asString());

        parser = new Parser();
        boolean error;
        try {
            parser.parseLine(".use r1 as r2");
            error = false;
        } catch (SyntaxException e) {
            error = true;
            assertTrue(e.getMessage().contains("wrong name: 'r2' (register name)"));
        }
        assertTrue(error);
    }

    @Test
    void testSkip() throws SyntaxException {
        parser = new Parser();
        parser.parseLine(".INCLUDE \"m8def.inc\"");
        assertEquals(parser.getOutput().get(0), ".INCLUDE \"m8def.inc\"");
    }


    @Test
    void testPreprocessor() throws SyntaxException {
        parser = new Parser(true);
        parser.parseLine("#define __ATMEGA8__");
        parser.parseLine("#ifdef __ATMEGA8__");
        parser.parseLine("#endif __ATMEGA8__");
        assertEquals(3, parser.getOutput().size());
        assertEquals("#define __ATMEGA8__", parser.getOutput().get(0));
        assertEquals("#ifdef __ATMEGA8__", parser.getOutput().get(1));
        assertEquals("#endif __ATMEGA8__", parser.getOutput().get(2));
    }


    @Test
    void testExpressions() throws SyntaxException {
        parser = new Parser(false);
        parser.parseLine(".equ\tURSEL\t= 7\t; Register Select");
        parser.parseLine(".equ\tUCSZ0\t= 1\t; Character Size");
        parser.parseLine(".equ\tUCSZ1\t= 2\t; Character Size");
        parser.parseLine("r19 = (1<<URSEL)|(1<<UCSZ1)|(1<<UCSZ0)");

        parser.parseLine("r20 = BYTE1(cFreq/256)");

        parser.parseLine(".equ FE=1");
        parser.parseLine(".equ DOR=0");
        parser.parseLine(".equ PE=4");
        parser.parseLine("r21 &= (1<<FE)|(1<<DOR)|(1<<PE)");
        assertEquals("andi\tr21, (1<<FE)|(1<<DOR)|(1<<PE)", parser.getOutput().getLastLine());

        // rmp = cPre2|(1<<WGM21)		; CTC mode and prescaler
        // TODO

        parser = new Parser(true);
        parser.parseLine(".equ LCD_DELTA_X");
        parser.parseLine("r25.r24 += LCD_DELTA_X");
        assertEquals("adiw\tr24, (LCD_DELTA_X)", parser.getOutput().getLastLine());

        parser = new Parser(false);
        parser.parseLine(".equ LCD_DELTA_X");
        parser.parseLine("if (r25 == LCD_DELTA_X) goto label");
        assertEquals("breq\tlabel", parser.getOutput().getLastLine());
    }

    @Test
    void testPairArgs() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".proc my_proc");
        parser.parseLine(".args x(r23.r24)");
        assertEquals("my_proc", parser.currentProcedure.name);
        assertTrue(parser.currentProcedure.args.size() == 1);
//        assertTrue(parser.currentProcedure.args.containsKey("x"));
//        assertEquals("r24", parser.currentProcedure.args.get("x").register);
//        assertEquals("r22", parser.currentProcedure.args.get("y").register);
        parser.parseLine(".endproc");


        parser.preloadLine(".DEF rmp = R16");
        parser.parseLine(".proc my_proc_2");
        parser.parseLine(".args val(ZH.ZL.rmp)");
        assertEquals("my_proc_2", parser.currentProcedure.name);
        assertTrue(parser.currentProcedure.args.size() == 1);
//        assertTrue(parser.currentProcedure.args.containsKey("x"));
//        assertEquals("r24", parser.currentProcedure.args.get("x").register);
//        assertEquals("r22", parser.currentProcedure.args.get("y").register);
        parser.parseLine(".endproc");
    }


    @Test
    void testParseIfBlock() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine("if (r21 == 5) { ; comment");
        parser.parseLine("r21 = 0");
        parser.parseLine("cli");
        parser.parseLine("}");

        assertEquals(6, parser.getOutput().size());
        assertEquals("; if (r21 == 5) { ; comment", parser.getOutput().get(0));
        assertEquals("cpi\tr21, 5\t\t; comment", parser.getOutput().get(1));
        assertEquals("brne\t__if_1\t\t; comment", parser.getOutput().get(2));
        assertEquals("clr\tr21", parser.getOutput().get(3));
        assertEquals("cli", parser.getOutput().get(4));
        assertEquals("__if_1:", parser.getOutput().get(5));
    }

    @Test
    void testParseIfElseBlocks() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine("if (r21 == 5) {");
        parser.parseLine("r21 = 0");
        parser.parseLine("cli");
        parser.parseLine("} else {");
        parser.parseLine("r21 = 5");
        parser.parseLine("sei");
        parser.parseLine("}");


        assertEquals(10, parser.getOutput().size());
        assertEquals("; if (r21 == 5) {", parser.getOutput().get(0));
        assertEquals("cpi\tr21, 5", parser.getOutput().get(1));
        assertEquals("brne\t__if_1", parser.getOutput().get(2));
        assertEquals("clr\tr21", parser.getOutput().get(3));
        assertEquals("cli", parser.getOutput().get(4));
        assertEquals("rjmp\t__if_else_4", parser.getOutput().get(5));
        assertEquals("__if_1:", parser.getOutput().get(6));
        assertEquals("ldi\tr21, 5", parser.getOutput().get(7));
        assertEquals("sei", parser.getOutput().get(8));
        assertEquals("__if_else_4:", parser.getOutput().get(9));
    }

    @Test
    void testBytesBlock() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine("byte[] {  ; data");
        parser.parseLine("0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a");
        parser.parseLine("0, 1,");
        parser.parseLine("123, 0xab, CONST");
        parser.parseLine("}");
        assertTrue(parser.getOutput().size() >= 2);
        assertEquals(".db\t0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07", parser.getOutput().get(0).trim());
        assertEquals(".db\t0x08, 0x09, 0x0a, 0, 1, 123, 0xab, CONST", parser.getOutput().get(1).trim());
    }

    @Test
    void testPrgPtrDeclarationError() {
        Parser parser = new Parser();
        try {
            parser.parseLine(".extern font_5x7 : prg");
            assertTrue(false);
        } catch (SyntaxException ignore) {}
    }

    @Test
    void testCallSingleEqu() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".equ COLOR");
        parser.parseLine(".extern spi_write_word (w: r25.r24)");
        parser.parseLine("rcall spi_write_word (COLOR)");
        assertEquals(4, parser.getOutput().size());
        assertEquals("ldi\tr24, BYTE1(COLOR)", parser.getOutput().get(1));
        assertEquals("ldi\tr25, BYTE2(COLOR)", parser.getOutput().get(2));
        assertEquals("rcall\tspi_write_word", parser.getOutput().get(3));
    }

    @Test
    void testCallMultipleEqu() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".equ COLOR");
        parser.parseLine(".equ ATTR");
        parser.parseLine(".extern spi_write_data (color: r25.r24, attr: r22)");
        parser.parseLine("rcall spi_write_data (color: COLOR, attr: ATTR)");
        assertEquals(5, parser.getOutput().size());
        assertEquals("ldi\tr24, BYTE1(COLOR)", parser.getOutput().get(1));
        assertEquals("ldi\tr25, BYTE2(COLOR)", parser.getOutput().get(2));
        assertEquals("ldi\tr22, ATTR", parser.getOutput().get(3));
        assertEquals("rcall\tspi_write_data", parser.getOutput().get(4));
    }

    @Test
    void testMoveEquToAliasPair() throws SyntaxException {
        Parser parser = new Parser();
        parser.parseLine(".equ COLOR");
        parser.parseLine(".use r25.r24 as color");
        parser.parseLine("color = COLOR");
        assertEquals(3, parser.getOutput().size());
        assertEquals("ldi\tr24, BYTE1(COLOR)", parser.getOutput().get(1));
        assertEquals("ldi\tr25, BYTE2(COLOR)", parser.getOutput().get(2));
    }


//    @Test
//    void testInline() throws SyntaxException {
//        Parser parser = new Parser();
//        parser.parseLine("inline macro_name (val) {");
//        parser.parseLine("  .use r16 as tmp");
//        parser.parseLine("  io[DDDRD] = tmp = val");
//        parser.parseLine("}");
//
//        parser.parseLine("macro_name (1)");
//    }

}
