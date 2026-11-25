package io.octammo.api;

/**
 * Describes a GUI-less upgrade.
 *
 * @param skipLoading true = deliver the result at once, without the title loading
 */
public record UpgradeContext(String stationId, String pathId, boolean skipLoading) {
    public UpgradeContext(String stationId, String pathId) {
        this(stationId, pathId, true);
    }
}
