package io.octammo.util;

import java.util.Set;

/** Which blocks a hoe turns into farmland and a shovel into a dirt path (plain names, no Bukkit, unit tested). */
public final class ToolUse {
    private static final Set<String> TILLABLE = Set.of("GRASS_BLOCK", "DIRT_PATH", "DIRT", "COARSE_DIRT", "ROOTED_DIRT");
    private static final Set<String> FLATTENABLE = Set.of("GRASS_BLOCK", "DIRT", "PODZOL", "COARSE_DIRT", "MYCELIUM", "ROOTED_DIRT");

    private ToolUse() {}

    /** A hoe (any material ending in _HOE) used on a block it would till. */
    public static boolean tills(String itemMaterial, String blockMaterial) {
        return itemMaterial.endsWith("_HOE") && TILLABLE.contains(blockMaterial);
    }

    /** A shovel (any material ending in _SHOVEL) used on a block it would flatten into a path. */
    public static boolean flattens(String itemMaterial, String blockMaterial) {
        return itemMaterial.endsWith("_SHOVEL") && FLATTENABLE.contains(blockMaterial);
    }
}
