package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.event.UpgradePreEvent;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.util.ChanceMath;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The transaction of one upgrade attempt, independent of any GUI: validate, pick the result, compute the chance,
 * take money / levels, roll ONCE and work out what goes back to the player. It never touches slots or the
 * inventory: the caller clears its slots afterwards and hands out {@link Outcome#rewards()} when the loading ends.
 *
 * With a multiplier above 1 (the "result amount" button) one attempt covers the whole batch: the result amount and
 * (unless the path says scale-costs: false) the input, item costs, money and levels are multiplied, and the single
 * roll decides the whole batch.
 */
public final class UpgradeEngine {
    /**
     * @param rewards       every item the player gets at the end (leftovers, survivors, refunds, the result)
     * @param costs         the costs as charged (already multiplied), used for refunds
     * @param refundPercent share of money / levels / custom costs that is paid back (failure only)
     * @param consumed      plain-text list of what was used up (for the log and UpgradeResultEvent)
     */
    public record Outcome(boolean success, boolean protectionUsed, double chance, double bonus, List<ItemStack> rewards,
                          ItemStack inputBefore, ItemStack output, String outputName, int outputAmount, int multiplier,
                          List<Cost> costs, int refundPercent, List<String> consumed) {}

    private static final Map<String, String> NBT_KEYS = Map.of(
            "soulbound", "MMOITEMS_SOULBOUND",
            "gems", "MMOITEMS_GEM_STONES",
            "upgrade-level", "MMOITEMS_UPGRADE");

    private final OctaMMOPlugin plugin;

    public UpgradeEngine(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    /** The multiplier that applies to costs: the batch size, or 1 when the path does not scale its costs. */
    public static int costMultiplier(PathData path, int multiplier) {
        return path.scaleCosts() ? Math.max(1, multiplier) : 1;
    }

    /** Input pieces one attempt needs. */
    public static int inputNeeded(PathData path, int multiplier) {
        return path.consumeInput() ? costMultiplier(path, multiplier) : 1;
    }

    /** What is wrong with these inputs, or null when the attempt can start (suffix of upgrade.problem.*). */
    public String problem(Player p, PathData path, RunePlan plan, UpgradeInputs in, int multiplier) {
        if (Items.isEmpty(in.input()) || !path.input().matches(MmoItems.identify(in.input()))
                || in.input().getAmount() < inputNeeded(path, multiplier)) return "no-input";
        for (int i = 0; i < plan.specs().size(); i++) {
            ItemStack s = i < in.runes().size() ? in.runes().get(i) : null;
            RunePlan.Spec spec = plan.specs().get(i);
            if (Items.isEmpty(s) || !CostService.matches(spec.cost(), s) || s.getAmount() < spec.required()) return "runes";
        }
        for (Requirement r : path.requirements()) if (!r.test(p)) return "requirements";
        List<Cost> costs = CostService.scale(path.costs(), costMultiplier(path, multiplier));
        boolean needsMoney = costs.stream().anyMatch(c -> c instanceof Cost.Money m && m.amount() > 0);
        if (needsMoney && !plugin.economyAvailable()) return "economy";
        for (Cost c : costs) if (!plugin.costService().hasNonItem(p, c)) return "costs";
        return null;
    }

    /** Booster pieces per definition (capped by max-items), in definition order. */
    public int[] boosterCounts(PathData path, List<ItemStack> boosters) {
        List<Defs.BoosterDef> defs = path.chance().boosters();
        int[] have = new int[defs.size()];
        for (ItemStack s : boosters) {
            int d = defIndex(defs, s);
            if (d >= 0) have[d] += s.getAmount();
        }
        for (int i = 0; i < have.length; i++) have[i] = ChanceMath.counted(have[i], defs.get(i).maxItems());
        return have;
    }

    public double boosterBonus(PathData path, int[] counted) {
        double bonus = 0;
        for (int i = 0; i < counted.length; i++) bonus += counted[i] * path.chance().boosters().get(i).add();
        return bonus;
    }

    private static int defIndex(List<Defs.BoosterDef> defs, ItemStack s) {
        if (Items.isEmpty(s)) return -1;
        for (int i = 0; i < defs.size(); i++) if (CostService.matches(defs.get(i).item(), s)) return i;
        return -1;
    }

    /** @return the outcome, or null when nothing was taken (a message was sent to the player). */
    public Outcome run(Player p, StationData station, PathData path, RunePlan plan, UpgradeInputs in, int multiplier) {
        int m = Math.max(1, multiplier);
        String problem = problem(p, path, plan, in, m);
        if (problem != null) {
            plugin.lang().send(p, "upgrade.problem." + problem);
            return null;
        }

        // The result must exist BEFORE anything is taken, so a broken output never eats the player's items.
        Defs.ResultDef result = pickResult(path);
        ItemStack output = MmoItems.create(result.ref());
        if (output == null) {
            plugin.lang().send(p, "upgrade.output-missing", "item", result.ref().key());
            plugin.getLogger().warning("Path '" + station.id() + "/" + path.id() + "': output " + result.ref().key() + " does not exist in MMOItems.");
            return null;
        }
        ItemStack downgrade = null;
        if (path.fail().input() == Defs.InputFail.DOWNGRADE) {
            downgrade = MmoItems.create(path.fail().downgrade());
            if (downgrade == null) {
                plugin.lang().send(p, "upgrade.output-missing", "item", path.fail().downgrade().key());
                return null;
            }
        }

        int[] counted = boosterCounts(path, in.boosters());
        double bonus = boosterBonus(path, counted);
        double chance = ChanceMath.total(path.chance().base(), bonus, path.chance().maxTotal());
        UpgradePreEvent pre = new UpgradePreEvent(p, station, path, chance, bonus);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return null;
        chance = pre.getChance();

        int costMult = costMultiplier(path, m);
        List<Cost> costs = CostService.scale(path.costs(), costMult);

        // ---- point of no return
        if (!plugin.costService().consumeNonItems(p, costs)) {
            plugin.lang().send(p, "upgrade.payment-failed");
            return null;
        }
        boolean success = ThreadLocalRandom.current().nextDouble(100.0) < chance;   // the ONLY roll

        List<ItemStack> rewards = new ArrayList<>();
        List<String> consumed = new ArrayList<>();
        Defs.FailDef fail = path.fail();

        // protection: only when it saves an item that would be lost
        boolean protectionUsed = false;
        ItemStack prot = in.protection();
        if (!success && fail.protection() != null && fail.protection().keepsInput() && !Items.isEmpty(prot)
                && CostService.matches(fail.protection().item(), prot)
                && (fail.input() == Defs.InputFail.CONSUME || fail.input() == Defs.InputFail.DOWNGRADE)) {
            protectionUsed = true;
        }

        // input
        ItemStack input = in.input();
        int inputUsed;
        if (success) inputUsed = path.consumeInput() ? costMult : 0;
        else inputUsed = switch (fail.input()) {
            case KEEP -> 0;
            case CONSUME, DOWNGRADE -> protectionUsed ? 0 : costMult;
            case BREAK -> costMult;
        };
        addLeft(rewards, input, inputUsed);
        if (inputUsed > 0) consumed.add(inputUsed + "x input " + path.input().key());
        if (!success && inputUsed > 0 && fail.input() == Defs.InputFail.DOWNGRADE) addStacks(rewards, downgrade, inputUsed);

        // rune items
        for (int i = 0; i < plan.specs().size(); i++) {
            RunePlan.Spec spec = plan.specs().get(i);
            ItemStack s = in.runes().get(i);
            int used = spec.required();
            if (!success) {
                used = switch (fail.costs()) {
                    case CONSUME -> spec.required();
                    case REFUND -> 0;
                    case PARTIAL -> spec.required() - ChanceMath.percentOf(spec.required(), fail.refundPercent());
                };
            }
            addLeft(rewards, s, used);
            if (used > 0) consumed.add(used + "x " + plugin.costService().nameOf(spec.cost()));
        }

        // boosters
        Defs.BoosterConsume mode = path.chance().consume();
        boolean consumeBoosters = mode == Defs.BoosterConsume.ALWAYS || (mode == Defs.BoosterConsume.SUCCESS_ONLY && success);
        List<Defs.BoosterDef> defs = path.chance().boosters();
        List<List<ItemStack>> byDef = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) byDef.add(new ArrayList<>());
        for (ItemStack s : in.boosters()) {
            if (Items.isEmpty(s)) continue;
            int d = defIndex(defs, s);
            if (d >= 0) byDef.get(d).add(s);
            else rewards.add(s.clone());
        }
        for (int d = 0; d < defs.size(); d++) {
            List<ItemStack> stacks = byDef.get(d);
            int[] amounts = stacks.stream().mapToInt(ItemStack::getAmount).toArray();
            int[] taken = ChanceMath.distribute(amounts, consumeBoosters ? counted[d] : 0);
            for (int i = 0; i < stacks.size(); i++) addLeft(rewards, stacks.get(i), taken[i]);
            if (consumeBoosters && counted[d] > 0) consumed.add(counted[d] + "x booster " + plugin.costService().nameOf(defs.get(d).item()));
        }

        // protection item
        if (!Items.isEmpty(prot)) {
            addLeft(rewards, prot, protectionUsed ? 1 : 0);
            if (protectionUsed) consumed.add("1x protection");
        }

        // money / levels / custom: consumed above; on failure a share may come back when the loading ends
        int refundPercent = 0;
        if (!success) {
            refundPercent = switch (fail.costs()) {
                case CONSUME -> 0;
                case REFUND -> 100;
                case PARTIAL -> fail.refundPercent();
            };
        }
        for (Cost c : costs) {
            if (!CostService.isItemCost(c)) consumed.add(plugin.costService().describe(c));
        }

        ItemStack given = null;
        int outputAmount = result.ref().amount() * m;   // the file's amount, once per step of the multiplier
        if (success) {
            given = preserve(input, output, result.preserve());
            addStacks(rewards, given, outputAmount);
        }
        return new Outcome(success, protectionUsed, chance, bonus, rewards, input.clone(), given,
                MmoItems.displayName(result.ref()), outputAmount, m, costs, refundPercent, consumed);
    }

    private static void addLeft(List<ItemStack> rewards, ItemStack stack, int used) {
        if (Items.isEmpty(stack)) return;
        int left = stack.getAmount() - used;
        if (left <= 0) return;
        ItemStack copy = stack.clone();
        copy.setAmount(left);
        rewards.add(copy);
    }

    /** {@code amount} copies of the stack, split by its max stack size. */
    private static void addStacks(List<ItemStack> rewards, ItemStack stack, int amount) {
        int max = Math.max(1, stack.getMaxStackSize());
        while (amount > 0) {
            ItemStack copy = stack.clone();
            copy.setAmount(Math.min(max, amount));
            rewards.add(copy);
            amount -= copy.getAmount();
        }
    }

    private Defs.ResultDef pickResult(PathData path) {
        List<Defs.ResultDef> results = path.results();
        if (path.mode() == Defs.OutputMode.SINGLE || results.size() == 1) return results.get(0);
        int total = results.stream().mapToInt(Defs.ResultDef::weight).sum();
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (Defs.ResultDef r : results) {
            roll -= r.weight();
            if (roll < 0) return r;
        }
        return results.get(results.size() - 1);
    }

    private ItemStack preserve(ItemStack from, ItemStack to, List<String> tokens) {
        if (tokens.isEmpty()) return to;
        boolean durability = false;
        List<String> keys = new ArrayList<>();
        for (String t : tokens) {
            String token = t.toLowerCase(java.util.Locale.ROOT);
            if (token.equals("durability")) durability = true;
            else keys.add(NBT_KEYS.getOrDefault(token, t));
        }
        return MmoItems.preserve(from, to, keys, durability);
    }
}
