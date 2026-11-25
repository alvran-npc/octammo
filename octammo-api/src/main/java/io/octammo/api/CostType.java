package io.octammo.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A custom cost. Use it in an upgrade file with {@code kind: CUSTOM}, {@code id: <id()>} plus your own keys.
 * It is shown in the requirement checklist of the GUI.
 */
public interface CostType {
    String id();

    boolean has(Player player, ConfigurationSection config);

    void take(Player player, ConfigurationSection config);

    /** Called when the path refunds costs after a failure. {@code percent} is 100 for REFUND. */
    default void refund(Player player, ConfigurationSection config, int percent) {}

    /** Text for the checklist. */
    default String describe(ConfigurationSection config) {
        return id();
    }
}
