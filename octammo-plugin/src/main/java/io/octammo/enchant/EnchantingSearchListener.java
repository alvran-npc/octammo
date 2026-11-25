package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Captures a single player chat message as an enchanting search query. */
public final class EnchantingSearchListener implements Listener {
    private final OctaMMOPlugin plugin;
    private final Map<UUID, EnchantingGui> waiting = new ConcurrentHashMap<>();

    public EnchantingSearchListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, EnchantingGui gui) {
        UUID playerId = player.getUniqueId();
        waiting.put(playerId, gui);
        // One delayed expiry per prompt; this is not a repeating task or GUI animation.
        long timeoutSeconds = Math.max(10L, Math.min(600L,
                plugin.getConfig().getLong("enchanting.search-timeout-seconds", 60L)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (waiting.remove(playerId, gui)) gui.searchExpired();
        }, 20L * timeoutSeconds);
    }

    public void end(Player player) {
        waiting.remove(player.getUniqueId());
    }

    /** Command fallback for servers that replace or suppress normal chat events. */
    public boolean acceptCommand(Player player, String keyword) {
        EnchantingGui gui = waiting.remove(player.getUniqueId());
        if (gui == null) return false;
        gui.acceptSearch(keyword);
        return true;
    }

    /** Cancels a stale prompt from a previous GUI session and safely returns its input. */
    public void cancelPrevious(Player player) {
        EnchantingGui previous = waiting.remove(player.getUniqueId());
        if (previous != null) previous.returnInputToPlayer();
    }

    public boolean isWaiting(Player player) {
        return waiting.containsKey(player.getUniqueId());
    }

    public void clear() {
        var active = java.util.List.copyOf(waiting.values());
        waiting.clear();
        active.forEach(EnchantingGui::cancelSearchPrompt);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EnchantingGui gui = waiting.remove(player.getUniqueId());
        if (gui == null) return;
        event.setCancelled(true);
        String keyword = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) gui.acceptSearch(keyword);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EnchantingGui gui = waiting.remove(event.getPlayer().getUniqueId());
        if (gui != null) gui.returnInputToPlayer();
    }
}
