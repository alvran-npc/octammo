package io.octammo.hook;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.PlayerStats;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * %octammo_upgrades_done%, %octammo_upgrades_success%, %octammo_upgrades_failed%,
 * %octammo_can_upgrade_&lt;id&gt;%
 */
public final class PapiHook extends PlaceholderExpansion {
    private final OctaMMOPlugin plugin;

    public PapiHook(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    /** Resolves %placeholders% of other plugins in a text. */
    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override public @NotNull String getIdentifier() { return "octammo"; }
    @Override public @NotNull String getAuthor() { return "OctaMMO"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        PlayerStats stats = plugin.stats();
        int ok = stats.get(player, PlayerStats.UPGRADE_SUCCESS);
        int bad = stats.get(player, PlayerStats.UPGRADE_FAIL);
        String p = params.toLowerCase();
        if (p.startsWith("can_upgrade_")) {
            return String.valueOf(plugin.upgrades().canOpen(player, p.substring("can_upgrade_".length())));
        }
        return switch (p) {
            case "upgrades_success" -> String.valueOf(ok);
            case "upgrades_failed" -> String.valueOf(bad);
            case "upgrades_done" -> String.valueOf(ok + bad);
            default -> null;
        };
    }
}
