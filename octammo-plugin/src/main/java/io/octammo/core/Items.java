package io.octammo.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

public final class Items {
    /** Marks GUI-only items (placeholders, previews). They are never real items. */
    public static final NamespacedKey VIRTUAL = Objects.requireNonNull(NamespacedKey.fromString("octammo:virtual"));

    private Items() {}

    public static ItemStack make(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(VIRTUAL, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack markVirtual(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(VIRTUAL, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isEmpty(ItemStack s) {
        return s == null || s.getType().isAir() || s.getAmount() <= 0;
    }

    public static boolean isVirtual(ItemStack s) {
        return !isEmpty(s) && s.hasItemMeta() && s.getItemMeta().getPersistentDataContainer().has(VIRTUAL, PersistentDataType.BYTE);
    }

    /** A real item: not empty and not a GUI placeholder. */
    public static boolean isReal(ItemStack s) {
        return !isEmpty(s) && !isVirtual(s);
    }

    public static void giveOrDrop(Player p, ItemStack stack) {
        if (isEmpty(stack)) return;
        p.getInventory().addItem(stack).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }
}
