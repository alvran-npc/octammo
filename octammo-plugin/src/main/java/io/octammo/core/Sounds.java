package io.octammo.core;

import io.octammo.OctaMMOPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Sounds {
    private static final Map<String, Optional<Sound>> CACHE = new ConcurrentHashMap<>();

    private Sounds() {}

    /** Accepts "entity.player.levelup", "minecraft:entity.player.levelup" or "ENTITY_PLAYER_LEVELUP". */
    public static Sound resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return CACHE.computeIfAbsent(raw.trim().toLowerCase(Locale.ROOT), k -> {
            if (k.contains(":") || k.contains(".")) {
                NamespacedKey nk = NamespacedKey.fromString(k);
                return Optional.ofNullable(nk == null ? null : Registry.SOUNDS.get(nk));
            }
            for (Sound s : Registry.SOUNDS) {
                if (s.getKey().getKey().replace('.', '_').equals(k)) return Optional.of(s);
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** Plays the sound configured under sounds.<name>, if any. */
    public static void play(OctaMMOPlugin plugin, Player player, String name) {
        playRaw(player, plugin.getConfig().getString("sounds." + name, ""), 1f, 1f);
    }

    public static void playRaw(Player player, String raw, float volume, float pitch) {
        Sound sound = resolve(raw);
        if (sound != null) player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
