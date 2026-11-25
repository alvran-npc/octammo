package io.octammo.api;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * @param output the item that was given on success, otherwise null
 */
public record UpgradeResult(Status status, @Nullable ItemStack output) {
    public enum Status {
        /** the roll succeeded */
        SUCCESS,
        /** the roll failed (costs were spent according to the path) */
        FAILED,
        /** the station / path does not exist or the module is disabled */
        UNKNOWN_PATH,
        /** missing item, cost, requirement or permission, nothing was taken */
        REQUIREMENTS_NOT_MET,
        /** an event was cancelled or the output item does not exist, nothing was taken */
        CANCELLED
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
