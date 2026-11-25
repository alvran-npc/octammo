package io.octammo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChanceMathTest {
    @Test
    void stackedBoostersAddUp() {
        // base 75, +10 per Lucky Charm: 85, 95, 100 (capped by max-total)
        assertEquals(85.0, ChanceMath.total(75, 10, 100));
        assertEquals(95.0, ChanceMath.total(75, 20, 100));
        assertEquals(100.0, ChanceMath.total(75, 30, 100));
    }

    @Test
    void maxTotalAndBoundsAreRespected() {
        assertEquals(90.0, ChanceMath.total(75, 30, 90));
        assertEquals(100.0, ChanceMath.total(75, 30, 500));
        assertEquals(0.0, ChanceMath.total(0, -10, 100));
    }

    @Test
    void onlyMaxItemsCount() {
        assertEquals(3, ChanceMath.counted(5, 3));
        assertEquals(2, ChanceMath.counted(2, 3));
        assertEquals(0, ChanceMath.counted(-1, 3));
    }

    @Test
    void distributeTakesInSlotOrderAndKeepsTheRest() {
        // 2 Lucky Charm in slot 1, 3 in slot 2, only 4 count: 2 + 2 are consumed, 1 goes back
        assertArrayEquals(new int[]{2, 2}, ChanceMath.distribute(new int[]{2, 3}, 4));
        assertArrayEquals(new int[]{0, 0}, ChanceMath.distribute(new int[]{2, 3}, 0));
        assertArrayEquals(new int[]{2, 3}, ChanceMath.distribute(new int[]{2, 3}, 99));
    }

    @Test
    void gaugeFillsAndColours() {
        assertEquals(0, ChanceMath.gaugePanels(0, 9));
        assertEquals(1, ChanceMath.gaugePanels(1, 9));
        assertEquals(9, ChanceMath.gaugePanels(100, 9));
        assertEquals("low", ChanceMath.gaugeZone(29.9));
        assertEquals("mid", ChanceMath.gaugeZone(30));
        assertEquals("mid", ChanceMath.gaugeZone(70));
        assertEquals("high", ChanceMath.gaugeZone(70.1));
    }

    @Test
    void refundIsRoundedDown() {
        assertEquals(5, ChanceMath.percentOf(10, 50));
        assertEquals(1, ChanceMath.percentOf(3, 50));
        assertEquals(0, ChanceMath.percentOf(1, 50));
        assertEquals(10, ChanceMath.percentOf(10, 500));
    }
}
