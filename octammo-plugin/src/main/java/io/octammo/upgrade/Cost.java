package io.octammo.upgrade;

import io.octammo.core.ItemRef;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** One requirement of a path. Item costs go into rune slots, the others are charged from the player. */
public sealed interface Cost {
    record Money(double amount, String provider) implements Cost {}
    record Levels(int amount) implements Cost {}
    record MmoItem(ItemRef ref) implements Cost {}
    record Vanilla(Material material, int amount) implements Cost {}
    /** A cost registered by another plugin through the API. */
    record Custom(String id, ConfigurationSection config) implements Cost {}
}
