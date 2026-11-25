package io.octammo.hook;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

public final class FloodgateHook {
    public boolean isBedrock(UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Throwable t) {
            return false;
        }
    }
}
