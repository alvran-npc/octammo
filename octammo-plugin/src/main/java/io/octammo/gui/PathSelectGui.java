package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.upgrade.Defs;
import io.octammo.upgrade.PathData;
import io.octammo.upgrade.StationData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Menu listing the paths of a station. Clicking a path opens its Altar Forge. */
public final class PathSelectGui extends OctaGui {
    public static final Set<String> SINGLES = Set.of("info", "close");
    public static final Set<String> LISTS = Set.of("paths");

    private final StationData station;
    private final GuiLayout layout;
    private final Map<Integer, PathData> bySlot = new HashMap<>();
    private final int closeSlot;

    public PathSelectGui(OctaMMOPlugin plugin, StationData station, Player player) {
        super(plugin, player);
        this.station = station;
        this.layout = plugin.guis().pathSelect();
        this.closeSlot = layout.slot("close");

        Map<String, String> ph = Map.of("station", station.displayName());
        this.inventory = Bukkit.createInventory(this, 54, Lang.color(plugin.lang().expand(layout.title(), player, ph)));
        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            inventory.setItem(i, Items.make(edge ? layout.border() : layout.filler(), Component.space(), null));
        }
        inventory.setItem(layout.slot("info"), layout.item("info", ItemDef.of(Material.ENCHANTING_TABLE, "{station}", "{gui.path.info}")).build(plugin, player, ph));
        inventory.setItem(closeSlot, layout.item("close", ItemDef.of(Material.BARRIER, "{gui.close}")).build(plugin, player, ph));

        List<Integer> slots = layout.slots("paths");
        List<PathData> paths = station.pathData();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (i < paths.size()) {
                bySlot.put(slot, paths.get(i));
                inventory.setItem(slot, entry(paths.get(i)));
            } else {
                inventory.setItem(slot, Items.make(layout.filler(), Component.space(), null));
            }
        }
        if (paths.size() > slots.size()) {
            plugin.getLogger().warning("Station '" + station.id() + "' has " + paths.size() + " paths but guis/path-select.yml only shows " + slots.size() + ".");
        }
    }

    private ItemStack entry(PathData p) {
        Defs.ResultDef first = p.results().get(0);
        ItemStack base = MmoItems.create(first.ref());
        if (base == null) base = new ItemStack(Material.BARRIER);
        Map<String, String> ph = new HashMap<>();
        ph.put("path", p.displayName());
        ph.put("input", MmoItems.displayName(p.input()));
        ph.put("output", MmoItems.displayName(first.ref()));
        ph.put("chance", ForgeGui.fmt(p.chance().base()));
        ph.put("description", String.join("\n", p.description()));

        ItemMeta meta = base.getItemMeta();
        meta.displayName(plugin.lang().render("{gui.path.entry-name}", player, ph));
        meta.lore(plugin.lang().renderLines(List.of("{gui.path.entry-lore}"), player, ph));
        base.setItemMeta(meta);
        base.setAmount(1);
        return Items.markVirtual(base);
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);   // nothing can be placed in or taken from this menu
        if (player.getGameMode() == GameMode.CREATIVE || e.getClickedInventory() != inventory) return;
        int slot = e.getRawSlot();
        if (slot == closeSlot) {
            closeNextTick();
            return;
        }
        PathData path = bySlot.get(slot);
        if (path == null) return;
        ForgeGui forge = ForgeGui.create(plugin, station, path, player);
        if (forge == null) {
            plugin.lang().send(player, "upgrade.too-many-runes", "path", path.id());
            return;
        }
        switchTo(forge);
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        // nothing to return
    }
}
