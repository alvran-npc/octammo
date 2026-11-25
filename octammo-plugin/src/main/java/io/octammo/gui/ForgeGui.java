package io.octammo.gui;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.event.UpgradeCloseEvent;
import io.octammo.core.Items;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.core.Sounds;
import io.octammo.upgrade.Cost;
import io.octammo.upgrade.CostService;
import io.octammo.upgrade.Defs;
import io.octammo.upgrade.PathData;
import io.octammo.upgrade.Requirement;
import io.octammo.upgrade.RunePlan;
import io.octammo.upgrade.StationData;
import io.octammo.upgrade.UpgradeInputs;
import io.octammo.upgrade.UpgradeEngine;
import io.octammo.util.ChanceMath;
import io.octammo.util.Multiplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * "Altar Forge": 6-row upgrade GUI for ONE path of a station. No animation and no repeating task: the GUI only
 * re-renders the slots whose content changed. The player puts the item to upgrade, every item cost, boosters and an
 * optional protection into the GUI, then presses the upgrade button (the GUI closes and the title loading starts).
 */
public final class ForgeGui extends OctaGui {
    public static final Set<String> SINGLES = Set.of("info", "input", "output-preview", "requirements", "chance",
            "protection", "upgrade-button", "back", "cancel");
    public static final Set<String> LISTS = Set.of("rune-ring", "booster", "chance-gauge");

    public enum Problem {
        NONE("none"), NO_INPUT("no-input"), RUNES("runes"), PERMISSION("permission"), WORLD("world"), REGION("region"),
        COOLDOWN("cooldown"), REQUIREMENTS("requirements"), ECONOMY("economy"), COSTS("costs");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        /** Lang key of the hint, "upgrade.problem.<key>". */
        public String messageKey() {
            return "upgrade.problem." + key;
        }
    }

    private static final ItemDef D_INFO = ItemDef.of(Material.ENCHANTING_TABLE, "{gui.info.name}", "{gui.info.lore}");
    private static final ItemDef D_INPUT = ItemDef.of(Material.ITEM_FRAME, "{gui.input.name}", "{gui.input.lore}");
    private static final ItemDef D_RUNE_EMPTY = ItemDef.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "{gui.rune.empty.name}", "{gui.rune.empty.lore}");
    private static final ItemDef D_RUNE_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, " ");
    private static final ItemDef D_RUNE_MISSING = ItemDef.of(Material.RED_STAINED_GLASS_PANE, "{gui.rune.missing.name}", "{gui.rune.missing.lore}");
    private static final ItemDef D_RUNE_OK = ItemDef.of(Material.LIME_STAINED_GLASS_PANE, "{gui.rune.ok.name}", "{gui.rune.ok.lore}");
    private static final ItemDef D_BOOSTER = ItemDef.of(Material.GLOWSTONE_DUST, "{gui.booster.name}", "{gui.booster.lore}");
    private static final ItemDef D_BOOSTER_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, "{gui.booster.locked}");
    private static final ItemDef D_PROTECTION = ItemDef.of(Material.SHIELD, "{gui.protection.name}", "{gui.protection.lore}");
    private static final ItemDef D_PROTECTION_LOCKED = ItemDef.of(Material.BLACK_STAINED_GLASS_PANE, "{gui.protection.locked}");
    private static final ItemDef D_REQUIREMENTS = ItemDef.of(Material.PAPER, "{gui.requirements.name}", "{gui.requirements.header}", "{lines}");
    private static final ItemDef D_CHANCE = ItemDef.of(Material.CLOCK, "{gui.chance.name}", "{gui.chance.lore}");
    private static final ItemDef D_OUTPUT_MISSING = ItemDef.of(Material.BARRIER, "{gui.output.missing}");
    private static final ItemDef D_GO_READY = ItemDef.of(Material.LIME_CONCRETE, "{gui.upgrade-button.ready}", "{gui.upgrade-button.ready-lore}").withGlow();
    private static final ItemDef D_GO_NOT_READY = ItemDef.of(Material.RED_CONCRETE, "{gui.upgrade-button.not-ready}", "{gui.upgrade-button.not-ready-lore}");
    private static final ItemDef D_AUTOFILL = ItemDef.of(Material.HOPPER, "{gui.autofill.name}", "{gui.autofill.lore}");
    private static final ItemDef D_AMOUNT = ItemDef.of(Material.GOLD_INGOT, "{gui.amount.name}", "{gui.amount.lore}");
    private static final ItemDef D_BACK = ItemDef.of(Material.ARROW, "{gui.back}");
    private static final ItemDef D_CANCEL = ItemDef.of(Material.BARRIER, "{gui.cancel}");

    private final StationData station;
    private final PathData path;
    private final GuiLayout layout;
    private RunePlan plan;
    private int multiplier = 1;
    private final int maxMultiplier;
    private final int autofillSlot, amountSlot;
    private final Set<Integer> interactive = new HashSet<>();
    private final Map<Integer, String> states = new HashMap<>();
    private final Map<Material, ItemStack> panes = new java.util.EnumMap<>(Material.class);

    private final int inSlot, infoSlot, outSlot, reqSlot, chanceSlot, protectionSlot, goSlot, backSlot, cancelSlot;
    private final List<Integer> ring, gauge, activeBoosterSlots, allBoosterSlots, runeStatus;
    private boolean locked;

    /** @return the GUI, or null when the path needs more rune slots than the layout offers. */
    public static ForgeGui create(OctaMMOPlugin plugin, StationData station, PathData path, Player player) {
        GuiLayout layout = plugin.guis().forge(station.gui());
        RunePlan plan = RunePlan.of(path, layout.slots("rune-ring"), 1);
        return plan == null ? null : new ForgeGui(plugin, station, path, layout, plan, player);
    }

    private ForgeGui(OctaMMOPlugin plugin, StationData station, PathData path, GuiLayout layout, RunePlan plan, Player player) {
        super(plugin, player);
        this.station = station;
        this.path = path;
        this.layout = layout;
        this.plan = plan;
        inSlot = layout.slot("input");
        infoSlot = layout.slot("info");
        outSlot = layout.slot("output-preview");
        reqSlot = layout.slot("requirements");
        chanceSlot = layout.slot("chance");
        protectionSlot = layout.slot("protection");
        goSlot = layout.slot("upgrade-button");
        backSlot = layout.slot("back");
        cancelSlot = layout.slot("cancel");
        ring = layout.slots("rune-ring");
        gauge = layout.slots("chance-gauge");
        allBoosterSlots = layout.slots("booster");
        runeStatus = layout.slots("rune-status");
        autofillSlot = layout.hasSlot("autofill") ? layout.slot("autofill") : -1;
        amountSlot = layout.hasSlot("amount") ? layout.slot("amount") : -1;
        maxMultiplier = computeMaxMultiplier();
        activeBoosterSlots = allBoosterSlots.subList(0, Math.min(path.chance().boosterSlots(), allBoosterSlots.size()));

        rebuildInteractive();

        Component title = Lang.color(plugin.lang().expand(layout.title(), player, Map.of("station", station.displayName())));
        this.inventory = Bukkit.createInventory(this, 54, title);
        drawStatic();
        refresh();
    }

    public StationData station() { return station; }
    public PathData path() { return path; }
    public RunePlan plan() { return plan; }
    public boolean locked() { return locked; }
    /** Batch size chosen with the result amount button (1 = the file's default). */
    public int multiplier() { return multiplier; }

    // ------------------------------------------------------------------ drawing

    private Map<String, String> base() {
        Map<String, String> m = new HashMap<>();
        m.put("station", station.displayName());
        m.put("path", path.displayName());
        m.put("description", String.join("\n", path.description()));
        return m;
    }

    private Map<String, String> with(Map<String, String> m, String... kv) {
        Map<String, String> copy = new HashMap<>(m);
        for (int i = 0; i + 1 < kv.length; i += 2) copy.put(kv[i], kv[i + 1]);
        return copy;
    }

    private ItemStack item(String key, ItemDef fallback, Map<String, String> ph) {
        return layout.item(key, fallback).build(plugin, player, ph);
    }

    private ItemStack pane(Material m) {
        return panes.computeIfAbsent(m, k -> Items.make(k, Component.space(), null));
    }

    private ItemStack buildOutputPreview() {
        Defs.ResultDef first = path.results().get(0);
        ItemStack real = MmoItems.create(first.ref());
        if (real == null) return item("output-missing", D_OUTPUT_MISSING, base());
        int total = Multiplier.total(first.ref().amount(), multiplier);
        real.setAmount(Math.max(1, Math.min(total, real.getMaxStackSize())));
        ItemMeta meta = real.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        Map<String, String> ph = with(base(), "output-name", MmoItems.displayName(first.ref()),
                "chance", fmt(path.chance().base()),
                "results", String.valueOf(path.results().size()),
                "amount", String.valueOf(total));
        lore.addAll(plugin.lang().renderLines(List.of(path.mode() == Defs.OutputMode.WEIGHTED ? "{gui.output.lore-weighted}" : "{gui.output.lore}"), player, ph));
        meta.lore(lore);
        real.setItemMeta(meta);
        return Items.markVirtual(real);
    }

    private void drawStatic() {
        Set<Integer> functional = layout.functionalSlots();
        for (int i = 0; i < 54; i++) {
            if (functional.contains(i)) continue;
            boolean edge = i < 9 || i % 9 == 0 || i % 9 == 8;
            inventory.setItem(i, pane(edge ? layout.border() : layout.filler()));
        }
        inventory.setItem(infoSlot, item("info", D_INFO, base()));
        inventory.setItem(backSlot, item("back", D_BACK, base()));
        inventory.setItem(cancelSlot, item("cancel", D_CANCEL, base()));
    }

    /** Sets a virtual item only when it is missing or its state changed, never over a real item. */
    private void put(int slot, String state, Supplier<ItemStack> supplier) {
        ItemStack cur = inventory.getItem(slot);
        if (Items.isReal(cur)) {
            states.remove(slot);
            return;
        }
        if (state.equals(states.get(slot)) && Items.isVirtual(cur)) return;
        inventory.setItem(slot, supplier.get());
        states.put(slot, state);
    }

    private ItemStack real(int slot) {
        ItemStack s = inventory.getItem(slot);
        return Items.isReal(s) ? s : null;
    }

    private int have(RunePlan.Spec r) {
        ItemStack s = real(r.slot());
        return s != null && CostService.matches(r.cost(), s) ? s.getAmount() : 0;
    }

    private List<ItemStack> boosterStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (int s : activeBoosterSlots) {
            ItemStack st = real(s);
            if (st != null) out.add(st);
        }
        return out;
    }

    public Defs.ProtectionDef activeProtectionDef() {
        return path.fail().protection();
    }

    /** The protection stack in the slot when it is a valid protection item, else null. */
    public ItemStack activeProtection() {
        if (path.fail().protection() == null) return null;
        ItemStack s = real(protectionSlot);
        return s != null && CostService.matches(path.fail().protection().item(), s) ? s : null;
    }

    /** Re-renders only what changed. Does nothing while an upgrade is being started. */
    public void refresh() {
        if (locked) return;
        Map<String, String> base = base();
        Problem problem = problem();

        if (real(inSlot) == null) {
            String name = MmoItems.displayName(path.input());
            put(inSlot, "in|" + name, () -> item("input-empty", D_INPUT, with(base, "item", name)));
        }

        Set<Integer> active = new HashSet<>();
        for (RunePlan.Spec r : plan.specs()) {
            active.add(r.slot());
            String name = plugin.costService().nameOf(r.cost());
            put(r.slot(), "rune|" + name + "|" + r.required(),
                    () -> item("rune-empty", D_RUNE_EMPTY, with(base, "item", name, "amount", String.valueOf(r.required()), "have", "0")));
        }
        for (int s : ring) {
            if (!active.contains(s)) put(s, "rune-locked", () -> item("rune-locked", D_RUNE_LOCKED, base));
        }
        for (int i = 0; i < runeStatus.size(); i++) {
            int s = runeStatus.get(i);
            if (i >= plan.specs().size()) {
                put(s, "st-locked", () -> item("rune-locked", D_RUNE_LOCKED, base));
                continue;
            }
            RunePlan.Spec r = plan.specs().get(i);
            int have = have(r);
            String name = plugin.costService().nameOf(r.cost());
            Map<String, String> ph = with(base, "item", name, "amount", String.valueOf(r.required()), "have", String.valueOf(have));
            if (have <= 0) put(s, "st-empty", () -> item("rune-status-empty", D_RUNE_LOCKED, base));
            else if (have < r.required()) put(s, "st-missing|" + have, () -> item("rune-missing", D_RUNE_MISSING, ph));
            else put(s, "st-ok|" + have, () -> item("rune-ok", D_RUNE_OK, ph));
        }

        for (int s : allBoosterSlots) {
            if (activeBoosterSlots.contains(s)) put(s, "booster", () -> item("booster-empty", D_BOOSTER, base));
            else put(s, "booster-locked", () -> item("booster-locked", D_BOOSTER_LOCKED, base));
        }
        if (interactive.contains(protectionSlot)) put(protectionSlot, "prot", () -> item("protection-empty", D_PROTECTION, base));
        else put(protectionSlot, "prot-locked", () -> item("protection-locked", D_PROTECTION_LOCKED, base));

        // requirement checklist: requirements, then costs
        List<String> lines = new ArrayList<>();
        for (Requirement r : path.requirements()) {
            lines.add(plugin.lang().format(r.test(player) ? "gui.requirements.line-ok" : "gui.requirements.line-missing", "text", r.text()));
        }
        List<Cost> scaled = scaledCosts();
        for (int ci = 0; ci < path.costs().size(); ci++) {
            Cost c = path.costs().get(ci);
            Cost shown = scaled.get(ci);
            boolean ok;
            String text;
            if (CostService.isItemCost(c)) {
                int need = CostService.amountOf(shown);
                int have = 0;
                for (RunePlan.Spec r : plan.specs()) if (r.cost() == c) have += have(r);
                ok = have >= need;
                text = plugin.lang().format("gui.requirements.progress", "text", plugin.costService().describe(shown),
                        "have", String.valueOf(have), "amount", String.valueOf(need));
            } else {
                ok = plugin.costService().hasNonItem(player, shown);
                text = plugin.costService().describe(shown);
            }
            lines.add(plugin.lang().format(ok ? "gui.requirements.line-ok" : "gui.requirements.line-missing", "text", text));
        }
        if (lines.isEmpty()) lines.add(plugin.lang().format("gui.requirements.none"));
        String joined = String.join("\n", lines);
        put(reqSlot, "req|" + joined, () -> item("requirements", D_REQUIREMENTS, with(base, "lines", joined)));

        // result preview (amount follows the multiplier) and the two helper buttons
        put(outSlot, "out|" + multiplier, this::buildOutputPreview);
        if (autofillSlot >= 0) put(autofillSlot, "autofill", () -> item("autofill", D_AUTOFILL, base));
        if (amountSlot >= 0) {
            int step = path.results().get(0).ref().amount();
            put(amountSlot, "amount|" + multiplier + "|" + maxMultiplier, () -> item("amount", D_AMOUNT, with(base,
                    "expression", Multiplier.expression(step, multiplier), "total", String.valueOf(Multiplier.total(step, multiplier)),
                    "step", String.valueOf(step), "multiplier", String.valueOf(multiplier), "max", String.valueOf(maxMultiplier))));
        }

        // chance (base + stacked boosters) and the static gauge
        int[] counted = plugin.service().engine().boosterCounts(path, boosterStacks());
        double bonus = plugin.service().engine().boosterBonus(path, counted);
        double total = ChanceMath.total(path.chance().base(), bonus, path.chance().maxTotal());
        int boosterCount = 0;
        for (int c : counted) boosterCount += c;
        final int boosterItems = boosterCount;
        put(chanceSlot, "ch|" + total + "|" + bonus + "|" + boosterItems, () -> item("chance", D_CHANCE, with(base,
                "base", fmt(path.chance().base()), "booster", fmt(bonus), "booster-count", String.valueOf(boosterItems),
                "chance", fmt(total), "bar", bar(total))));
        renderGauge(total);

        boolean ready = problem == Problem.NONE;
        String reason = plugin.lang().format(problem.messageKey(), "seconds", String.valueOf(plugin.upgrades().cooldownLeft(player, station)));
        put(goSlot, "go|" + problem + "|" + (problem == Problem.COOLDOWN ? reason : ""), () -> ready
                ? item("upgrade-button.ready", D_GO_READY, base)
                : item("upgrade-button.not-ready", D_GO_NOT_READY, with(base, "reason", reason)));
    }

    private void renderGauge(double chance) {
        int n = ChanceMath.gaugePanels(chance, gauge.size());
        String zone = ChanceMath.gaugeZone(chance);
        Material on = layout.material("gauge." + zone, switch (zone) {
            case "low" -> Material.RED_STAINED_GLASS_PANE;
            case "mid" -> Material.YELLOW_STAINED_GLASS_PANE;
            default -> Material.LIME_STAINED_GLASS_PANE;
        });
        Material off = layout.material("gauge.off", Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < gauge.size(); i++) {
            ItemStack want = pane(i < n ? on : off);
            if (!java.util.Objects.equals(inventory.getItem(gauge.get(i)), want)) inventory.setItem(gauge.get(i), want);
        }
    }

    public static String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String bar(double percent) {
        int filled = (int) Math.round(percent / 10.0);
        return "&a" + "\u25B0".repeat(Math.max(0, filled)) + "&8" + "\u25B1".repeat(Math.max(0, 10 - filled));
    }

    private void refreshLater() {
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    private void syncLater() {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    // ------------------------------------------------------------------ state

    public Problem problem() {
        ItemStack in = real(inSlot);
        if (in == null || !path.input().matches(MmoItems.identify(in)) || in.getAmount() < inputNeeded()) return Problem.NO_INPUT;
        for (RunePlan.Spec r : plan.specs()) if (have(r) < r.required()) return Problem.RUNES;
        if (!plugin.upgrades().canUse(player, station)) return Problem.PERMISSION;
        String denied = plugin.upgrades().deniedHere(player, station);
        if (denied != null) return denied.contains("world") ? Problem.WORLD : Problem.REGION;
        if (plugin.upgrades().cooldownLeft(player, station) > 0) return Problem.COOLDOWN;
        for (Requirement r : path.requirements()) if (!r.test(player)) return Problem.REQUIREMENTS;
        List<Cost> scaled = scaledCosts();
        boolean needsMoney = scaled.stream().anyMatch(c -> c instanceof Cost.Money m && m.amount() > 0);
        if (needsMoney && !plugin.economyAvailable()) return Problem.ECONOMY;
        for (Cost c : scaled) if (!plugin.costService().hasNonItem(player, c)) return Problem.COSTS;
        return Problem.NONE;
    }

    /** Copies of everything in the interactive slots, for the engine. Nothing is removed here. */
    public UpgradeInputs collect() {
        ItemStack in = real(inSlot);
        List<ItemStack> runes = new ArrayList<>();
        for (RunePlan.Spec r : plan.specs()) {
            ItemStack s = real(r.slot());
            runes.add(s == null ? null : s.clone());
        }
        List<ItemStack> boosters = new ArrayList<>();
        for (ItemStack s : boosterStacks()) boosters.add(s.clone());
        ItemStack prot = real(protectionSlot);
        return new UpgradeInputs(in == null ? null : in.clone(), runes, boosters, interactive.contains(protectionSlot) && prot != null ? prot.clone() : null);
    }

    public void lock() {
        locked = true;
    }

    /** Aborts a start that failed before anything was taken. */
    public void unlock() {
        locked = false;
        refresh();
    }

    /** Empties every interactive slot: the outcome already accounts for all of it. */
    public void clearSlots() {
        for (int slot : interactive) inventory.setItem(slot, null);
        states.clear();
    }

    // ------------------------------------------------------------------ clicks

    private boolean accepts(int slot, ItemStack item) {
        if (!Items.isReal(item)) return false;
        if (slot == inSlot) return path.input().matches(MmoItems.identify(item));
        if (activeBoosterSlots.contains(slot)) {
            for (Defs.BoosterDef d : path.chance().boosters()) if (CostService.matches(d.item(), item)) return true;
            return false;
        }
        if (slot == protectionSlot) {
            return interactive.contains(slot) && CostService.matches(path.fail().protection().item(), item);
        }
        for (RunePlan.Spec r : plan.specs()) if (r.slot() == slot) return CostService.matches(r.cost(), item);
        return false;
    }

    private void reject(int slot) {
        if (slot == inSlot) {
            plugin.lang().send(player, "upgrade.gui.reject-input", "name", MmoItems.displayName(path.input()));
        } else if (activeBoosterSlots.contains(slot)) {
            plugin.lang().send(player, "upgrade.gui.reject-booster");
        } else if (slot == protectionSlot) {
            plugin.lang().send(player, "upgrade.gui.reject-protection");
        } else {
            for (RunePlan.Spec r : plan.specs()) {
                if (r.slot() == slot) {
                    plugin.lang().send(player, "upgrade.gui.reject-rune", "name", plugin.costService().nameOf(r.cost()));
                    return;
                }
            }
        }
    }

    /** The item that the click would move INTO the slot. */
    private ItemStack incoming(InventoryClickEvent e) {
        ClickType click = e.getClick();
        if (click == ClickType.LEFT || click == ClickType.RIGHT) return e.getCursor();
        if (click == ClickType.NUMBER_KEY) {
            int b = e.getHotbarButton();
            return b >= 0 ? player.getInventory().getItem(b) : null;
        }
        if (click == ClickType.SWAP_OFFHAND) return player.getInventory().getItemInOffHand();
        return null;
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (locked || player.getGameMode() == GameMode.CREATIVE) {
            e.setCancelled(true);
            return;
        }
        InventoryAction action = e.getAction();
        if (action == InventoryAction.NOTHING) return;
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.UNKNOWN) {
            e.setCancelled(true);
            return;
        }

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        if (clicked != inventory) {   // the player's own inventory
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                quickPlace(e);
                return;
            }
            refreshLater();
            return;
        }

        int slot = e.getRawSlot();
        if (slot == goSlot) {
            e.setCancelled(true);
            Sounds.play(plugin, player, "click");
            plugin.service().tryStart(this);
            return;
        }
        if (slot == backSlot) {
            e.setCancelled(true);
            back();
            return;
        }
        if (slot == cancelSlot) {
            e.setCancelled(true);
            closeNextTick();
            return;
        }
        if (slot == autofillSlot) {
            e.setCancelled(true);
            Sounds.play(plugin, player, "click");
            autoFill();
            return;
        }
        if (slot == amountSlot) {
            e.setCancelled(true);
            Sounds.play(plugin, player, "click");
            changeAmount(e.getClick());
            return;
        }
        if (!interactive.contains(slot)) {
            e.setCancelled(true);
            return;
        }

        ItemStack current = inventory.getItem(slot);
        if (!Items.isReal(current)) {      // empty or a placeholder: place by hand, never swap with the placeholder
            e.setCancelled(true);
            manualPlace(e, slot);
            return;
        }

        switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_SOME, PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY -> refreshLater();
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                ItemStack in = incoming(e);
                if (!Items.isEmpty(in) && !accepts(slot, in)) {
                    e.setCancelled(true);
                    reject(slot);
                } else {
                    refreshLater();
                }
            }
            default -> e.setCancelled(true);
        }
    }

    /** Places the incoming item into an empty slot by hand (the event is already cancelled). */
    private void manualPlace(InventoryClickEvent e, int slot) {
        ItemStack src = incoming(e);
        if (!Items.isReal(src)) return;
        if (!accepts(slot, src)) {
            reject(slot);
            return;
        }
        ClickType click = e.getClick();
        if (click == ClickType.LEFT) {
            inventory.setItem(slot, src.clone());
            player.setItemOnCursor(null);
        } else if (click == ClickType.RIGHT) {
            ItemStack one = src.clone();
            one.setAmount(1);
            inventory.setItem(slot, one);
            ItemStack rest = src.clone();
            rest.setAmount(src.getAmount() - 1);
            player.setItemOnCursor(rest.getAmount() > 0 ? rest : null);
        } else if (click == ClickType.NUMBER_KEY) {
            inventory.setItem(slot, src.clone());
            player.getInventory().setItem(e.getHotbarButton(), null);
        } else if (click == ClickType.SWAP_OFFHAND) {
            inventory.setItem(slot, src.clone());
            player.getInventory().setItemInOffHand(null);
        } else {
            return;
        }
        states.remove(slot);
        refresh();
        syncLater();
    }

    /** Shift-click from the player's inventory: input first, then runes, boosters, protection. */
    private void quickPlace(InventoryClickEvent e) {
        ItemStack src = e.getCurrentItem();
        if (!Items.isReal(src)) return;
        ItemStack remaining = src.clone();

        List<Integer> order = new ArrayList<>();
        order.add(inSlot);
        for (RunePlan.Spec r : plan.specs()) order.add(r.slot());
        order.addAll(activeBoosterSlots);
        if (interactive.contains(protectionSlot)) order.add(protectionSlot);

        for (int slot : order) {
            if (remaining.getAmount() <= 0) break;
            if (!accepts(slot, remaining)) continue;
            int max = Math.max(1, remaining.getMaxStackSize());
            ItemStack cur = real(slot);
            if (cur == null) {
                int n = Math.min(max, remaining.getAmount());
                ItemStack put = remaining.clone();
                put.setAmount(n);
                inventory.setItem(slot, put);
                remaining.setAmount(remaining.getAmount() - n);
                states.remove(slot);
            } else if (cur.isSimilar(remaining) && cur.getAmount() < max) {
                int n = Math.min(max - cur.getAmount(), remaining.getAmount());
                ItemStack merged = cur.clone();
                merged.setAmount(cur.getAmount() + n);
                inventory.setItem(slot, merged);
                remaining.setAmount(remaining.getAmount() - n);
            }
        }
        if (remaining.getAmount() != src.getAmount()) {
            e.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
            refresh();
            syncLater();
        } else {
            plugin.lang().send(player, "upgrade.gui.reject-quick");
        }
    }

    // ------------------------------------------------------------------ result amount (batch size)

    private void rebuildInteractive() {
        interactive.clear();
        interactive.add(inSlot);
        for (RunePlan.Spec r : plan.specs()) interactive.add(r.slot());
        interactive.addAll(activeBoosterSlots);
        if (path.fail().protection() != null) interactive.add(protectionSlot);
    }

    private int costMultiplier() {
        return UpgradeEngine.costMultiplier(path, multiplier);
    }

    private int inputNeeded() {
        return UpgradeEngine.inputNeeded(path, multiplier);
    }

    /** The path's costs multiplied for the current batch size. */
    private List<Cost> scaledCosts() {
        return CostService.scale(path.costs(), costMultiplier());
    }

    /** Highest batch size: config / path cap, the input's stack size and the number of rune slots. Custom costs cannot scale. */
    private int computeMaxMultiplier() {
        int cap = path.maxMultiplier() != null ? path.maxMultiplier() : plugin.getConfig().getInt("upgrade.max-multiplier", 16);
        cap = Math.max(1, cap);
        for (Cost c : path.costs()) if (c instanceof Cost.Custom) return 1;
        if (path.scaleCosts()) {
            if (path.consumeInput()) {
                ItemStack sample = MmoItems.create(path.input());
                cap = Math.min(cap, sample == null ? 1 : Math.max(1, sample.getMaxStackSize()));
            }
            cap = Math.min(cap, RunePlan.maxMultiplier(path, ring, cap));
        }
        return Math.max(1, cap);
    }

    /** Left click +1 step, right click -1 step, shift-click back to the file's default. */
    private void changeAmount(ClickType click) {
        int next = multiplier;
        if (click == ClickType.LEFT) next++;
        else if (click == ClickType.RIGHT) next--;
        else if (click.isShiftClick()) next = 1;
        next = Math.max(1, Math.min(maxMultiplier, next));
        if (next == multiplier) {
            if (click == ClickType.LEFT) plugin.lang().send(player, "upgrade.amount-max", "max", String.valueOf(maxMultiplier));
            return;
        }
        setMultiplier(next);
    }

    /** Re-plans the rune slots. Items that no longer fit their slot go back to the player, the others stay. */
    private void setMultiplier(int next) {
        RunePlan nextPlan = RunePlan.of(path, ring, UpgradeEngine.costMultiplier(path, next));
        if (nextPlan == null) return;
        Map<Integer, RunePlan.Spec> bySlot = new HashMap<>();
        for (RunePlan.Spec n : nextPlan.specs()) bySlot.put(n.slot(), n);
        for (RunePlan.Spec old : plan.specs()) {
            ItemStack stack = real(old.slot());
            RunePlan.Spec n = bySlot.get(old.slot());
            if (stack != null && (n == null || !CostService.sameItem(n.cost(), old.cost()))) {
                Items.giveOrDrop(player, stack);
                inventory.setItem(old.slot(), null);
            }
        }
        plan = nextPlan;
        multiplier = next;
        rebuildInteractive();
        states.clear();
        refresh();
    }

    // ------------------------------------------------------------------ auto fill

    /** Moves the item to upgrade and every item cost from the player's inventory into the slots (nothing extra is taken). */
    private void autoFill() {
        ItemStack[] inv = player.getInventory().getStorageContents();
        List<String> missing = new ArrayList<>();
        int moved = fillSlot(inv, inSlot, s -> path.input().matches(MmoItems.identify(s)), inputNeeded(),
                MmoItems.displayName(path.input()), missing);
        for (RunePlan.Spec r : plan.specs()) {
            moved += fillSlot(inv, r.slot(), s -> CostService.matches(r.cost(), s), r.required(),
                    plugin.costService().nameOf(r.cost()), missing);
        }
        player.getInventory().setStorageContents(inv);
        if (!missing.isEmpty()) plugin.lang().send(player, "upgrade.autofill-missing", "items", String.join(", ", missing));
        else if (moved > 0) plugin.lang().send(player, "upgrade.autofill-done");
        else plugin.lang().send(player, "upgrade.autofill-nothing");
        refresh();
        syncLater();
    }

    /** Tops one slot up to {@code needed} pieces from {@code inv} (a copy of the storage). @return pieces moved */
    private int fillSlot(ItemStack[] inv, int slot, Predicate<ItemStack> match, int needed, String name, List<String> missing) {
        ItemStack cur = real(slot);
        if (cur != null && !match.test(cur)) return 0;   // something else sits there, leave it alone
        int have = cur == null ? 0 : cur.getAmount();
        int want = needed - have;
        if (want <= 0) return 0;

        ItemStack template = cur;
        int taken = 0;
        for (int i = 0; i < inv.length && taken < want; i++) {
            ItemStack s = inv[i];
            if (Items.isEmpty(s) || !match.test(s)) continue;
            if (template != null && !template.isSimilar(s)) continue;   // one kind of item per slot
            if (template == null) template = s.clone();
            int n = Math.min(s.getAmount(), want - taken);
            taken += n;
            if (n >= s.getAmount()) {
                inv[i] = null;
            } else {
                ItemStack rest = s.clone();
                rest.setAmount(s.getAmount() - n);
                inv[i] = rest;
            }
        }
        if (taken > 0) {
            ItemStack put = template.clone();
            put.setAmount(have + taken);
            inventory.setItem(slot, put);
            states.remove(slot);
        }
        if (have + taken < needed) missing.add((needed - have - taken) + "x " + name);
        return taken;
    }

    /** Gives every real item in the interactive slots back to the player. */
    public void returnAll() {
        List<ItemStack> returned = new ArrayList<>();
        for (int slot : interactive) {
            ItemStack s = inventory.getItem(slot);
            if (Items.isReal(s)) {
                Items.giveOrDrop(player, s);
                returned.add(s.clone());
            }
            inventory.setItem(slot, null);
        }
        states.clear();
        if (!returned.isEmpty()) Bukkit.getPluginManager().callEvent(new UpgradeCloseEvent(player, station, path, returned));
    }

    private void back() {
        returnAll();
        if (station.pathData().size() > 1) switchTo(new PathSelectGui(plugin, station, player));
        else closeNextTick();
    }

    public void switchToConfirm() {
        switchTo(new ConfirmGui(plugin, this));
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (transition || locked) return;   // while locked everything was already taken by the upgrade
        returnAll();
    }
}
