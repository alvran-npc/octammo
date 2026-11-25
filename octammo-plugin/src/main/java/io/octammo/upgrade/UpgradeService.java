package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.UpgradeContext;
import io.octammo.api.UpgradeResult;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.gui.ForgeGui;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Starts upgrades: from the GUI button, or GUI-less through the API. */
public final class UpgradeService {
    private final OctaMMOPlugin plugin;
    private final UpgradeEngine engine;

    public UpgradeService(OctaMMOPlugin plugin) {
        this.plugin = plugin;
        this.engine = new UpgradeEngine(plugin);
    }

    public UpgradeEngine engine() {
        return engine;
    }

    /** Called by the GO button: show a hint when something is missing, ask for confirmation when it is risky. */
    public void tryStart(ForgeGui gui) {
        Player p = gui.player();
        if (gui.locked() || plugin.loading().isUpgrading(p)) return;
        ForgeGui.Problem problem = gui.problem();
        if (problem != ForgeGui.Problem.NONE) {
            plugin.lang().send(p, problem.messageKey(), "seconds", String.valueOf(plugin.upgrades().cooldownLeft(p, gui.station())));
            gui.refresh();
            return;
        }
        PathData path = gui.path();
        boolean risky = (path.fail().input() == Defs.InputFail.CONSUME || path.fail().input() == Defs.InputFail.BREAK
                || path.fail().input() == Defs.InputFail.DOWNGRADE) && gui.activeProtection() == null;
        if (risky && plugin.getConfig().getBoolean("upgrade.confirm-destroy", true)) {
            gui.switchToConfirm();
            return;
        }
        begin(gui);
    }

    /** Validates, takes money, rolls once, empties the GUI, closes it and starts the loading. */
    public void begin(ForgeGui gui) {
        Player p = gui.player();
        if (gui.locked() || plugin.loading().isUpgrading(p)) return;
        ForgeGui.Problem problem = gui.problem();
        if (problem != ForgeGui.Problem.NONE) {
            plugin.lang().send(p, problem.messageKey(), "seconds", String.valueOf(plugin.upgrades().cooldownLeft(p, gui.station())));
            gui.refresh();
            return;
        }
        StationData station = gui.station();
        PathData path = gui.path();

        gui.lock();   // from here every click on the GUI is refused
        UpgradeEngine.Outcome outcome = engine.run(p, station, path, gui.plan(), gui.collect(), gui.multiplier());
        if (outcome == null) {   // nothing was taken
            gui.unlock();
            return;
        }
        gui.clearSlots();        // everything is accounted for in outcome.rewards()
        plugin.upgrades().startCooldown(p, station);
        p.closeInventory();      // the close handler ignores a locked GUI
        plugin.loading().start(p, station, path, outcome, false);
    }

    /** GUI-less upgrade: the input and item costs come from the inventory. */
    public UpgradeResult attempt(Player p, UpgradeContext ctx) {
        StationData station = plugin.upgrades().get(ctx.stationId());
        PathData path = station == null ? null : station.path(ctx.pathId());
        if (!plugin.upgrades().enabled() || path == null) return new UpgradeResult(UpgradeResult.Status.UNKNOWN_PATH, null);
        if (plugin.loading().isUpgrading(p) || !plugin.upgrades().canUse(p, station)
                || plugin.upgrades().cooldownLeft(p, station) > 0 || plugin.upgrades().deniedHere(p, station) != null) {
            return new UpgradeResult(UpgradeResult.Status.REQUIREMENTS_NOT_MET, null);
        }
        RunePlan plan = RunePlan.of(path, null, 1);

        // find the stacks in the inventory (copies, nothing is removed yet)
        ItemStack[] contents = p.getInventory().getStorageContents();
        int inputSlot = -1;
        for (int i = 0; i < contents.length && inputSlot < 0; i++) {
            if (Items.isReal(contents[i]) && path.input().matches(MmoItems.identify(contents[i]))) inputSlot = i;
        }
        if (inputSlot < 0) return new UpgradeResult(UpgradeResult.Status.REQUIREMENTS_NOT_MET, null);
        int[] used = new int[contents.length];
        used[inputSlot] = Math.min(1, contents[inputSlot].getAmount());
        ItemStack input = contents[inputSlot].clone();
        input.setAmount(1);

        List<ItemStack> runes = new ArrayList<>();
        for (RunePlan.Spec spec : plan.specs()) {
            int need = spec.required();
            ItemStack gathered = null;
            for (int i = 0; i < contents.length && need > 0; i++) {
                ItemStack s = contents[i];
                if (!Items.isReal(s) || !CostService.matches(spec.cost(), s)) continue;
                int free = s.getAmount() - used[i];
                if (free <= 0) continue;
                int take = Math.min(free, need);
                used[i] += take;
                need -= take;
                if (gathered == null) {
                    gathered = s.clone();
                    gathered.setAmount(take);
                } else {
                    gathered.setAmount(gathered.getAmount() + take);
                }
            }
            if (need > 0) return new UpgradeResult(UpgradeResult.Status.REQUIREMENTS_NOT_MET, null);
            runes.add(gathered);
        }

        UpgradeEngine.Outcome outcome = engine.run(p, station, path, plan, new UpgradeInputs(input, runes, List.of(), null), 1);
        if (outcome == null) return new UpgradeResult(UpgradeResult.Status.CANCELLED, null);

        // the rewards already contain every leftover, so the stacks used are removed completely
        for (int i = 0; i < contents.length; i++) {
            if (used[i] <= 0) continue;
            ItemStack s = contents[i];
            if (used[i] >= s.getAmount()) p.getInventory().setItem(i, null);
            else {
                ItemStack rest = s.clone();
                rest.setAmount(s.getAmount() - used[i]);
                p.getInventory().setItem(i, rest);
            }
        }
        plugin.upgrades().startCooldown(p, station);
        plugin.loading().start(p, station, path, outcome, ctx.skipLoading());
        return new UpgradeResult(outcome.success() ? UpgradeResult.Status.SUCCESS : UpgradeResult.Status.FAILED, outcome.output());
    }

    /** Parts of the GUI-less API for hooks that need the unwrapped optional. */
    public Optional<StationData> station(String id) {
        return Optional.ofNullable(plugin.upgrades().get(id));
    }
}
