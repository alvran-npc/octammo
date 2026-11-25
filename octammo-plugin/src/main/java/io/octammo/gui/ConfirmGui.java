package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.core.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.Set;

/**
 * Asked before a risky upgrade (the input item is lost on failure and no protection is inserted).
 * The Altar Forge keeps its items while this menu is open.
 */
public final class ConfirmGui extends OctaGui {
    public static final Set<String> SINGLES = Set.of("info", "confirm", "cancel");

    private final ForgeGui forge;
    private final GuiLayout layout;

    public ConfirmGui(OctaMMOPlugin plugin, ForgeGui forge) {
        super(plugin, forge.player());
        this.forge = forge;
        this.layout = plugin.guis().confirm();

        Map<String, String> ph = Map.of("station", forge.station().displayName(), "path", forge.path().displayName());
        this.inventory = Bukkit.createInventory(this, 54, Lang.color(plugin.lang().expand(layout.title(), player, ph)));
        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            inventory.setItem(i, Items.make(edge ? layout.border() : layout.filler(), Component.space(), null));
        }
        inventory.setItem(layout.slot("info"), layout.item("info",
                ItemDef.of(Material.TNT, "{gui.confirm.title}", "{gui.confirm.warning}")).build(plugin, player, ph));
        inventory.setItem(layout.slot("confirm"), layout.item("confirm",
                ItemDef.of(Material.LIME_CONCRETE, "{gui.confirm.accept}")).build(plugin, player, ph));
        inventory.setItem(layout.slot("cancel"), layout.item("cancel",
                ItemDef.of(Material.RED_CONCRETE, "{gui.confirm.decline}")).build(plugin, player, ph));
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE || e.getClickedInventory() != inventory) return;
        int slot = e.getRawSlot();
        if (slot == layout.slot("confirm")) {
            switchTo(forge);
            plugin.service().begin(forge);
        } else if (slot == layout.slot("cancel")) {
            switchTo(forge);
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (transition) return;
        forge.returnAll();   // closed with ESC: the player gets everything back
    }
}
