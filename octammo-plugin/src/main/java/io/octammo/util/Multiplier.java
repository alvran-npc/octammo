package io.octammo.util;

import java.util.function.IntPredicate;

/** Maths of the "result amount" button (pure Java, covered by unit tests). */
public final class Multiplier {
    private Multiplier() {}

    /** Result amount: the file's default amount times the multiplier (1+1+1 = 3, 2+2+2 = 6). */
    public static int total(int step, int multiplier) {
        return step * multiplier;
    }

    /** "2+2+2" for step 2 and multiplier 3; long expressions are cut after 8 terms. */
    public static String expression(int step, int multiplier) {
        int shown = Math.min(Math.max(1, multiplier), 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append('+');
            sb.append(step);
        }
        if (multiplier > shown) sb.append("+...");
        return sb.toString();
    }

    /** Number of slots needed for {@code amount} pieces when one slot holds {@code perSlot}. */
    public static int slotsNeeded(int amount, int perSlot) {
        return (amount + perSlot - 1) / Math.max(1, perSlot);
    }

    /** Highest multiplier in 1..cap for which {@code fits} is true (1 when none is). */
    public static int maxFitting(int cap, IntPredicate fits) {
        for (int m = Math.max(1, cap); m > 1; m--) {
            if (fits.test(m)) return m;
        }
        return 1;
    }
}
