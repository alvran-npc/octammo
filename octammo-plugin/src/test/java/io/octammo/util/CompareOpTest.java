package io.octammo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareOpTest {
    @Test
    void numbersCompareAsNumbers() {
        assertTrue(CompareOp.compare("30", ">=", "30"));
        assertTrue(CompareOp.compare("31", ">", "30"));
        assertFalse(CompareOp.compare("29.5", ">=", "30"));
        assertTrue(CompareOp.compare("10", "<", "9.5e2"));
        assertTrue(CompareOp.compare("5", "!=", "6"));
    }

    @Test
    void textOnlySupportsEquality() {
        assertTrue(CompareOp.compare("Gold", "=", "gold"));
        assertTrue(CompareOp.compare("a", "!=", "b"));
        assertFalse(CompareOp.compare("a", ">", "b"));
    }

    @Test
    void operatorCheck() {
        assertTrue(CompareOp.isOperator(">="));
        assertFalse(CompareOp.isOperator("=>"));
    }
}
