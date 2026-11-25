package io.octammo.upgrade;

import io.octammo.api.UpgradePath;
import io.octammo.core.ItemRef;

import java.util.List;

/** One path of a station, parsed from upgrades/&lt;station&gt;.yml. */
public record PathData(String stationId,
                       String id,
                       String displayName,
                       List<String> description,
                       List<Requirement> requirements,
                       ItemRef input,
                       boolean consumeInput,
                       List<Cost> costs,
                       Defs.OutputMode mode,
                       List<Defs.ResultDef> results,
                       Defs.ChanceDef chance,
                       Defs.FailDef fail,
                       Effects onSuccess,
                       Effects onFailEffects,
                       Integer loadingTicks,
                       boolean scaleCosts,
                       Integer maxMultiplier) implements UpgradePath {

    @Override public String inputType() { return input.type(); }
    @Override public String inputId() { return input.id(); }
    @Override public double baseChance() { return chance.base(); }
}
