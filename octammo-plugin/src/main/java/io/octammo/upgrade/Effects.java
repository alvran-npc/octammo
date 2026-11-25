package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.EffectType;
import io.octammo.core.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * on-success / on-fail-effects of a path: commands ("console: ..." or "player: ..."), sound, title,
 * a server broadcast and effects registered through the API.
 */
public record Effects(List<String> commands, String sound, String title, boolean broadcast, List<ConfigurationSection> custom) {
    public static final Effects NONE = new Effects(List.of(), "", "", false, List.of());

    public static Effects parse(ConfigurationSection s) {
        if (s == null) return NONE;
        List<ConfigurationSection> custom = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("effects")) {
            ConfigurationSection cs = new MemoryConfiguration().createSection("effect");
            for (Map.Entry<?, ?> e : map.entrySet()) cs.set(String.valueOf(e.getKey()), e.getValue());
            custom.add(cs);
        }
        return new Effects(List.copyOf(s.getStringList("commands")), s.getString("sound", ""),
                s.getString("title", ""), s.getBoolean("broadcast", false), custom);
    }

    /** Runs everything for the player. {@code ph} fills {output-name}, {path}, {station}, {player}. */
    public void run(OctaMMOPlugin plugin, Player player, Map<String, String> ph) {
        for (String raw : commands) {
            String line = raw;
            boolean asPlayer = false;
            if (line.regionMatches(true, 0, "console:", 0, 8)) line = line.substring(8);
            else if (line.regionMatches(true, 0, "player:", 0, 7)) { line = line.substring(7); asPlayer = true; }
            line = line.trim();
            if (line.startsWith("/")) line = line.substring(1);
            line = plugin.lang().expand(line, player, ph).replace("{player}", player.getName());
            if (asPlayer) Bukkit.dispatchCommand(player, line);
            else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
        Sounds.playRaw(player, sound, 1f, 1f);
        if (broadcast) {
            Bukkit.broadcast(plugin.lang().render(plugin.lang().raw("upgrade.broadcast"), player, ph));
        }
        for (ConfigurationSection cs : custom) {
            EffectType type = plugin.registry().effect(cs.getString("type", ""));
            if (type != null) {
                try {
                    type.run(player, cs);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Effect '" + type.id() + "' threw: " + e.getMessage());
                }
            }
        }
    }
}
