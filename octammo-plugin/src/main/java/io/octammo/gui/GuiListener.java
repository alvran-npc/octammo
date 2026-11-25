package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Forwards inventory events to the OctaGui that owns the inventory, plus join / quit handling. */
public final class GuiListener implements Listener {
    private final OctaMMOPlugin plugin;

    public GuiListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof OctaGui gui && e.getWhoClicked() instanceof Player) {
            gui.handleClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof OctaGui gui) gui.handleDrag(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof OctaGui gui) gui.handleClose(e);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.pending().deliverOnJoin(p);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        plugin.loading().handleQuit(e.getPlayer());
        plugin.upgrades().forget(e.getPlayer().getUniqueId());
        if (plugin.interactLock() != null) plugin.interactLock().forget(e.getPlayer().getUniqueId());
    }
}
