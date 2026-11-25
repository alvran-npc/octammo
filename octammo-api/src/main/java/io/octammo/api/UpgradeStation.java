package io.octammo.api;

import java.util.List;

/** A station = one upgrades/&lt;id&gt;.yml file. */
public interface UpgradeStation {
    String id();

    String displayName();

    /** Extra permission of the file, empty when none. */
    String permission();

    List<UpgradePath> paths();
}
