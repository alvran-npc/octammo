package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Snapshot of upgrade-loading.* taken when an upgrade starts (a reload cannot change a running loading). */
public record LoadingConfig(boolean enabled, int duration, int percentStep, String title, String subtitle,
                            int fadeIn, int stay, int fadeOut, boolean soundEnabled, String tickSound, int soundEvery,
                            int resultTicks, boolean reopen, Lock lock) {

    public record Lock(boolean freeze, boolean invulnerable, boolean commands, List<String> allowedCommands,
                       boolean interact, boolean inventory, boolean dropPickup, boolean combat) {}

    public static LoadingConfig read(OctaMMOPlugin plugin, StationData station, PathData path) {
        FileConfiguration c = plugin.getConfig();
        String root = "upgrade-loading.";
        int duration = c.getInt(root + "duration-ticks", 100);
        if (station.loadingTicks() != null) duration = station.loadingTicks();
        if (path.loadingTicks() != null) duration = path.loadingTicks();
        String title = c.getString(root + "title", "");
        String subtitle = c.getString(root + "subtitle-format", "");
        Lock lock = new Lock(
                c.getBoolean(root + "lock.freeze-movement", true),
                c.getBoolean(root + "lock.invulnerable", true),
                c.getBoolean(root + "lock.block-commands", true),
                c.getStringList(root + "lock.allowed-commands"),
                c.getBoolean(root + "lock.block-interact", true),
                c.getBoolean(root + "lock.block-inventory", true),
                c.getBoolean(root + "lock.block-drop-pickup", true),
                c.getBoolean(root + "lock.block-combat", true));
        return new LoadingConfig(
                c.getBoolean(root + "enabled", true),
                Math.max(0, duration),
                Math.max(1, c.getInt(root + "percent-step", 1)),
                title == null || title.isBlank() ? "{upgrade.loading.title}" : title,
                subtitle == null || subtitle.isBlank() ? "{upgrade.loading.subtitle}" : subtitle,
                Math.max(0, c.getInt(root + "title-times.fade-in", 0)),
                Math.max(2, c.getInt(root + "title-times.stay", 10)),
                Math.max(0, c.getInt(root + "title-times.fade-out", 0)),
                c.getBoolean(root + "sound.enabled", true),
                c.getString(root + "sound.tick", "BLOCK_NOTE_BLOCK_CHIME"),
                Math.max(1, c.getInt(root + "sound.every-percent", 10)),
                Math.max(0, c.getInt(root + "result-title-ticks", 40)),
                c.getBoolean(root + "reopen-gui-after", true),
                lock);
    }

    public LoadingConfig withDuration(int ticks) {
        return new LoadingConfig(enabled, ticks, percentStep, title, subtitle, fadeIn, stay, fadeOut, soundEnabled,
                tickSound, soundEvery, resultTicks, reopen, lock);
    }
}
