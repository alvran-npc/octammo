package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/**
 * Crash / disconnect safety net. Before an animation starts the rewards are written to
 * pending-rewards/&lt;player&gt;_&lt;session&gt;.yml; the file is deleted after delivery. Files of a player who was not
 * online at the end are delivered on the next join. All file access runs on the I/O thread.
 */
public final class PendingRewards {
    private final OctaMMOPlugin plugin;
    private final ExecutorService io;
    private final File dir;
    private final Map<UUID, Set<String>> index = new ConcurrentHashMap<>();

    public PendingRewards(OctaMMOPlugin plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
        this.dir = new File(plugin.getDataFolder(), "pending-rewards");
    }

    /** One-time scan at startup so that a join never has to list the folder. */
    public void load() {
        dir.mkdirs();
        String[] names = dir.list();
        if (names == null) return;
        for (String n : names) {
            int us = n.indexOf('_');
            if (!n.endsWith(".yml") || us <= 0) continue;
            try {
                index.computeIfAbsent(UUID.fromString(n.substring(0, us)), k -> ConcurrentHashMap.newKeySet()).add(n);
            } catch (IllegalArgumentException ignored) {
                // not ours
            }
        }
        int total = index.values().stream().mapToInt(Set::size).sum();
        if (total > 0) plugin.getLogger().info(total + " pending upgrade reward(s) waiting for their players.");
    }

    private static String fileName(UpgradeSession s) {
        return s.playerId + "_" + s.id + ".yml";
    }

    /** Serialises on the calling (main) thread, writes on the I/O thread. */
    public CompletableFuture<Void> save(UpgradeSession s) {
        List<String> encoded = new ArrayList<>();
        for (ItemStack item : s.outcome.rewards()) encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("session", s.id.toString());
        yml.set("player", s.playerId.toString());
        yml.set("name", s.playerName);
        yml.set("station", s.station.id());
        yml.set("path", s.path.id());
        yml.set("success", s.outcome.success());
        yml.set("created", s.startedAt);
        yml.set("items", encoded);
        String data = yml.saveToString();
        String name = fileName(s);

        index.computeIfAbsent(s.playerId, k -> ConcurrentHashMap.newKeySet()).add(name);
        s.persisted = true;
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    Files.createDirectories(dir.toPath());
                    Path tmp = dir.toPath().resolve(name + ".tmp");
                    Files.writeString(tmp, data, StandardCharsets.UTF_8);
                    Files.move(tmp, dir.toPath().resolve(name), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write pending reward " + name, e);
                }
            }, io);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** The rewards were delivered: forget and delete the file. */
    public void delete(UpgradeSession s) {
        if (!s.persisted) return;
        String name = fileName(s);
        Set<String> names = index.get(s.playerId);
        if (names != null) names.remove(name);
        deleteFile(name);
    }

    private void deleteFile(String name) {
        try {
            io.execute(() -> {
                try {
                    Files.deleteIfExists(dir.toPath().resolve(name));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not delete pending reward " + name, e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /** Delivers (once) everything waiting for a joining player. */
    public void deliverOnJoin(Player player) {
        Set<String> names = index.remove(player.getUniqueId());
        if (names == null || names.isEmpty()) return;
        List<String> files = List.copyOf(names);
        try {
            io.execute(() -> {
                List<ItemStack> items = new ArrayList<>();
                for (String n : files) {
                    File f = new File(dir, n);
                    if (!f.exists()) continue;
                    YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                    for (String b64 : yml.getStringList("items")) {
                        try {
                            items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Unreadable item in " + n, e);
                        }
                    }
                }
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            index.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).addAll(files);
                            return;
                        }
                        for (ItemStack item : items) Items.giveOrDrop(player, item);
                        plugin.lang().send(player, "upgrade.pending-delivered");
                        for (String n : files) deleteFile(n);
                    });
                } catch (IllegalPluginAccessException ignored) {
                    // plugin is shutting down, the files stay for the next start
                }
            });
        } catch (RejectedExecutionException e) {
            index.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).addAll(files);
        }
    }
}
