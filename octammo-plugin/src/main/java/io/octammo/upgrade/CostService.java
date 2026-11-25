package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.CostType;
import io.octammo.core.MmoItems;
import io.octammo.util.ChanceMath;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Money, XP levels and custom costs are charged from the player; item costs live in the rune slots of the GUI
 * (or are taken from the inventory for GUI-less upgrades).
 */
public final class CostService {
    private final OctaMMOPlugin plugin;

    public CostService(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isItemCost(Cost c) {
        return c instanceof Cost.MmoItem || c instanceof Cost.Vanilla;
    }

    public static int amountOf(Cost c) {
        return switch (c) {
            case Cost.MmoItem i -> i.ref().amount();
            case Cost.Vanilla v -> v.amount();
            default -> 0;
        };
    }

    /** Every amount multiplied (the costs of a batch upgrade). Custom costs cannot be scaled and stay as they are. */
    public static List<Cost> scale(List<Cost> costs, int multiplier) {
        if (multiplier <= 1) return costs;
        List<Cost> out = new java.util.ArrayList<>();
        for (Cost c : costs) {
            out.add(switch (c) {
                case Cost.Money m -> new Cost.Money(m.amount() * multiplier, m.provider());
                case Cost.Levels l -> new Cost.Levels(l.amount() * multiplier);
                case Cost.MmoItem i -> new Cost.MmoItem(new io.octammo.core.ItemRef(i.ref().type(), i.ref().id(), i.ref().amount() * multiplier));
                case Cost.Vanilla v -> new Cost.Vanilla(v.material(), v.amount() * multiplier);
                case Cost.Custom custom -> custom;
            });
        }
        return out;
    }

    /** Same kind of item (ignoring the amount). */
    public static boolean sameItem(Cost a, Cost b) {
        if (a instanceof Cost.MmoItem x && b instanceof Cost.MmoItem y) return x.ref().matches(y.ref());
        if (a instanceof Cost.Vanilla x && b instanceof Cost.Vanilla y) return x.material() == y.material();
        return false;
    }

    /** Does this stack count as the item cost? (plain vanilla item: no meta, not an MMOItem) */
    public static boolean matches(Cost cost, ItemStack s) {
        if (s == null) return false;
        return switch (cost) {
            case Cost.MmoItem i -> i.ref().matches(MmoItems.identify(s));
            case Cost.Vanilla v -> s.getType() == v.material() && !s.hasItemMeta() && MmoItems.identify(s) == null;
            default -> false;
        };
    }

    /** Money / levels / custom check. Item costs always return true here. */
    public boolean hasNonItem(Player p, Cost cost) {
        return switch (cost) {
            case Cost.Money m -> m.amount() <= 0 || (plugin.economyAvailable() && plugin.economy().has(p, m.amount()));
            case Cost.Levels l -> p.getLevel() >= l.amount();
            case Cost.Custom c -> {
                CostType type = plugin.registry().cost(c.id());
                yield type != null && type.has(p, c.config());
            }
            default -> true;
        };
    }

    /** Takes money first (the only step that can fail), then levels, then custom costs. */
    public boolean consumeNonItems(Player p, List<Cost> costs) {
        for (Cost c : costs) {
            if (c instanceof Cost.Money m && m.amount() > 0 && (!plugin.economyAvailable() || !plugin.economy().withdraw(p, m.amount()))) {
                return false;
            }
        }
        for (Cost c : costs) {
            if (c instanceof Cost.Levels l) p.setLevel(Math.max(0, p.getLevel() - l.amount()));
            if (c instanceof Cost.Custom custom) {
                CostType type = plugin.registry().cost(custom.id());
                if (type != null) type.take(p, custom.config());
            }
        }
        return true;
    }

    /** Gives back {@code percent} of the money / levels / custom costs (failure with REFUND or PARTIAL). */
    public void refundNonItems(Player p, List<Cost> costs, int percent) {
        if (percent <= 0) return;
        for (Cost c : costs) {
            switch (c) {
                case Cost.Money m -> {
                    if (plugin.economyAvailable()) plugin.economy().deposit(p, m.amount() * Math.min(100, percent) / 100.0);
                }
                case Cost.Levels l -> p.setLevel(p.getLevel() + ChanceMath.percentOf(l.amount(), percent));
                case Cost.Custom custom -> {
                    CostType type = plugin.registry().cost(custom.id());
                    if (type != null) type.refund(p, custom.config(), Math.min(100, percent));
                }
                default -> { }
            }
        }
    }

    /** Short plain text, e.g. "5000", "3x Corrupted Shard". */
    public String describe(Cost cost) {
        return switch (cost) {
            case Cost.Money m -> plugin.lang().format("cost.money", "amount",
                    plugin.economyAvailable() ? plugin.economy().format(m.amount()) : String.valueOf(m.amount()));
            case Cost.Levels l -> plugin.lang().format("cost.levels", "amount", String.valueOf(l.amount()));
            case Cost.MmoItem i -> plugin.lang().format("cost.item", "amount", String.valueOf(i.ref().amount()),
                    "name", MmoItems.displayName(i.ref()));
            case Cost.Vanilla v -> plugin.lang().format("cost.item", "amount", String.valueOf(v.amount()),
                    "name", pretty(v.material().name()));
            case Cost.Custom c -> {
                CostType type = plugin.registry().cost(c.id());
                yield type == null ? c.id() : type.describe(c.config());
            }
        };
    }

    /** Name of the item a cost asks for (no amount). */
    public String nameOf(Cost cost) {
        return switch (cost) {
            case Cost.MmoItem i -> MmoItems.displayName(i.ref());
            case Cost.Vanilla v -> pretty(v.material().name());
            default -> "";
        };
    }

    private static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String w : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
