package io.octammo.core;

import java.util.Locale;

/** Reference to an MMOItems template (TYPE + ID) with an amount. */
public record ItemRef(String type, String id, int amount) {
    public ItemRef {
        type = type.toUpperCase(Locale.ROOT);
        id = id.toUpperCase(Locale.ROOT);
        if (amount < 1) amount = 1;
    }

    public boolean matches(ItemRef other) {
        return other != null && type.equals(other.type) && id.equals(other.id);
    }

    public String key() {
        return type + ":" + id;
    }
}
