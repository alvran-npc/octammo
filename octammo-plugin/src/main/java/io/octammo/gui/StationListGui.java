package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.upgrade.StationData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** /itemupgrade without an argument: the stations this player may open (uses the path-select layout). */
public final class StationListGui extends OctaGui {
    private final GuiLayout layout;
    private final Map<Integer, String> bySlot = new HashMap<>();
    private final int closeSlot;

    public StationListGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        this.layout = plugin.guis().pathSelect();
        this.closeSlot = layout.slot("close");
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render("{gui.station-list.title}", player, null));
        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            inventory.setItem(i, Items.make(edge ? layout.border() : layout.filler(), Component.space(), null));
        }
        Map<String, String> none = Map.of();
        inventory.setItem(layout.slot("info"), ItemDef.of(Material.ENCHANTING_TABLE, "{gui.station-list.title}", "{gui.station-list.info}").build(plugin, player, none));
        inventory.setItem(closeSlot, layout.item("close", ItemDef.of(Material.BARRIER, "{gui.close}")).build(plugin, player, none));

        List<Integer> slots = layout.slots("paths");
        int i = 0;
        for (StationData st : plugin.upgrades().all()) {
            if (i >= slots.size()) break;
            if (!plugin.upgrades().canUse(player, st) || st.pathData().isEmpty()) continue;
            Map<String, String> ph = Map.of("station", st.displayName(), "paths", String.valueOf(st.pathData().size()));
            bySlot.put(slots.get(i), st.id());
            inventory.setItem(slots.get(i), ItemDef.of(Material.ANVIL, "{gui.station-list.entry-name}", "{gui.station-list.entry-lore}").build(plugin, player, ph));
            i++;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE || e.getClickedInventory() != inventory) return;
        int slot = e.getRawSlot();
        if (slot == closeSlot) {
            closeNextTick();
            return;
        }
        String id = bySlot.get(slot);
        if (id != null) plugin.upgrades().open(player, id, player, null);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        // nothing to return
    }
}
