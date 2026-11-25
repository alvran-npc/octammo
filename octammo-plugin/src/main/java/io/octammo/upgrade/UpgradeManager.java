package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.event.UpgradeStationOpenEvent;
import io.octammo.core.ItemRef;
import io.octammo.gui.ForgeGui;
import io.octammo.gui.OctaGui;
import io.octammo.gui.PathSelectGui;
import io.octammo.gui.StationListGui;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Loads upgrades/*.yml (stations with paths), checks access and opens the GUIs. */
public final class UpgradeManager {
    private final OctaMMOPlugin plugin;
    private final Map<String, StationData> stations = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private boolean enabled;

    public UpgradeManager(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        stations.clear();
        enabled = plugin.getConfig().getBoolean("modules.item-upgrade", true);

        File dir = new File(plugin.getDataFolder(), "upgrades");
        if (!dir.exists()) {
            dir.mkdirs();
            plugin.saveResource("upgrades/example-sword.yml", false);
        }
        File[] list = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list == null) return;
        Arrays.sort(list, Comparator.comparing(File::getName));

        for (File f : list) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                stations.put(id, parseStation(id, YamlConfiguration.loadConfiguration(f), f.getName()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[upgrades/" + f.getName() + "] skipped: " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + stations.size() + " upgrade station(s).");
    }

    private StationData parseStation(String id, YamlConfiguration cfg, String fileName) {
        List<PathData> paths = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection("paths");
        if (sec == null) throw new IllegalArgumentException("'paths:' section is missing");
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                paths.add(parsePath(id, key.toLowerCase(Locale.ROOT), s));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[upgrades/" + fileName + "] path '" + key + "' skipped: " + e.getMessage());
            }
        }
        return new StationData(id, cfg.getString("display-name", id), cfg.getString("permission", ""),
                cfg.getString("gui", "default-upgrade"), Math.max(0, cfg.getInt("cooldown", 0)),
                lower(cfg.getStringList("worlds-blacklist")), lower(cfg.getStringList("worldguard-regions-blacklist")),
                cfg.contains("loading.duration-ticks") ? Integer.valueOf(cfg.getInt("loading.duration-ticks")) : null,
                List.copyOf(paths));
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    private static ConfigurationSection asSection(Map<?, ?> map) {
        ConfigurationSection cs = new MemoryConfiguration().createSection("e");
        for (Map.Entry<?, ?> e : map.entrySet()) cs.set(String.valueOf(e.getKey()), e.getValue());
        return cs;
    }

    private static ItemRef itemRef(ConfigurationSection s, String what) {
        String type = s.getString("type");
        String id = s.getString("id");
        if (type == null || id == null) throw new IllegalArgumentException(what + " needs 'type' and 'id'");
        return new ItemRef(type, id, s.getInt("amount", 1));
    }

    /** MMOITEMS / VANILLA item described by a section (used by costs, boosters, protection). */
    private static Cost itemCost(ConfigurationSection s, String what) {
        String kind = s.getString("kind", "MMOITEMS").toUpperCase(Locale.ROOT);
        if (kind.equals("VANILLA")) {
            Material m = Material.matchMaterial(s.getString("material", ""));
            if (m == null || m.isAir()) throw new IllegalArgumentException(what + ": unknown material '" + s.getString("material") + "'");
            return new Cost.Vanilla(m, Math.max(1, s.getInt("amount", 1)));
        }
        if (kind.equals("MMOITEMS")) return new Cost.MmoItem(itemRef(s, what));
        throw new IllegalArgumentException(what + ": kind must be MMOITEMS or VANILLA, got '" + kind + "'");
    }

    private PathData parsePath(String stationId, String id, ConfigurationSection s) {
        List<Requirement> requirements = new ArrayList<>();
        for (String line : s.getStringList("requirements")) requirements.add(Requirement.parse(plugin, line));

        ConfigurationSection in = s.getConfigurationSection("input");
        if (in == null) throw new IllegalArgumentException("'input' is missing");
        ItemRef input = new ItemRef(itemRef(in, "input").type(), itemRef(in, "input").id(), 1);

        List<Cost> costs = new ArrayList<>();
        for (Map<?, ?> map : s.getMapList("costs")) {
            ConfigurationSection c = asSection(map);
            String kind = c.getString("kind", "").toUpperCase(Locale.ROOT);
            switch (kind) {
                case "MMOITEMS", "VANILLA" -> costs.add(itemCost(c, "cost"));
                case "MONEY" -> costs.add(new Cost.Money(Math.max(0, c.getDouble("amount")), c.getString("provider", "vault")));
                case "LEVELS" -> costs.add(new Cost.Levels(Math.max(0, c.getInt("amount"))));
                case "CUSTOM", "COMMAND_CHECK" -> {
                    if (c.getString("id") == null) throw new IllegalArgumentException("a CUSTOM cost needs 'id'");
                    costs.add(new Cost.Custom(c.getString("id"), c));
                }
                default -> throw new IllegalArgumentException("unknown cost kind '" + kind + "' (MMOITEMS, VANILLA, MONEY, LEVELS, CUSTOM)");
            }
        }

        ConfigurationSection out = s.getConfigurationSection("output");
        if (out == null) throw new IllegalArgumentException("'output' is missing");
        Defs.OutputMode mode = "WEIGHTED".equalsIgnoreCase(out.getString("mode", "SINGLE")) ? Defs.OutputMode.WEIGHTED : Defs.OutputMode.SINGLE;
        List<Defs.ResultDef> results = new ArrayList<>();
        for (Map<?, ?> map : out.getMapList("results")) {
            ConfigurationSection r = asSection(map);
            List<String> preserve = r.getStringList("preserve");
            results.add(new Defs.ResultDef(itemRef(r, "result"), Math.max(1, r.getInt("weight", 100)), List.copyOf(preserve)));
        }
        if (results.isEmpty()) throw new IllegalArgumentException("'output.results' is empty");
        if (mode == Defs.OutputMode.SINGLE && results.size() > 1) results = List.of(results.get(0));

        ConfigurationSection ch = s.getConfigurationSection("chance");
        List<Defs.BoosterDef> boosters = new ArrayList<>();
        if (ch != null) {
            for (Map<?, ?> map : ch.getMapList("boosters")) {
                ConfigurationSection b = asSection(map);
                boosters.add(new Defs.BoosterDef(itemCost(b, "booster"), b.getDouble("add", 5), Math.max(1, b.getInt("max-items", 1))));
            }
        }
        int boosterSlots = ch == null ? 0 : Math.max(0, Math.min(3, ch.getInt("booster-slots", boosters.isEmpty() ? 0 : 3)));
        if (boosters.isEmpty()) boosterSlots = 0;
        Defs.BoosterConsume consume = Defs.BoosterConsume.ALWAYS;
        if (ch != null) {
            try {
                consume = Defs.BoosterConsume.valueOf(ch.getString("booster-consume", "ALWAYS").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("chance.booster-consume must be ALWAYS, SUCCESS_ONLY or NEVER");
            }
        }
        Defs.ChanceDef chance = new Defs.ChanceDef(
                ch == null ? 100 : Math.max(0, Math.min(100, ch.getDouble("base", 100))),
                ch == null ? 100 : Math.max(0, Math.min(100, ch.getDouble("max-total", 100))),
                boosterSlots, consume, List.copyOf(boosters));

        ConfigurationSection of = s.getConfigurationSection("on-fail");
        Defs.InputFail inputFail = Defs.InputFail.KEEP;
        Defs.CostFail costFail = Defs.CostFail.CONSUME;
        Defs.ProtectionDef protection = null;
        ItemRef downgrade = null;
        int refundPercent = 50;
        if (of != null) {
            try {
                inputFail = Defs.InputFail.valueOf(of.getString("input", "KEEP").toUpperCase(Locale.ROOT));
                costFail = Defs.CostFail.valueOf(of.getString("costs", "CONSUME").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("on-fail.input: KEEP|CONSUME|DOWNGRADE|BREAK, on-fail.costs: CONSUME|REFUND|PARTIAL");
            }
            refundPercent = Math.max(0, Math.min(100, of.getInt("refund-percent", 50)));
            ConfigurationSection pr = of.getConfigurationSection("protection");
            if (pr != null) protection = new Defs.ProtectionDef(itemCost(pr, "protection"), pr.getBoolean("keeps-input", true));
            ConfigurationSection dg = of.getConfigurationSection("downgrade");
            if (dg != null) downgrade = new ItemRef(itemRef(dg, "downgrade").type(), itemRef(dg, "downgrade").id(), 1);
        }
        if (inputFail == Defs.InputFail.DOWNGRADE && downgrade == null) {
            plugin.getLogger().warning("Path '" + stationId + "/" + id + "': on-fail.input DOWNGRADE needs on-fail.downgrade {type,id}; using KEEP.");
            inputFail = Defs.InputFail.KEEP;
        }

        return new PathData(stationId, id, s.getString("display-name", id), List.copyOf(s.getStringList("description")),
                List.copyOf(requirements), input, in.getBoolean("consume", true), List.copyOf(costs), mode, List.copyOf(results),
                chance, new Defs.FailDef(inputFail, costFail, refundPercent, protection, downgrade),
                Effects.parse(s.getConfigurationSection("on-success")), Effects.parse(s.getConfigurationSection("on-fail-effects")),
                s.contains("loading.duration-ticks") ? Integer.valueOf(s.getInt("loading.duration-ticks")) : null,
                out.getBoolean("scale-costs", true),
                out.contains("max-multiplier") ? Integer.valueOf(Math.max(1, out.getInt("max-multiplier"))) : null);
    }

    // ------------------------------------------------------------------ access

    public boolean enabled() { return enabled; }
    public int stationCount() { return stations.size(); }
    public Set<String> ids() { return Collections.unmodifiableSet(stations.keySet()); }
    public java.util.Collection<StationData> all() { return Collections.unmodifiableCollection(stations.values()); }
    public StationData get(String id) { return id == null ? null : stations.get(id.toLowerCase(Locale.ROOT)); }

    public boolean canUse(Player p, StationData st) {
        if (!p.hasPermission("octammo.upgrade")) return false;
        if (!st.permission().isBlank() && !p.hasPermission(st.permission())) return false;
        return p.hasPermission("octammo.upgrade." + st.id()) || !plugin.getConfig().getBoolean("upgrade.require-station-permission", false);
    }

    /** For %octammo_can_upgrade_<id>%. */
    public boolean canOpen(Player p, String id) {
        StationData st = get(id);
        return enabled && st != null && canUse(p, st) && cooldownLeft(p, st) == 0 && deniedHere(p, st) == null;
    }

    /** Lang key of the reason why the player may not upgrade here, or null. */
    public String deniedHere(Player p, StationData st) {
        if (st.worldsBlacklist().contains(p.getWorld().getName().toLowerCase(Locale.ROOT))) return "upgrade.world-denied";
        if (plugin.worldGuard() != null) {
            if (!st.regionsBlacklist().isEmpty()) {
                Set<String> here = plugin.worldGuard().regionsAt(p.getLocation());
                for (String r : st.regionsBlacklist()) if (here.contains(r)) return "upgrade.region-denied";
            }
            if (!plugin.worldGuard().flagAllows(p)) return "upgrade.region-denied";
        }
        return null;
    }

    // ------------------------------------------------------------------ cooldown (seconds, per station and global)

    public long cooldownLeft(Player p, StationData st) {
        Map<String, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return 0;
        long now = System.currentTimeMillis();
        long until = Math.max(map.getOrDefault(st.id(), 0L), map.getOrDefault("*", 0L));
        return until <= now ? 0 : (until - now + 999) / 1000;
    }

    public void startCooldown(Player p, StationData st) {
        long now = System.currentTimeMillis();
        Map<String, Long> map = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        if (st.cooldown() > 0) map.put(st.id(), now + st.cooldown() * 1000L);
        int global = plugin.getConfig().getInt("upgrade.global-cooldown", 0);
        if (global > 0) map.put("*", now + global * 1000L);
    }

    public void forget(UUID id) {
        cooldowns.remove(id);
    }

    // ------------------------------------------------------------------ opening

    /** Opens the station list (no id), the path menu / forge of a station. {@code sender} gets the error messages. */
    public boolean open(CommandSender sender, String stationId, Player target, String pathId) {
        if (!enabled) { plugin.lang().send(sender, "module-disabled"); return false; }
        if (plugin.floodgate() != null && plugin.floodgate().isBedrock(target.getUniqueId())) {
            plugin.lang().send(target, "upgrade.bedrock-unsupported");
            return false;
        }
        if (target.getGameMode() == GameMode.CREATIVE && !plugin.getConfig().getBoolean("upgrade.allow-creative", false)) {
            plugin.lang().send(sender, "upgrade.creative-denied");
            return false;
        }
        if (plugin.loading().isUpgrading(target)) { plugin.lang().send(sender, "upgrade.busy"); return false; }

        if (stationId == null) {
            new StationListGui(plugin, target).open();
            return true;
        }
        StationData st = get(stationId);
        if (st == null) {
            plugin.lang().send(sender, "upgrade.file-not-found", "file", stationId);
            plugin.lang().send(sender, "upgrade.available", "files", String.join(", ", stations.keySet()));
            return false;
        }
        if (sender == target && !canUse(target, st)) { plugin.lang().send(sender, "no-permission"); return false; }
        String denied = deniedHere(target, st);
        if (denied != null) { plugin.lang().send(sender, denied); return false; }
        if (st.pathData().isEmpty()) { plugin.lang().send(sender, "upgrade.no-paths"); return false; }

        PathData path = null;
        if (pathId != null) {
            path = st.path(pathId);
            if (path == null) { plugin.lang().send(sender, "upgrade.path-not-found", "path", pathId); return false; }
        } else if (st.pathData().size() == 1) {
            path = st.pathData().get(0);
        }

        UpgradeStationOpenEvent event = new UpgradeStationOpenEvent(target, st, path);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        if (path == null) {
            new PathSelectGui(plugin, st, target).open();
            return true;
        }
        return openForge(sender, st, path, target);
    }

    public boolean openForge(CommandSender sender, StationData st, PathData path, Player target) {
        ForgeGui gui = ForgeGui.create(plugin, st, path, target);
        if (gui == null) {
            plugin.lang().send(sender, "upgrade.too-many-runes", "path", path.id());
            return false;
        }
        gui.open();
        return true;
    }

    /** After a loading: the same forge again, empty. */
    public void reopen(Player player, StationData st, PathData path) {
        if (!player.isOnline() || plugin.loading().isUpgrading(player)) return;
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof OctaGui) return;
        UpgradeStationOpenEvent event = new UpgradeStationOpenEvent(player, st, path);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        openForge(player, st, path, player);
    }

    /** Closes every open OctaMMO GUI (the close handler hands the items back). */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof OctaGui) p.closeInventory();
        }
    }
}
