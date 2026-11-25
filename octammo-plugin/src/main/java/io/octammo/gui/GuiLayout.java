package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A validated layout from guis/<name>.yml. Layouts are pure data: slots, theme and item definitions.
 * A broken file is reported (file, key, value) and replaced by the built-in layout.
 */
public final class GuiLayout {
    private final String title;
    private final Material border;
    private final Material filler;
    private final List<Material> shimmer;
    private final Map<String, List<Integer>> slots;
    private final ConfigurationSection items;
    private final Map<String, ItemDef> itemCache = new HashMap<>();

    private GuiLayout(String title, Material border, Material filler, List<Material> shimmer,
                      Map<String, List<Integer>> slots, ConfigurationSection items) {
        this.title = title;
        this.border = border;
        this.filler = filler;
        this.shimmer = shimmer;
        this.slots = slots;
        this.items = items;
    }

    public static GuiLayout load(OctaMMOPlugin plugin, String name, String defaultName, Set<String> singles, Set<String> lists) {
        List<String> errors = new ArrayList<>();
        GuiLayout layout = null;
        File file = new File(plugin.getDataFolder(), "guis/" + name + ".yml");
        if (file.exists()) {
            layout = parse(YamlConfiguration.loadConfiguration(file), singles, lists, errors);
        } else {
            errors.add("file not found");
        }
        if (!errors.isEmpty()) {
            for (String e : errors) plugin.getLogger().warning("[guis/" + name + ".yml] " + e);
            plugin.getLogger().warning("[guis/" + name + ".yml] using the built-in layout '" + defaultName + "' instead.");
            List<String> builtInErrors = new ArrayList<>();
            layout = null;
            try (InputStream in = plugin.getResource("guis/" + defaultName + ".yml")) {
                if (in != null) {
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        layout = parse(YamlConfiguration.loadConfiguration(r), singles, lists, builtInErrors);
                    }
                }
            } catch (Exception e) {
                builtInErrors.add(e.getMessage());
            }
            if (layout == null || !builtInErrors.isEmpty()) {
                throw new IllegalStateException("Built-in GUI layout '" + defaultName + "' is invalid: " + builtInErrors);
            }
        }
        return layout;
    }

    private static GuiLayout parse(YamlConfiguration cfg, Set<String> singles, Set<String> lists, List<String> errors) {
        int rows = cfg.getInt("rows", 6);
        if (rows != 6) errors.add("rows: must be 6, got " + rows);

        Material border = material(cfg.getString("theme.border", "BLACK_STAINED_GLASS_PANE"), "theme.border", Material.BLACK_STAINED_GLASS_PANE, errors);
        Material filler = material(cfg.getString("theme.filler", "GRAY_STAINED_GLASS_PANE"), "theme.filler", Material.GRAY_STAINED_GLASS_PANE, errors);
        List<Material> shimmer = new ArrayList<>();
        for (String s : cfg.getStringList("theme.shimmer-colors")) {
            Material m = material(s, "theme.shimmer-colors", null, errors);
            if (m != null) shimmer.add(m);
        }

        Map<String, List<Integer>> slots = new LinkedHashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("slots");
        if (sec == null) {
            errors.add("slots: section is missing");
        } else {
            for (String key : sec.getKeys(false)) {
                Object raw = sec.get(key);
                List<Integer> values = new ArrayList<>();
                if (raw instanceof Number n) {
                    values.add(n.intValue());
                } else if (raw instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Number n) values.add(n.intValue());
                        else errors.add("slots." + key + ": '" + o + "' is not a number");
                    }
                } else {
                    errors.add("slots." + key + ": '" + raw + "' must be a number or a list of numbers");
                }
                for (int v : values) {
                    if (v < 0 || v > 53) errors.add("slots." + key + ": " + v + " is out of range (0-53)");
                }
                slots.put(key, values);
            }
            for (String k : singles) {
                if (!slots.containsKey(k) || slots.get(k).size() != 1) errors.add("slots." + k + ": needs exactly one slot number");
            }
            for (String k : lists) {
                if (!slots.containsKey(k) || slots.get(k).isEmpty()) errors.add("slots." + k + ": needs a list of slot numbers");
            }
            Set<Integer> seen = new HashSet<>();
            for (Map.Entry<String, List<Integer>> e : slots.entrySet()) {
                for (int v : e.getValue()) {
                    if (!seen.add(v)) errors.add("slots." + e.getKey() + ": slot " + v + " is used more than once");
                }
            }
        }
        return new GuiLayout(cfg.getString("title", "&8Altar Forge"), border, filler, shimmer, slots,
                cfg.getConfigurationSection("items"));
    }

    private static Material material(String name, String key, Material fallback, List<String> errors) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null || m.isAir()) {
            errors.add(key + ": unknown material '" + name + "'");
            return fallback;
        }
        return m;
    }

    public String title() { return title; }
    public Material border() { return border; }
    public Material filler() { return filler; }
    public List<Material> shimmerColors() { return shimmer; }

    public boolean hasSlot(String key) { return slots.containsKey(key) && !slots.get(key).isEmpty(); }
    public int slot(String key) { return slots.get(key).get(0); }
    public List<Integer> slots(String key) { return slots.getOrDefault(key, List.of()); }

    /** Every slot number that has a function (everything else is filler / border). */
    public Set<Integer> functionalSlots() {
        Set<Integer> all = new HashSet<>();
        for (List<Integer> l : slots.values()) all.addAll(l);
        return all;
    }

    /** A plain material written as a string under items:, e.g. items.gauge.low. */
    public Material material(String path, Material fallback) {
        String s = items == null ? null : items.getString(path);
        Material m = s == null ? null : Material.matchMaterial(s);
        return m == null || m.isAir() ? fallback : m;
    }

    public ItemDef item(String path, ItemDef fallback) {
        return itemCache.computeIfAbsent(path, p -> ItemDef.parse(items == null ? null : items.getConfigurationSection(p), fallback));
    }
}
