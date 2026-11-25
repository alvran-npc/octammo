package io.octammo.util;

/** Pure maths of the chance / booster system (no Bukkit), covered by unit tests. */
public final class ChanceMath {
    private ChanceMath() {}

    /** chance = min(maxTotal, base + bonus), kept inside 0-100. */
    public static double total(double base, double bonus, double maxTotal) {
        return Math.max(0, Math.min(Math.min(100.0, maxTotal), base + bonus));
    }

    /** Booster items that count: the ones present, capped by max-items. */
    public static int counted(int have, int maxItems) {
        return Math.max(0, Math.min(have, maxItems));
    }

    /**
     * Spreads {@code toConsume} pieces over stacks in slot order.
     * @return pieces taken from each stack (same length as the input)
     */
    public static int[] distribute(int[] stackAmounts, int toConsume) {
        int[] taken = new int[stackAmounts.length];
        int left = toConsume;
        for (int i = 0; i < stackAmounts.length && left > 0; i++) {
            taken[i] = Math.min(stackAmounts[i], left);
            left -= taken[i];
        }
        return taken;
    }

    /** Number of filled gauge panels: 0 only for a 0% chance, otherwise at least one. */
    public static int gaugePanels(double chance, int panels) {
        if (chance <= 0) return 0;
        return Math.max(1, Math.min(panels, (int) Math.round(chance * panels / 100.0)));
    }

    /** "low" (&lt; 30), "mid" (30 to 70) or "high" (&gt; 70). */
    public static String gaugeZone(double chance) {
        if (chance < 30) return "low";
        if (chance <= 70) return "mid";
        return "high";
    }

    /** Percent of {@code amount}, rounded down. */
    public static int percentOf(int amount, int percent) {
        return (int) Math.floor(amount * Math.max(0, Math.min(100, percent)) / 100.0);
    }
}
