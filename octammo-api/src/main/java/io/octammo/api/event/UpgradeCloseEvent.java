package io.octammo.api.event;

import io.octammo.api.MmoItemRef;
import io.octammo.api.UpgradePath;
import io.octammo.api.UpgradeStation;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Fired when an Altar Forge is closed and the items inside are handed back. */
public class UpgradeCloseEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private final List<ItemStack> returned;

    public UpgradeCloseEvent(@NotNull Player player, @NotNull UpgradeStation station, @NotNull UpgradePath path, @NotNull List<ItemStack> returned) {
        super(player);
        this.station = station;
        this.path = path;
        this.returned = returned;
    }

    public UpgradeStation getStation() { return station; }
    public UpgradePath getPath() { return path; }
    public List<ItemStack> getReturnedItems() { return returned; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
