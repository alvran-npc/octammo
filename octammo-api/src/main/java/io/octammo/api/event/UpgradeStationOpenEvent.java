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

/** Before a station GUI is opened. */
public class UpgradeStationOpenEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private boolean cancelled;

    public UpgradeStationOpenEvent(@NotNull Player player, @NotNull UpgradeStation station, @Nullable UpgradePath path) {
        super(player);
        this.station = station;
        this.path = path;
    }

    public UpgradeStation getStation() { return station; }
    /** The path that opens directly, or null when the path menu is shown. */
    public @Nullable UpgradePath getPath() { return path; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
