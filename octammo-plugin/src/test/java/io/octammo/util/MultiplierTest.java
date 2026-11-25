package io.octammo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiplierTest {
    @Test
    void resultAmountGrowsByTheFilesDefault() {
        assertEquals(1, Multiplier.total(1, 1));
        assertEquals(3, Multiplier.total(1, 3));   // 1+1+1
        assertEquals(6, Multiplier.total(2, 3));   // 2+2+2
    }

    @Test
    void expressionShowsTheSum() {
        assertEquals("1", Multiplier.expression(1, 1));
        assertEquals("1+1", Multiplier.expression(1, 2));
        assertEquals("2+2+2", Multiplier.expression(2, 3));
        assertEquals("2+2+2+2+2+2+2+2+...", Multiplier.expression(2, 12));
    }

    @Test
    void slotsNeededRoundsUp() {
        assertEquals(1, Multiplier.slotsNeeded(64, 64));
        assertEquals(2, Multiplier.slotsNeeded(65, 64));
        assertEquals(3, Multiplier.slotsNeeded(3, 1));
    }

    @Test
    void maxFittingFindsTheLargestThatFits() {
        // 8 rune slots, a cost of 3 non-stackable pieces per step: 2 steps fit (6 slots), 3 do not (9)
        assertEquals(2, Multiplier.maxFitting(16, m -> Multiplier.slotsNeeded(3 * m, 1) <= 8));
        assertEquals(16, Multiplier.maxFitting(16, m -> true));
        assertEquals(1, Multiplier.maxFitting(16, m -> false));
        assertEquals(1, Multiplier.maxFitting(0, m -> true));
    }
}
