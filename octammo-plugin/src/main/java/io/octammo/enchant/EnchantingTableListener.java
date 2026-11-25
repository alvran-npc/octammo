package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Replaces vanilla enchanting-table interaction with the OctaMMO enchanting GUI. */
public final class EnchantingTableListener implements Listener {
    private final OctaMMOPlugin plugin;

    public EnchantingTableListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantingTable(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("modules.custom-enchanting", true)) return;
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;
        if (!event.getPlayer().hasPermission("octammo.enchant")) {
            plugin.lang().send(event.getPlayer(), "no-permission");
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        new EnchantingGui(plugin, event.getPlayer()).open();
    }
}
