package io.octammo.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point of the OctaMMO API.
 *
 * <pre>{@code
 * OctaMMOApi api = OctaMMO.getApi();
 * }</pre>
 * It can also be loaded with {@code Bukkit.getServicesManager().load(OctaMMOApi.class)}.
 */
public final class OctaMMO {
    private static volatile OctaMMOApi api;

    private OctaMMO() {}

    /** @throws IllegalStateException if OctaMMO has not finished enabling (or is disabled) */
    public static @NotNull OctaMMOApi getApi() {
        OctaMMOApi current = api;
        if (current == null) throw new IllegalStateException("OctaMMO is not enabled yet. Add softdepend/depend: [OctaMMO] and wait for PluginEnableEvent.");
        return current;
    }

    @ApiStatus.Internal
    public static void setApi(OctaMMOApi value) {
        api = value;
    }
}
