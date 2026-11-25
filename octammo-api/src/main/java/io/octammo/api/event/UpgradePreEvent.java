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

/** Fired before anything is taken. Cancel it, or change the final chance. */
public class UpgradePreEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UpgradeStation station;
    private final UpgradePath path;
    private final double boosterBonus;
    private double chance;
    private boolean cancelled;

    public UpgradePreEvent(@NotNull Player player, @NotNull UpgradeStation station, @NotNull UpgradePath path, double chance, double boosterBonus) {
        super(player);
        this.station = station;
        this.path = path;
        this.chance = chance;
        this.boosterBonus = boosterBonus;
    }

    public UpgradeStation getStation() { return station; }
    public UpgradePath getPath() { return path; }
    /** Final chance in percent (base + boosters), 0-100. */
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = Math.max(0, Math.min(100, chance)); }
    /** Percentage points added by booster items. */
    public double getBoosterBonus() { return boosterBonus; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
