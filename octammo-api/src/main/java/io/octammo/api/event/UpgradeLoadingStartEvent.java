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

/** Before the title loading starts. Cancel it (the result is then delivered at once) or change the duration. */
public class UpgradeLoadingStartEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private int durationTicks;
    private boolean cancelled;

    public UpgradeLoadingStartEvent(@NotNull Player player, @NotNull UpgradeStation station, @NotNull UpgradePath path, int durationTicks) {
        super(player);
        this.station = station;
        this.path = path;
        this.durationTicks = durationTicks;
    }

    public UpgradeStation getStation() { return station; }
    public UpgradePath getPath() { return path; }
    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int durationTicks) { this.durationTicks = Math.max(1, durationTicks); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
