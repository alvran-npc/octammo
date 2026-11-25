package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.CostType;
import io.octammo.api.EffectType;
import io.octammo.api.MmoItemRef;
import io.octammo.api.OctaMMOApi;
import io.octammo.api.RequirementType;
import io.octammo.api.UpgradeContext;
import io.octammo.api.UpgradeResult;
import io.octammo.api.UpgradeStation;
import io.octammo.core.ItemRef;
import io.octammo.core.MmoItems;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/** The OctaMMOApi implementation. */
public final class ApiImpl implements OctaMMOApi {
    private final OctaMMOPlugin plugin;

    public ApiImpl(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Collection<UpgradeStation> getStations() {
        return new ArrayList<UpgradeStation>(plugin.upgrades().all());
    }

    @Override
    public @NotNull Optional<UpgradeStation> getStation(@NotNull String id) {
        return Optional.ofNullable(plugin.upgrades().get(id));
    }

    @Override
    public void openStation(@NotNull Player player, @NotNull String stationId) {
        plugin.upgrades().open(player, stationId, player, null);
    }

    @Override
    public void openStation(@NotNull Player player, @NotNull String stationId, @NotNull String pathId) {
        plugin.upgrades().open(player, stationId, player, pathId);
    }

    @Override
    public @NotNull UpgradeResult attemptUpgrade(@NotNull Player player, @NotNull UpgradeContext context) {
        return plugin.service().attempt(player, context);
    }

    @Override
    public boolean isUpgrading(@NotNull Player player) {
        return plugin.loading().isUpgrading(player);
    }

    @Override public void registerCostType(@NotNull CostType type) { plugin.registry().register(type); }
    @Override public void registerRequirement(@NotNull RequirementType type) { plugin.registry().register(type); }
    @Override public void registerEffect(@NotNull EffectType type) { plugin.registry().register(type); }

    @Override
    public @NotNull Optional<MmoItemRef> resolve(@Nullable ItemStack item) {
        if (!plugin.mmoItems()) return Optional.empty();
        ItemRef ref = MmoItems.identify(item);
        return ref == null ? Optional.empty() : Optional.of(new MmoItemRef(ref.type(), ref.id()));
    }

    @Override
    public void reloadStations() {
        if (plugin.mmoItems()) plugin.upgrades().load();
    }
}
