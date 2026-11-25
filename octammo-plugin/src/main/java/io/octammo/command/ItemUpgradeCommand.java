package io.octammo.command;

import io.octammo.OctaMMOPlugin;
import io.octammo.upgrade.StationData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /itemupgrade [station] [path]  (also reachable as /octammo:itemupgrade) */
public final class ItemUpgradeCommand implements TabExecutor {
    private final OctaMMOPlugin plugin;

    public ItemUpgradeCommand(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("octammo.upgrade")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        plugin.upgrades().open(player, args.length > 0 ? args[0] : null, player, args.length > 1 ? args[1] : null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return plugin.upgrades().all().stream().filter(s -> plugin.upgrades().canUse(p, s))
                    .map(StationData::id).filter(n -> n.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            StationData st = plugin.upgrades().get(args[0]);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (st == null) return List.of();
            return st.pathData().stream().map(r -> r.id()).filter(k -> k.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
