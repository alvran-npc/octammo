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

/** Fired at 100% of the loading: the result is known and being delivered. */
public class UpgradeResultEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private final boolean success;
    private final ItemStack input;
    private final ItemStack output;
    private final List<String> consumedCosts;

    public UpgradeResultEvent(@NotNull Player player, @NotNull UpgradeStation station, @NotNull UpgradePath path, boolean success,
                              @Nullable ItemStack input, @Nullable ItemStack output, @NotNull List<String> consumedCosts) {
        super(player);
        this.station = station;
        this.path = path;
        this.success = success;
        this.input = input;
        this.output = output;
        this.consumedCosts = consumedCosts;
    }

    public UpgradeStation getStation() { return station; }
    public UpgradePath getPath() { return path; }
    public boolean isSuccess() { return success; }
    /** The item that was upgraded (as it was before). */
    public @Nullable ItemStack getInput() { return input; }
    /** The item that was given, null on failure. */
    public @Nullable ItemStack getOutput() { return output; }
    /** Plain-text description of every cost that was consumed. */
    public List<String> getConsumedCosts() { return consumedCosts; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
