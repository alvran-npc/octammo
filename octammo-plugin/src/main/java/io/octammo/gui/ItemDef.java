package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/** An item described in guis/*.yml. name / lore may use {lang.key} tokens, {placeholders} and PlaceholderAPI. */
public record ItemDef(Material material, String name, List<String> lore, boolean glow,
                      Integer customModelData, String itemModel) {

    public static ItemDef of(Material material, String name, String... lore) {
        return new ItemDef(material, name, List.of(lore), false, null, null);
    }

    public ItemDef withGlow() {
        return new ItemDef(material, name, lore, true, customModelData, itemModel);
    }

    public static ItemDef parse(ConfigurationSection s, ItemDef fallback) {
        if (s == null) return fallback;
        Material m = Material.matchMaterial(s.getString("material", ""));
        if (m == null || m.isAir()) m = fallback.material();
        return new ItemDef(m,
                s.getString("name", fallback.name()),
                s.contains("lore") ? s.getStringList("lore") : fallback.lore(),
                s.getBoolean("glow", fallback.glow()),
                s.contains("custom-model-data") ? Integer.valueOf(s.getInt("custom-model-data")) : fallback.customModelData(),
                s.getString("item-model", fallback.itemModel()));
    }

    /** Builds a virtual (GUI-only) item. */
    public ItemStack build(OctaMMOPlugin plugin, Player player, Map<String, String> ph) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.lang().render(name, player, ph));
        List<Component> lines = plugin.lang().renderLines(lore, player, ph);
        if (!lines.isEmpty()) meta.lore(lines);
        if (glow) meta.setEnchantmentGlintOverride(true);
        if (customModelData != null) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of(customModelData.floatValue()));
            meta.setCustomModelDataComponent(cmd);
        }
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.toLowerCase());
            if (key != null) meta.setItemModel(key);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Items.VIRTUAL, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }
}
