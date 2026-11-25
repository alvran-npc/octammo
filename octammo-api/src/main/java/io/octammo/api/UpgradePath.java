package io.octammo.api;

import java.util.List;

/** One upgrade path of a station. */
public interface UpgradePath {
    String stationId();

    String id();

    String displayName();

    List<String> description();

    /** TYPE of the item that gets upgraded. */
    String inputType();

    /** ID of the item that gets upgraded. */
    String inputId();

    /** Base success chance in percent, before boosters. */
    double baseChance();
}
