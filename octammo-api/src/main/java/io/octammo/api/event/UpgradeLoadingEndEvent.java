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

/** When the loading ended (100% or finished early). */
public class UpgradeLoadingEndEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private final boolean success;
    private final boolean instant;

    public UpgradeLoadingEndEvent(@NotNull Player player, @NotNull UpgradeStation station, @NotNull UpgradePath path, boolean success, boolean instant) {
        super(player);
        this.station = station;
        this.path = path;
        this.success = success;
        this.instant = instant;
    }

    public UpgradeStation getStation() { return station; }
    public UpgradePath getPath() { return path; }
    public boolean isSuccess() { return success; }
    /** true when the loading was skipped (disconnect, shutdown, skip-loading). */
    public boolean isInstant() { return instant; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
