package io.octammo.core;

import io.octammo.OctaMMOPlugin;
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

/**
 * Adds keys that exist in the built-in file but not in the admin's file, never touching what the admin
 * changed. The old file is copied to backups/ first. Used for config.yml and lang/*.yml.
 */
public final class ConfigMigrator {
    private static final java.util.List<String> LEGACY_DEFAULT_LOGO = java.util.List.of(
            ".###. .#### ##### .###. #...# #...# .###.",
            "#...# #.... ..#.. #...# ##.## ##.## #...#",
            "#...# #.... ..#.. ##### #.#.# #.#.# #...#",
            "#...# #.... ..#.. #...# #...# #...# #...#",
            ".###. .#### ..#.. #...# #...# #...# .###."
    );

    private ConfigMigrator() {}

    public static void migrate(OctaMMOPlugin plugin, File file, String resource) {
        if (!file.exists()) {
            plugin.saveResource(resource, false);
            return;
        }
        YamlConfiguration def = new YamlConfiguration();
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                def = YamlConfiguration.loadConfiguration(r);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read built-in " + resource + ": " + e.getMessage());
            return;
        }

        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        int added = 0;
        // Replace only the previous built-in placeholder. If an administrator
        // customized the logo, preserve it exactly as configured.
        if (user.getStringList("startup-banner.logo").equals(LEGACY_DEFAULT_LOGO)) {
            java.util.List<String> replacement = def.getStringList("startup-banner.logo");
            if (!replacement.isEmpty()) {
                user.set("startup-banner.logo", replacement);
                added++;
            }
        }
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key) || user.contains(key)) continue;
            user.set(key, def.get(key));
            added++;
        }
        int defVersion = def.getInt("config-version", 0);
        boolean versionBump = defVersion > user.getInt("config-version", 0);
        if (versionBump) user.set("config-version", defVersion);
        if (added == 0 && !versionBump) return;

        try {
            File backups = new File(plugin.getDataFolder(), "backups");
            backups.mkdirs();
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            Files.copy(file.toPath(), new File(backups, file.getName() + "-" + stamp + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            user.save(file);
            plugin.getLogger().info("Migrated " + resource + ": " + added + " new key(s) added, old file saved in backups/.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not migrate " + resource + ": " + e.getMessage());
        }
    }
}
