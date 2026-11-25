package io.octammo.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/** Public API of OctaMMO. Every method must be called from the main server thread unless stated otherwise. */
public interface OctaMMOApi {
    /** Version of this API. It only grows; methods are never removed inside the same major plugin version. */
    int API_VERSION = 2;

    default int apiVersion() {
        return API_VERSION;
    }

    // ---------------------------------------------------------------- Item Upgrade

    @NotNull Collection<UpgradeStation> getStations();

    @NotNull Optional<UpgradeStation> getStation(@NotNull String id);

    /** Opens the path menu (or the Altar Forge when the station has one path). Permission and region checks apply. */
    void openStation(@NotNull Player player, @NotNull String stationId);

    /** Opens the Altar Forge of one path. */
    void openStation(@NotNull Player player, @NotNull String stationId, @NotNull String pathId);

    /**
     * Upgrades without a GUI. The input item and the item costs are taken from the player's inventory.
     * Boosters and protection are not used.
     */
    @NotNull UpgradeResult attemptUpgrade(@NotNull Player player, @NotNull UpgradeContext context);

    /** True while the loading phase of an upgrade runs for this player. */
    boolean isUpgrading(@NotNull Player player);

    // ---------------------------------------------------------------- extension points

    void registerCostType(@NotNull CostType type);

    void registerRequirement(@NotNull RequirementType type);

    void registerEffect(@NotNull EffectType type);

    // ---------------------------------------------------------------- utilities

    /** TYPE + ID of an MMOItem, empty for anything else. */
    @NotNull Optional<MmoItemRef> resolve(@Nullable ItemStack item);

    /** Reloads the upgrades/*.yml files. */
    void reloadStations();
}
