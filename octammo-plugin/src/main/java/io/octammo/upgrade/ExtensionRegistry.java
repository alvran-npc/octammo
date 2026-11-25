package io.octammo.upgrade;

import io.octammo.api.CostType;
import io.octammo.api.EffectType;
import io.octammo.api.RequirementType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Custom costs, requirements and effects registered by other plugins. */
public final class ExtensionRegistry {
    private final Map<String, CostType> costs = new ConcurrentHashMap<>();
    private final Map<String, RequirementType> requirements = new ConcurrentHashMap<>();
    private final Map<String, EffectType> effects = new ConcurrentHashMap<>();

    public void register(CostType t) { costs.put(t.id().toLowerCase(Locale.ROOT), t); }
    public void register(RequirementType t) { requirements.put(t.id().toLowerCase(Locale.ROOT), t); }
    public void register(EffectType t) { effects.put(t.id().toLowerCase(Locale.ROOT), t); }

    public CostType cost(String id) { return id == null ? null : costs.get(id.toLowerCase(Locale.ROOT)); }
    public RequirementType requirement(String id) { return id == null ? null : requirements.get(id.toLowerCase(Locale.ROOT)); }
    public EffectType effect(String id) { return id == null ? null : effects.get(id.toLowerCase(Locale.ROOT)); }
}
