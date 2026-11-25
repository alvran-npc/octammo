package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** External per-feature GUI settings stored under plugins/OctaMMO/guis/. */
public final class FeatureGuiConfig {
    private final YamlConfiguration yaml;

    private FeatureGuiConfig(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static FeatureGuiConfig load(OctaMMOPlugin plugin, String file, String resource) {
        File folder = new File(plugin.getDataFolder(), "guis");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the GUI configuration folder.");
        }
        File target = new File(folder, file);
        if (!target.exists()) {
            try {
                plugin.saveResource("guis/" + resource, false);
            } catch (IllegalArgumentException ignored) {
                try {
                    if (!target.createNewFile()) plugin.getLogger().warning("Could not create " + target.getName());
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not create " + target.getName() + ": " + ex.getMessage());
                }
            }
        }

        YamlConfiguration user = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration defaults = loadDefaults(plugin, resource);
        if (defaults != null) mergeMissingKeys(plugin, target, user, defaults);
        return new FeatureGuiConfig(user);
    }

    private static YamlConfiguration loadDefaults(OctaMMOPlugin plugin, String resource) {
        try (InputStream input = plugin.getResource("guis/" + resource)) {
            if (input == null) return null;
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read default GUI resource " + resource + ": " + ex.getMessage());
            return null;
        }
    }

    private static void mergeMissingKeys(OctaMMOPlugin plugin, File target,
                                         YamlConfiguration user, YamlConfiguration defaults) {
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || user.contains(key)) continue;
            user.set(key, defaults.get(key));
            added++;
        }
        if (added == 0) return;
        try {
            File backups = new File(plugin.getDataFolder(), "backups/guis");
            if (!backups.exists() && !backups.mkdirs()) {
                plugin.getLogger().warning("Could not create the GUI backup folder; config migration was skipped.");
                return;
            }
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            File backup = new File(backups, target.getName() + "-" + stamp + ".bak");
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            user.save(target);
            plugin.getLogger().info("Updated guis/" + target.getName() + ": " + added
                    + " missing key(s) added; previous file backed up to backups/guis/.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not migrate GUI config " + target.getName() + ": " + ex.getMessage());
        }
    }

    public String title(String fallback) {
        return yaml.getString("title", fallback);
    }

    public int slot(String key, int fallback) {
        int value = yaml.getInt("slots." + key, fallback);
        return value >= 0 && value < 54 ? value : fallback;
    }

    public int[] slots(String key, int[] fallback) {
        List<Integer> values = yaml.getIntegerList("slots." + key);
        if (values.isEmpty()) return fallback;
        int[] checked = values.stream().filter(value -> value >= 0 && value < 54)
                .distinct().mapToInt(Integer::intValue).toArray();
        return checked.length == 0 ? fallback : checked;
    }

    public String fillerMaterial(String fallback) {
        return yaml.getString("decoration.material", fallback);
    }

    public String fillerName(String fallback) {
        return yaml.getString("decoration.name", fallback);
    }

    public String fillerLore(String fallback) {
        return yaml.getString("decoration.lore", fallback);
    }

    public String accentMaterial(String fallback) {
        return yaml.getString("decoration.accent-material", fallback);
    }

    public Sound sound(String key, Sound fallback) {
        String value = yaml.getString("sounds." + key + ".type", fallback.name());
        try {
            return Sound.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public float soundVolume(String key, float fallback) {
        return (float) Math.max(0.0, Math.min(2.0, yaml.getDouble("sounds." + key + ".volume", fallback)));
    }

    public float soundPitch(String key, float fallback) {
        return (float) Math.max(0.5, Math.min(2.0, yaml.getDouble("sounds." + key + ".pitch", fallback)));
    }
}
