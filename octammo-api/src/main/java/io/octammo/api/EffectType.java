package io.octammo.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A custom effect for {@code on-success} / {@code on-fail-effects}:
 * <pre>
 * on-success:
 *   effects:
 *     - { type: my-effect, power: 3 }
 * </pre>
 */
public interface EffectType {
    String id();

    void run(Player player, ConfigurationSection config);
}
