package io.octammo.command;

import io.octammo.OctaMMOPlugin;
import io.octammo.enchant.EnchantingGui;
import io.octammo.gui.ItemFixerGui;
import io.octammo.recipe.RecipeGui;
import io.octammo.recipe.RecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /octammo help | reload | upgrade | combine | crafting | fixer | enchanting | list | version */
public final class OctaCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("help", "reload", "upgrade", "combine", "crafting", "fixer", "enchanting", "search", "list", "version", "ver");
    private final OctaMMOPlugin plugin;

    public OctaCommand(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                if (!sender.hasPermission("octammo.use")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.lang().sendList(sender, "commands-help");
            }
            case "version", "ver" -> about(sender);
            case "reload" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                plugin.reloadAll();
                plugin.lang().send(sender, "reloaded-ok", "files", String.valueOf(plugin.upgrades().stationCount()));
            }
            case "list" -> {
                if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return true; }
                if (plugin.upgrades().ids().isEmpty()) plugin.lang().send(sender, "upgrade.list-empty");
                else plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", plugin.upgrades().ids()));
            }
            case "upgrade" -> upgrade(sender, args);
            case "combine" -> openRecipe(sender, RecipeManager.Kind.COMBINE, "octammo.combine", "modules.combine");
            case "crafting" -> openRecipe(sender, RecipeManager.Kind.CRAFT, "octammo.crafting", "modules.crafting");
            case "fixer" -> openFixer(sender);
            case "enchanting", "enchant" -> openEnchanting(sender);
            case "search" -> searchEnchant(sender, args);
            default -> plugin.lang().sendList(sender, "commands-help");
        }
        return true;
    }

    private void about(CommandSender sender) {
        var meta = plugin.getPluginMeta();
        plugin.lang().sendList(sender, "about",
                "authors", joinAnd(meta.getAuthors()),
                "version", meta.getVersion(),
                "description", plugin.lang().format("plugin-description"));
    }

    static String joinAnd(List<String> names) {
        if (names.isEmpty()) return "-";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private void upgrade(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.item-upgrade", true) || !plugin.mmoItems() || !plugin.upgrades().enabled()) {
            plugin.lang().send(sender, "module-disabled"); return;
        }
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("octammo.admin")) { plugin.lang().send(sender, "no-permission"); return; }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { plugin.lang().send(sender, "player-not-found", "player", args[2]); return; }
        } else if (sender instanceof Player p) {
            if (!p.hasPermission("octammo.upgrade")) { plugin.lang().send(sender, "no-permission"); return; }
            target = p;
        } else {
            plugin.lang().send(sender, "player-only"); return;
        }
        String station = args.length >= 2 ? args[1] : null;
        String path = args.length >= 4 ? args[3] : null;
        if (plugin.upgrades().open(sender, station, target, path) && target != sender && station != null) {
            plugin.lang().send(sender, "upgrade.opened-for", "file", station.toLowerCase(Locale.ROOT), "player", target.getName());
        }
    }

    private void openRecipe(CommandSender sender, RecipeManager.Kind kind, String permission, String module) {
        if (!(sender instanceof Player player)) { plugin.lang().send(sender, "player-only"); return; }
        if (!plugin.getConfig().getBoolean(module, true)) { plugin.lang().send(sender, "module-disabled"); return; }
        if (!player.hasPermission(permission)) { plugin.lang().send(sender, "no-permission"); return; }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                && !plugin.getConfig().getBoolean("upgrade.allow-creative", false)) {
            plugin.lang().send(sender, "upgrade.creative-denied");
            return;
        }
        new RecipeGui(plugin, player, kind).open();
    }

    private void searchEnchant(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { plugin.lang().send(sender, "player-only"); return; }
        if (!player.hasPermission("octammo.enchant")) { plugin.lang().send(sender, "no-permission"); return; }
        if (args.length < 2) {
            plugin.lang().send(sender, "enchanting.search-command-usage");
            return;
        }
        String keyword = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (!plugin.enchantingSearch().acceptCommand(player, keyword)) {
            plugin.lang().send(sender, "enchanting.search-not-pending");
        }
    }

    private void openFixer(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.lang().send(sender, "player-only"); return; }
        if (!plugin.getConfig().getBoolean("modules.item-fixer", true) || !plugin.mmoItems()) { plugin.lang().send(sender, "module-disabled"); return; }
        if (!player.hasPermission("octammo.fixer")) { plugin.lang().send(sender, "no-permission"); return; }
        new ItemFixerGui(plugin, player).open();
    }

    private void openEnchanting(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.lang().send(sender, "player-only"); return; }
        if (!plugin.getConfig().getBoolean("modules.custom-enchanting", true)) { plugin.lang().send(sender, "module-disabled"); return; }
        if (!player.hasPermission("octammo.enchant")) { plugin.lang().send(sender, "no-permission"); return; }
        new EnchantingGui(plugin, player).open();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String sub : SUBS) {
                boolean allowed = switch (sub) {
                    case "reload", "list" -> sender.hasPermission("octammo.admin");
                    case "upgrade" -> sender.hasPermission("octammo.upgrade");
                    case "combine" -> sender.hasPermission("octammo.combine");
                    case "crafting" -> sender.hasPermission("octammo.crafting");
                    case "fixer" -> sender.hasPermission("octammo.fixer");
                    case "enchanting", "enchant", "search" -> sender.hasPermission("octammo.enchant");
                    default -> true;
                };
                if (allowed) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.upgrade")) {
            out.addAll(plugin.upgrades().ids());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("upgrade") && sender.hasPermission("octammo.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("upgrade")) {
            var station = plugin.upgrades().get(args[1]);
            if (station != null) station.pathData().forEach(path -> out.add(path.id()));
        }
        return out.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
