package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import io.octammo.core.Items;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Base of every OctaMMOPlugin inventory. The listener forwards events to the holder. */
public abstract class OctaGui implements InventoryHolder {
    protected final OctaMMOPlugin plugin;
    protected final Player player;
    protected Inventory inventory;
    /** true while the plugin itself switches to another OctaMMOPlugin inventory (the close event must not return items). */
    protected boolean transition;

    protected OctaGui(OctaMMOPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player player() { return player; }

    public abstract void handleClick(InventoryClickEvent e);

    public abstract void handleClose(InventoryCloseEvent e);

    /** Input slot that accepts a deliberate one-item drag; -1 means no input slot. */
    protected int inputSlot() { return -1; }

    /**
     * Safely supports drag-and-drop into the configured input slot. All other drags
     * touching the GUI are blocked so decorative/control items can never be moved.
     */
    public void handleDrag(InventoryDragEvent e) {
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!touchesTop) return;
        e.setCancelled(true);
        int target = inputSlot();
        if (target < 0 || e.getRawSlots().size() != 1 || !e.getRawSlots().contains(target)) return;
        ItemStack dragged = e.getOldCursor();
        if (!Items.isReal(dragged)) return;
        // Apply after Bukkit finishes the cancelled drag transaction. This avoids
        // InventoryClickEvent#setCursor's deprecated pre-transaction behaviour.
        ItemStack one = dragged.clone();
        one.setAmount(1);
        ItemStack remainder = dragged.clone();
        remainder.setAmount(Math.max(0, dragged.getAmount() - 1));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
            ItemStack existing = inventory.getItem(target);
            if (Items.isReal(existing)) return;
            inventory.setItem(target, one);
            player.setItemOnCursor(Items.isEmpty(remainder) ? null : remainder);
            onInputChanged();
        });
    }

    /** Hook for input GUIs to refresh their preview after a drag insertion. */
    protected void onInputChanged() {}

    /** Called after the inventory is shown to the player. */
    public void onOpened() {}

    public void open() {
        player.openInventory(inventory);
        onOpened();
    }

    /**
     * Shows another OctaMMO inventory on the next tick. Paper warns against opening
     * or closing inventories from inside InventoryClickEvent; the transition flag is
     * set only immediately before the scheduled open so an early manual close still
     * returns the current GUI's input items.
     */
    protected void switchTo(OctaGui other) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
            transition = true;
            try {
                player.openInventory(other.getInventory());
                other.onOpened();
            } finally {
                transition = false;
            }
        });
    }

    /** Closes this GUI safely after the active inventory event has completed. */
    protected void closeNextTick() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == inventory) {
                player.closeInventory();
            }
        });
    }
}
