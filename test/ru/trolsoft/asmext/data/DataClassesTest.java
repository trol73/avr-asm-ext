package ru.trolsoft.asmext.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataClassesTest {

    @Test
    void testAlias() {
        Alias a1 = new Alias("a1", "r1");
        Alias a2 = new Alias("a2", "r2");
        Alias a11 = new Alias("a1", "r1");

        assertEquals(a1.toString(), "alias (a1 -> r1)");

        assertEquals(a1, a11);
        assertNotEquals(a1, a2);
        assertNotEquals(a1, null);
        assertNotEquals(a1, new Object());
        assertEquals(a1.hashCode(), a11.hashCode());
        assertNotEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void testNamedPair() {
        NamedPair n1 = new NamedPair("n1", "val-1");
        NamedPair n2 = new NamedPair("n2", "v2");
        NamedPair n11 = new NamedPair("n1", "val-1");
        NamedPair n12 = new NamedPair("n1", "v-1");

        assertEquals(n1.toString(), "(n1 = val-1)");

        assertEquals(n1, n11);
        assertNotEquals(n1, n2);
        assertNotEquals(n1, null);
        assertNotEquals(n1, new Object());
        assertNotEquals(n1, n12);

        assertEquals(n1.hashCode(), n11.hashCode());
        assertNotEquals(n1.hashCode(), n2.hashCode());
        assertNotEquals(n1.hashCode(), n12.hashCode());
    }
}
