package io.octammo.upgrade;

import io.octammo.api.UpgradePath;
import io.octammo.api.UpgradeStation;

import java.util.ArrayList;
import java.util.List;

/** A loaded upgrades/&lt;id&gt;.yml file. */
public record StationData(String id,
                          String displayName,
                          String permission,
                          String gui,
                          int cooldown,
                          List<String> worldsBlacklist,
                          List<String> regionsBlacklist,
                          Integer loadingTicks,
                          List<PathData> pathData) implements UpgradeStation {

    @Override
    public List<UpgradePath> paths() {
        return new ArrayList<UpgradePath>(pathData);
    }

    public PathData path(String pathId) {
        if (pathId == null) return null;
        for (PathData p : pathData) if (p.id().equalsIgnoreCase(pathId)) return p;
        return null;
    }
}
