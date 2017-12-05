package ru.trolsoft.asmext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.trolsoft.asmext.ParserUtils.*;


class ParserUtilsTest {

    private List<String> lst(String... args) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, args);
        return result;
    }

    @Test
    void testNameValidators() {
        assertTrue(isValidName("abC_"));
        assertTrue(isValidName("_abc"));
        assertTrue(isValidName("ab_c1"));
        assertFalse(isValidName("1abc"));
        assertFalse(isValidName("12_"));
        assertFalse(isValidName(""));
        assertFalse(isValidName(null));

        assertTrue(isRegister("r0"));
        assertTrue(isRegister("r31"));
        assertTrue(isRegister("R5"));
        assertTrue(isRegister("zl"));
        assertTrue(isRegister("YH"));
        assertFalse(isRegister("r32"));
        assertFalse(isRegister("r"));

        assertTrue(isInstruction("mov"));
        assertFalse(isInstruction("movs"));

        assertTrue(isComment("// comment"));
        assertTrue(isComment("; comment"));
        assertFalse(isComment("123"));
        assertFalse(isComment("abc"));
    }

    @Test
    void testValueParser() {
        assertTrue(isNumber("123"));
        assertTrue(isNumber("-123"));
        assertTrue(isNumber("0"));
        assertTrue(isNumber("0b101"));
        assertTrue(isNumber("0xab"));
        assertTrue(isNumber("-0b101"));
        assertTrue(isNumber("-0xab"));
        assertFalse(isNumber("ab"));
        assertFalse(isNumber("0xG"));
        assertFalse(isNumber("0b020"));
        assertFalse(isNumber("-0b020"));
        assertFalse(isNumber("-0b101b"));

        assertTrue(isConstExpression("1+2"));
        assertTrue(isConstExpression("(1 + 2)"));
        assertTrue(isConstExpression("1 +\t2"));
        assertTrue(isConstExpression("2*2+4"));
        assertFalse(isConstExpression("2*x"));
        assertFalse(isConstExpression("(2*2"));
        assertFalse(isConstExpression(")2*2("));
        assertFalse(isConstExpression("2*2("));
        assertFalse(isConstExpression("2*2)"));
        assertFalse(isConstExpression("(2+2)("));
        assertFalse(isConstExpression("2+"));
        assertFalse(isConstExpression("(2+)"));

        assertTrue(isConstExpression("(0xABC)"));
        assertTrue(isConstExpression("0b10100110 + 123"));
        assertTrue(isConstExpression("(0xABC)-1"));
    }

    @Test
    void testVars() {
        assertTrue(getTypeSize("byte") == 1);
        assertTrue(getTypeSize("word") == 2);
        assertTrue(getTypeSize("dword") == 4);
        assertTrue(getTypeSize("unknown") == -1);
    }

    @Test
    void testTokenUtils() {
        List<String> tokens;

        tokens = lst("1", "  ", "+", "\t", "5");
        removeEmptyTokens(tokens);
        assertEquals(tokens, lst("1", "+", "5"));

        tokens = lst("1", "+", "5");
        mergeTokens(tokens);
        assertTrue(tokens.size() == 1);
        assertEquals(tokens.get(0), "1+5");

        tokens = lst("1", "+", "5", "-", "  ", "r11");
        mergeTokens(tokens);
        assertTrue(tokens.size() == 3);
        assertEquals(tokens.get(0), "1+5");
        assertEquals(tokens.get(1), "-");
        assertEquals(tokens.get(2), "r11");

        tokens = lst("(", "1", "\t", "+", "5", ")");
        mergeTokens(tokens);
        assertTrue(tokens.size() == 1);
        assertEquals(tokens.get(0), "(1+5)");

        tokens = lst("(", "1", "\t", "+", "5", ")", " ", "*", "2");
        mergeTokens(tokens);
        assertTrue(tokens.size() == 1);
        assertEquals(tokens.get(0), "(1+5)*2");

        tokens = lst("2", "*", "(", "5", " ", "-", "1", ")", " ", "*", "2");
        mergeTokens(tokens);
        assertTrue(tokens.size() == 1);
        assertEquals(tokens.get(0), "2*(5-1)*2");


    }
}
