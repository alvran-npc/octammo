package io.octammo.upgrade;

import io.octammo.core.ItemRef;

import java.util.List;

/** Small value types of an upgrade path. */
public final class Defs {
    private Defs() {}

    public enum BoosterConsume { ALWAYS, SUCCESS_ONLY, NEVER }
    public enum InputFail { KEEP, CONSUME, DOWNGRADE, BREAK }
    public enum CostFail { CONSUME, REFUND, PARTIAL }
    public enum OutputMode { SINGLE, WEIGHTED }

    public record ResultDef(ItemRef ref, int weight, List<String> preserve) {}

    /** An item that adds {@code add} percentage points per piece, at most {@code maxItems} pieces count. */
    public record BoosterDef(Cost item, double add, int maxItems) {}

    public record ChanceDef(double base, double maxTotal, int boosterSlots, BoosterConsume consume, List<BoosterDef> boosters) {}

    public record ProtectionDef(Cost item, boolean keepsInput) {}

    /** What happens on a failed roll. {@code downgrade} is the item given for DOWNGRADE (may be null). */
    public record FailDef(InputFail input, CostFail costs, int refundPercent, ProtectionDef protection, ItemRef downgrade) {}
}
