package ru.trolsoft.asmext.utils;

import org.junit.jupiter.api.Test;
import ru.trolsoft.asmext.compiler.Cmd;
import ru.trolsoft.asmext.processor.SyntaxException;

import static org.junit.jupiter.api.Assertions.*;
import static ru.trolsoft.asmext.utils.AsmUtils.*;

class AsmUtilsTest {
    @Test
    void testSimple() throws SyntaxException {
        Instruction instr = parseLine("cli");
        assertEquals(Cmd.CLI, instr.getCommand());
        assertNull(instr.getArg1Str());
        assertNull(instr.getArg2Str());
    }

    @Test
    void testSingle() throws SyntaxException {
        Instruction instr = parseLine("rjmp lbl");
        assertEquals(Cmd.RJMP, instr.getCommand());
        assertEquals("lbl", instr.getArg1Str());
        assertNull(instr.getArg2Str());
    }

    @Test
    void testDouble() throws SyntaxException {
        Instruction instr = parseLine("mov r1, r20");
        assertEquals(Cmd.MOV, instr.getCommand());
        assertEquals("r1", instr.getArg1Str());
        assertEquals("r20", instr.getArg2Str());
    }

    @Test
    void testMultiple() throws SyntaxException {
        Instruction instr = parseLine("ldi r1, (1+1)");
        assertEquals(Cmd.LDI, instr.getCommand());
        assertEquals("r1", instr.getArg1Str());
        assertEquals("(1+1)", instr.getArg2Str());
    }

    @Test
    void testInvalidCmd() {
        try {
            parseLine("inv r1, (1+1)");
            fail("SyntaxException expected");
        } catch (SyntaxException ignore) {
        }
    }

    @Test
    void testNoComma() {
        try {
            parseLine("mov r1 r2");
            fail("SyntaxException expected");
        } catch (SyntaxException ignore) {
        }
    }
}
