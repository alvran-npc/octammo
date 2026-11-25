package io.octammo.core;

import io.octammo.OctaMMOPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Per-player counters stored in the player's persistent data container. */
public final class PlayerStats {
    public static final String UPGRADE_SUCCESS = "upgrade_success";
    public static final String UPGRADE_FAIL = "upgrade_fail";

    private final OctaMMOPlugin plugin;

    public PlayerStats(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public int get(Player p, String key) {
        Integer v = p.getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public void add(Player p, String key, int delta) {
        p.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.INTEGER, get(p, key) + delta);
    }
}
