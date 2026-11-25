package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels what a player may not do while their upgrade loads. Every handler starts with an O(1) lookup in the
 * session map and returns at once for everybody else. Movement is not listened to (slowness / negative jump do it).
 */
public final class LoadingLockListener implements Listener {
    private final OctaMMOPlugin plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public LoadingLockListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    private LoadingConfig.Lock lock(UUID id) {
        return plugin.loading().lockOf(id);
    }

    private void warn(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastMessage.put(p.getUniqueId(), now);
        plugin.lang().send(p, "upgrade.loading.locked");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.commands()) return;
        String label = e.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        for (String allowed : l.allowedCommands()) if (allowed.equalsIgnoreCase(label)) return;
        e.setCancelled(true);
        warn(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.interact()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.interact()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.inventory()) return;
        e.setCancelled(true);
        if (e.getPlayer() instanceof Player p) warn(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        LoadingConfig.Lock l = lock(e.getWhoClicked().getUniqueId());
        if (l == null || !l.inventory()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.dropPickup()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        LoadingConfig.Lock l = lock(p.getUniqueId());
        if (l == null || !l.dropPickup()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.inventory()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.inventory()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.interact()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        LoadingConfig.Lock l = lock(e.getPlayer().getUniqueId());
        if (l == null || !l.interact()) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker) {
            LoadingConfig.Lock l = lock(attacker.getUniqueId());
            if (l != null && l.combat()) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getEntity() instanceof Player victim) {
            LoadingConfig.Lock l = lock(victim.getUniqueId());
            if (l != null && l.combat()) e.setCancelled(true);
        }
    }
}
