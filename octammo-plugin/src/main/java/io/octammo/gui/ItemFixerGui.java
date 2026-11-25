package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.ItemRef;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Repairs MMOItems data from its canonical TYPE/ID and returns installed gemstones and skins. */
public final class ItemFixerGui extends OctaGui {
    private final int inputSlot, extractSlot, repairSlot, closeSlot;
    private final FeatureGuiConfig guiConfig;

    public ItemFixerGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.guiConfig = FeatureGuiConfig.load(plugin, "item-fixer.yml", "item-fixer.yml");
        this.inputSlot = guiConfig.slot("input", 20);
        this.extractSlot = guiConfig.slot("extract", 24);
        this.repairSlot = guiConfig.slot("repair", 30);
        this.closeSlot = guiConfig.slot("close", 49);
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render(guiConfig.title("&8✦ &#F59E0B&lOCTAMMO &8» &6ITEM FORGE"), player, null));
        draw();
    }

    private void draw() {
        inventory.clear();
        Material pane = Material.matchMaterial(guiConfig.fillerMaterial("PURPLE_STAINED_GLASS_PANE"));
        if (pane == null) pane = Material.PURPLE_STAINED_GLASS_PANE;
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53}) {
            inventory.setItem(slot, item(pane, guiConfig.fillerName("&#B56CFF&l✦"), guiConfig.fillerLore("&8OctaMMO Item Utility")));
        }
        inventory.setItem(4, item(Material.ANVIL, "&6&lITEM RECONDITIONING", "&7Place one MMOItems item in the slot.", "&7Repair uses the item's original Type and ID.", "&8&m                                  ", "&7Installed gemstones and skins are returned.", "&7The MMOItems upgrade level is removed."));
        inventory.setItem(inputSlot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here."));
        inventory.setItem(extractSlot, item(Material.AMETHYST_SHARD, "&d&l✦ ENHANCEMENT RESTORATION", "&7Gemstones are extracted with their saved data.", "&7Applied skins are returned as MMOItems.", "&7Both are restored when installed on the item.", "", "&8This panel is informational only."));
        inventory.setItem(repairSlot, item(Material.GRINDSTONE, "&a&l✦ REPAIR & RESTORE", "&7Refresh the item from its Type and ID.", "&7Remove the installed skin and gemstones.", "&7Remove the MMOItems upgrade level (+N).", "&7Return recovered enhancements to your inventory."));
        inventory.setItem(closeSlot, item(Material.BARRIER, "&c&l✕ CLOSE", "&7Return the input item."));
    }

    private ItemStack item(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack input() {
        ItemStack stack = inventory.getItem(inputSlot);
        return Items.isReal(stack) ? stack : null;
    }

    @Override
    protected int inputSlot() { return inputSlot; }

    private boolean transactionPending;

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (transactionPending) { e.setCancelled(true); return; }
        if (e.getClickedInventory() != inventory) {
            if (e.getClickedInventory() == null || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                shiftInputFromPlayerInventory(e);
            }
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == closeSlot) { closeNextTick(); return; }
        if (slot == inputSlot) { swapInput(e); return; }
        if (slot == repairSlot) { repair(); }
    }

    private boolean isStillOpen() {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == inventory;
    }

    private void shiftInputFromPlayerInventory(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (!Items.isReal(clicked) || MmoItems.identify(clicked) == null) {
            plugin.lang().send(player, "fixer.not-mmoitem");
            return;
        }
        int sourceSlot = event.getSlot();
        org.bukkit.inventory.Inventory bottom = event.getClickedInventory();
        ItemStack snapshot = clicked.clone();
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen()) return;
                ItemStack source = bottom.getItem(sourceSlot);
                if (!Items.isReal(source) || !source.isSimilar(snapshot)) return;
                ItemStack existing = input();
                if (existing != null) return;
                ItemStack one = source.clone();
                one.setAmount(1);
                inventory.setItem(inputSlot, one);
                ItemStack remainder = source.clone();
                remainder.setAmount(source.getAmount() - 1);
                bottom.setItem(sourceSlot, Items.isEmpty(remainder) ? null : remainder);
            } finally {
                transactionPending = false;
            }
        });
    }

    private void swapInput(InventoryClickEvent e) {
        ClickType click = e.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND) return;
        boolean hotbar = e.getClick() == ClickType.NUMBER_KEY;
        boolean offhand = e.getClick() == ClickType.SWAP_OFFHAND;
        if (hotbar || offhand) {
            ItemStack external = offhand ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItem(e.getHotbarButton());
            ItemStack currentItem = input();
            if (Items.isReal(external) && MmoItems.identify(external) == null) {
                plugin.lang().send(player, "fixer.not-mmoitem");
                return;
            }
            if (Items.isReal(external) && external.getAmount() != 1) {
                plugin.lang().send(player, "fixer.single-item-only");
                return;
            }
            ItemStack capturedExternal = Items.isReal(external) ? external.clone() : null;
            ItemStack capturedCurrent = Items.isReal(currentItem) ? currentItem.clone() : null;
            int hotbarSlot = e.getHotbarButton();
            transactionPending = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!isStillOpen()) return;
                    ItemStack latestExternal = hotbar ? player.getInventory().getItem(hotbarSlot)
                            : player.getInventory().getItemInOffHand();
                    if (!sameItem(latestExternal, capturedExternal) || !sameItem(input(), capturedCurrent)) return;
                    if (hotbar) player.getInventory().setItem(hotbarSlot, capturedCurrent);
                    else player.getInventory().setItemInOffHand(capturedCurrent);
                    inventory.setItem(inputSlot, capturedExternal == null
                            ? item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here.")
                            : capturedExternal);
                } finally { transactionPending = false; }
            });
            return;
        }

        ItemStack cursor = e.getView().getCursor();
        ItemStack capturedCursor = Items.isReal(cursor) ? cursor.clone() : null;
        ItemStack current = input();
        ItemStack capturedCurrent = Items.isReal(current) ? current.clone() : null;
        if (capturedCursor != null && MmoItems.identify(capturedCursor) == null) {
            plugin.lang().send(player, "fixer.not-mmoitem");
            return;
        }
        if (capturedCursor != null && capturedCurrent != null && capturedCursor.getAmount() != 1) {
            plugin.lang().send(player, "fixer.single-item-only");
            return;
        }
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen()) return;
                if (capturedCursor == null) {
                    inventory.setItem(inputSlot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&e&lITEM INPUT", "&7Place one MMOItems item here."));
                    if (capturedCurrent != null) player.setItemOnCursor(capturedCurrent);
                } else if (capturedCurrent == null) {
                    ItemStack one = capturedCursor.clone();
                    one.setAmount(1);
                    inventory.setItem(inputSlot, one);
                    ItemStack remainder = capturedCursor.clone();
                    remainder.setAmount(capturedCursor.getAmount() - 1);
                    player.setItemOnCursor(Items.isEmpty(remainder) ? null : remainder);
                } else if (capturedCursor.getAmount() == 1) {
                    inventory.setItem(inputSlot, capturedCursor);
                    player.setItemOnCursor(capturedCurrent);
                } else {
                    plugin.lang().send(player, "fixer.single-item-only");
                }
            } finally { transactionPending = false; }
        });
    }

    private boolean sameItem(ItemStack first, ItemStack second) {
        if (!Items.isReal(first) && !Items.isReal(second)) return true;
        return Items.isReal(first) && Items.isReal(second)
                && first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    @Override
    public void handleDrag(InventoryDragEvent e) {
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < e.getView().getTopInventory().getSize());
        if (!touchesTop) return;
        e.setCancelled(true);
        if (transactionPending || e.getRawSlots().size() != 1 || !e.getRawSlots().contains(inputSlot)) return;
        ItemStack dragged = e.getOldCursor();
        if (!Items.isReal(dragged) || MmoItems.identify(dragged) == null) {
            plugin.lang().send(player, "fixer.not-mmoitem");
            return;
        }
        ItemStack one = dragged.clone();
        one.setAmount(1);
        ItemStack remainder = dragged.clone();
        remainder.setAmount(Math.max(0, dragged.getAmount() - 1));
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen() || input() != null) return;
                inventory.setItem(inputSlot, one);
                player.setItemOnCursor(Items.isEmpty(remainder) ? null : remainder);
            } finally { transactionPending = false; }
        });
    }

    private ItemRef requireMmoItem() {
        ItemStack item = input();
        if (item == null) {
            plugin.lang().send(player, "fixer.insert-item");
            return null;
        }
        if (item.getAmount() != 1) {
            plugin.lang().send(player, "fixer.single-item-only");
            return null;
        }
        ItemRef ref = MmoItems.identify(item);
        if (ref == null) plugin.lang().send(player, "fixer.not-mmoitem");
        return ref;
    }

    private void repair() {
        ItemRef ref = requireMmoItem();
        if (ref == null) return;
        ItemStack original = input();
        MmoItems.RebuildResult rebuilt = MmoItems.rebuild(original);
        if (rebuilt == null) {
            plugin.lang().send(player, "fixer.rebuild-failed");
            return;
        }
        ItemStack clean = rebuilt.item();
        // Preserve native Minecraft enchantments only. MMOItems enhancements are
        // deliberately removed by the native Reforger and returned as separate items.
        if (plugin.getConfig().getBoolean("item-fixer.preserve-vanilla-enchantments", true)) {
            copyVanillaEnchants(original, clean);
        }
        inventory.setItem(inputSlot, null);
        Items.giveOrDrop(player, clean);
        rebuilt.extraItems().forEach(extra -> Items.giveOrDrop(player, extra));
        plugin.lang().send(player, "fixer.repair-success",
                "item", MmoItems.displayName(ref),
                "gems", String.valueOf(rebuilt.gemstoneCount()),
                "skin", rebuilt.skinReturned() ? "1" : "0",
                "upgrades", String.valueOf(rebuilt.removedUpgradeLevel()));
        closeNextTick();
    }

    private void copyVanillaEnchants(ItemStack source, ItemStack target) {
        if (!source.hasItemMeta() || !target.hasItemMeta()) return;
        ItemMeta from = source.getItemMeta();
        ItemMeta to = target.getItemMeta();
        for (var entry : from.getEnchants().entrySet()) to.addEnchant(entry.getKey(), entry.getValue(), true);
        target.setItemMeta(to);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        ItemStack item = inventory.getItem(inputSlot);
        if (Items.isReal(item)) Items.giveOrDrop(player, item);
        inventory.setItem(inputSlot, null);
    }
}
