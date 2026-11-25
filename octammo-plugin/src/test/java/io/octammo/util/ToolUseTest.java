package io.octammo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolUseTest {
    @Test
    void hoeTillsOnlyTillableBlocks() {
        assertTrue(ToolUse.tills("NETHERITE_HOE", "GRASS_BLOCK"));
        assertTrue(ToolUse.tills("WOODEN_HOE", "DIRT_PATH"));
        assertFalse(ToolUse.tills("NETHERITE_HOE", "CHEST"));     // opening a chest stays possible
        assertFalse(ToolUse.tills("DIAMOND_SHOVEL", "DIRT"));
    }

    @Test
    void shovelFlattensOnlyFlattenableBlocks() {
        assertTrue(ToolUse.flattens("IRON_SHOVEL", "GRASS_BLOCK"));
        assertTrue(ToolUse.flattens("STONE_SHOVEL", "PODZOL"));
        assertFalse(ToolUse.flattens("IRON_SHOVEL", "DIRT_PATH"));
        assertFalse(ToolUse.flattens("IRON_SHOVEL", "ANVIL"));
        assertFalse(ToolUse.flattens("IRON_HOE", "GRASS_BLOCK"));
    }
}
