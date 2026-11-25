package io.octammo.core;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Thin wrapper around the MMOItems / MythicLib API. Every direct API call lives here. */
public final class MmoItems {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern COLOR = Pattern.compile("(?i)[&\u00a7]#[0-9a-f]{6}|[&\u00a7][0-9a-fk-orx]");
    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();

    private MmoItems() {}

    /** Result of MMOItems' native revision rebuild, including gemstones returned by its reforge options. */
    public record RebuildResult(ItemStack item, List<ItemStack> extraItems,
                                int gemstoneCount, boolean skinReturned, int removedUpgradeLevel) {
        public RebuildResult {
            item = item.clone();
            extraItems = extraItems.stream().filter(Items::isReal).map(ItemStack::clone).toList();
        }
        @Override public ItemStack item() { return item.clone(); }
    }

    private record EnhancementRecovery(List<ItemStack> items, int gemstoneCount,
                                       boolean skinReturned, int upgradeLevel) {}

    public static void clearCache() {
        NAME_CACHE.clear();
    }

    /** @return the TYPE/ID of an MMOItem, or null when the stack is not an MMOItem. */
    public static ItemRef identify(ItemStack stack) {
        if (Items.isEmpty(stack)) return null;
        try {
            NBTItem nbt = NBTItem.get(stack);
            if (!nbt.hasTag("MMOITEMS_ITEM_TYPE") || !nbt.hasTag("MMOITEMS_ITEM_ID")) return null;
            String type = nbt.getString("MMOITEMS_ITEM_TYPE");
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            if (type == null || type.isEmpty() || id == null || id.isEmpty()) return null;
            return new ItemRef(type, id, stack.getAmount());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Builds a fresh item of amount 1, or null if the template does not exist. */
    public static ItemStack create(ItemRef ref) {
        try {
            Type type = Type.get(ref.type());
            if (type == null) return null;
            ItemStack stack = MMOItems.plugin.getItem(type, ref.id());
            if (Items.isEmpty(stack)) return null;
            stack = stack.clone();
            stack.setAmount(1);
            return stack;
        } catch (Throwable t) {
            return null;
        }
    }


    /**
     * Rebuilds an MMOItem through MMOItems' own Reforger while deliberately removing
     * its installed upgrade level, skin and gemstones. Recoverable gemstones and the
     * applied skin are built back into their original MMOItems as standalone outputs.
     *
     * The expensive LiveMMOItem read is performed only when the player confirms Repair,
     * never while a GUI is being opened or refreshed.
     *
     * @return rebuilt base item plus recovered enhancement items; null means that the
     * item could not be restored safely and the caller must keep the original untouched.
     */
    public static RebuildResult rebuild(ItemStack original) {
        if (Items.isEmpty(original) || identify(original) == null) return null;
        try {
            ClassLoader loader = MMOItems.class.getClassLoader();
            EnhancementRecovery recovery = recoverEnhancements(original, loader);
            if (recovery == null) return null;

            Class<?> reforgerType = Class.forName("net.Indyuce.mmoitems.api.util.MMOItemReforger", true, loader);
            Constructor<?> constructor = reforgerType.getConstructor(ItemStack.class);
            Object reforger = constructor.newInstance(original.clone());

            Object canReforge = reforgerType.getMethod("canReforge").invoke(reforger);
            if (!Boolean.TRUE.equals(canReforge)) return null;

            Object options = createLoreRepairOptions(loader);
            if (options == null) return null;

            Method reforge = null;
            for (Method candidate : reforgerType.getMethods()) {
                if (candidate.getName().equals("reforge") && candidate.getParameterCount() == 1
                        && candidate.getParameterTypes()[0].isInstance(options)) {
                    reforge = candidate;
                    break;
                }
            }
            if (reforge == null || !Boolean.TRUE.equals(reforge.invoke(reforger, options))) return null;

            Object rawResult = reforgerType.getMethod("getResult").invoke(reforger);
            if (!(rawResult instanceof ItemStack result) || Items.isEmpty(result)) return null;
            if (!verifyEnhancementsRemoved(result, identify(original), loader)) {
                MMOItems.plugin.getLogger().warning("Item Fixer verification failed: the rebuilt MMOItem still contains a skin, gemstone, or upgrade. Original item was kept.");
                return null;
            }

            List<ItemStack> extraItems = new ArrayList<>(recovery.items());
            // Preserve any other MMOItems reforge outputs, while avoiding duplicate
            // gemstones already recovered through LiveMMOItem.extractGemstones().
            try {
                Object rawExtras = reforgerType.getMethod("getReforgingOutput").invoke(reforger);
                if (rawExtras instanceof Collection<?> collection) {
                    for (Object extra : collection) {
                        if (!(extra instanceof ItemStack stack) || !Items.isReal(stack)) continue;
                        boolean duplicate = extraItems.stream().anyMatch(existing -> existing.isSimilar(stack));
                        if (!duplicate) extraItems.add(stack.clone());
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // Some compatible MMOItems builds do not expose extra reforging output.
            }
            result.setAmount(original.getAmount());
            return new RebuildResult(result, extraItems, recovery.gemstoneCount(),
                    recovery.skinReturned(), recovery.upgradeLevel());
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            MMOItems.plugin.getLogger().warning("MMOItems item rebuild was cancelled: " + cause.getMessage());
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            MMOItems.plugin.getLogger().warning("MMOItems item rebuild is unavailable: " + ex.getMessage());
            return null;
        }
    }

    /** Confirms that the native Reforger removed exactly the enhancement categories requested. */
    private static boolean verifyEnhancementsRemoved(ItemStack rebuilt, ItemRef expected, ClassLoader loader)
            throws ReflectiveOperationException {
        ItemRef actual = identify(rebuilt);
        if (expected == null || actual == null || !expected.matches(actual)) return false;

        NBTItem nbt = NBTItem.get(rebuilt);
        if (nbt.hasTag("MMOITEMS_SKIN_ID") && !nbt.getString("MMOITEMS_SKIN_ID").isBlank()) return false;
        if (nbt.hasTag("MMOITEMS_SKIN_TYPE") && !nbt.getString("MMOITEMS_SKIN_TYPE").isBlank()) return false;

        Class<?> liveType = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem", true, loader);
        Object live = liveType.getConstructor(ItemStack.class).newInstance(rebuilt.clone());
        if (!readGemstoneEntries(liveType, live).isEmpty()) return false;
        Object level = liveType.getMethod("getUpgradeLevel").invoke(live);
        return !(level instanceof Number number) || number.intValue() <= 0;
    }

    /** Reads and validates every enhancement before allowing the original item to be consumed. */
    private static EnhancementRecovery recoverEnhancements(ItemStack original, ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> liveType = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem", true, loader);
        Object live = liveType.getConstructor(ItemStack.class).newInstance(original.clone());

        Collection<?> detectedGems = readGemstoneEntries(liveType, live);
        Method extractMethod = liveType.getMethod("extractGemstones");
        Object extracted = extractMethod.invoke(live);
        if (!(extracted instanceof Collection<?> extractedGems)) {
            MMOItems.plugin.getLogger().warning("Item Fixer could not read MMOItems gemstone extraction output; original item was kept.");
            return null;
        }
        if (extractedGems.size() != detectedGems.size()) {
            MMOItems.plugin.getLogger().warning("Item Fixer found " + detectedGems.size()
                    + " installed gemstone(s), but MMOItems restored " + extractedGems.size()
                    + ". Repair was cancelled to prevent gemstone loss.");
            return null;
        }

        List<ItemStack> extras = new ArrayList<>();
        for (Object entry : extractedGems) {
            Object gemstone = pairValue(entry);
            if (gemstone == null) {
                MMOItems.plugin.getLogger().warning("Item Fixer could not resolve a restored gemstone object; original item was kept.");
                return null;
            }
            ItemStack gemstoneStack = buildMmoItem(gemstone);
            if (Items.isEmpty(gemstoneStack)) {
                MMOItems.plugin.getLogger().warning("Item Fixer could not build a restored gemstone; original item was kept.");
                return null;
            }
            gemstoneStack.setAmount(1);
            extras.add(gemstoneStack);
        }

        NBTItem nbt = NBTItem.get(original);
        String skinId = nbt.hasTag("MMOITEMS_SKIN_ID") ? nbt.getString("MMOITEMS_SKIN_ID") : "";
        String skinType = nbt.hasTag("MMOITEMS_SKIN_TYPE") ? nbt.getString("MMOITEMS_SKIN_TYPE") : "";
        boolean hasSkin = skinId != null && !skinId.isBlank();
        if (hasSkin) {
            if (skinType == null || skinType.isBlank()) skinType = "SKIN";
            Type type = Type.get(skinType);
            ItemStack skinStack = type == null ? null : MMOItems.plugin.getItem(type, skinId);
            if (Items.isEmpty(skinStack)) {
                MMOItems.plugin.getLogger().warning("Item Fixer found installed skin " + skinType + ":" + skinId
                        + " but its MMOItems template could not be restored. Original item was kept.");
                return null;
            }
            skinStack.setAmount(1);
            extras.add(skinStack);
        }

        int upgradeLevel = 0;
        try {
            // getUpgradeLevel() returns zero when no upgrade data exists. Do not
            // require an active template: old items can still carry an upgrade level.
            Object level = liveType.getMethod("getUpgradeLevel").invoke(live);
            if (level instanceof Number number) upgradeLevel = Math.max(0, number.intValue());
        } catch (ReflectiveOperationException ignored) {
            // The reforge options below still clear the upgrade level on supported builds.
        }
        return new EnhancementRecovery(List.copyOf(extras), extractedGems.size(), hasSkin, upgradeLevel);
    }

    private static Collection<?> readGemstoneEntries(Class<?> liveType, Object live) throws ReflectiveOperationException {
        Method getter;
        try {
            getter = liveType.getMethod("getGemstones");
        } catch (NoSuchMethodException olderApi) {
            getter = liveType.getMethod("getGemStones");
        }
        Object value = getter.invoke(live);
        if (value instanceof Collection<?> collection) return collection;
        throw new ReflectiveOperationException("MMOItems gemstone data is not a collection");
    }

    /** Supports modern Pair<GemstoneData, MMOItem> and legacy lists of MMOItem objects. */
    private static Object pairValue(Object entry) {
        if (entry == null) return null;
        for (String methodName : List.of("getValue", "getRight", "getSecond")) {
            try {
                Object value = entry.getClass().getMethod(methodName).invoke(entry);
                if (value != null) return value;
            } catch (ReflectiveOperationException ignored) {
                // Try the next pair accessor.
            }
        }
        if (hasPublicMethod(entry.getClass(), "newBuilder")) return entry;
        return null;
    }

    private static boolean hasPublicMethod(Class<?> type, String name) {
        try { type.getMethod(name); return true; }
        catch (NoSuchMethodException ignored) { return false; }
    }

    private static ItemStack buildMmoItem(Object mmoItem) throws ReflectiveOperationException {
        Object builder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
        Object built = builder.getClass().getMethod("build").invoke(builder);
        return built instanceof ItemStack stack ? stack.clone() : null;
    }

    /**
     * Creates MMOItems' native ReforgeOptions. Existing server keep-data preferences
     * remain in effect except the three values this operation explicitly removes.
     */
    private static Object createLoreRepairOptions(ClassLoader loader) throws ReflectiveOperationException {
        Object pluginInstance = MMOItems.plugin;
        Object fileConfig = pluginInstance.getClass().getMethod("getConfig").invoke(pluginInstance);
        Object rawSection = fileConfig.getClass()
                .getMethod("getConfigurationSection", String.class)
                .invoke(fileConfig, "item-revision.keep-data");

        YamlConfiguration optionsConfig = new YamlConfiguration();
        optionsConfig.set("display-name", false);
        optionsConfig.set("lore", false);
        optionsConfig.set("enchantments", true);
        optionsConfig.set("upgrades", true);
        optionsConfig.set("gemstones", true);
        optionsConfig.set("gems", true);
        optionsConfig.set("skins", true);
        optionsConfig.set("soulbound", true);
        optionsConfig.set("kept-lore-prefix", "§7");
        optionsConfig.set("external-sh", true);
        optionsConfig.set("modifications", true);
        optionsConfig.set("reroll", false);
        // This is an MMOItems ReforgeOptions preservation flag, not an OctaMMO hook.
        // Keep MMOItems' default so unrelated stored item data is not stripped.
        optionsConfig.set("advanced-enchantments", true);
        optionsConfig.set("tier", true);
        if (rawSection instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) optionsConfig.set(key, section.get(key));
        }
        optionsConfig.set("lore", false);
        optionsConfig.set("upgrades", false);
        optionsConfig.set("gemstones", false);
        optionsConfig.set("gems", false);
        optionsConfig.set("skins", false);

        Class<?> optionsType = Class.forName("net.Indyuce.mmoitems.api.ReforgeOptions", true, loader);
        Constructor<?> optionsConstructor = optionsType.getConstructor(ConfigurationSection.class);
        return optionsConstructor.newInstance(optionsConfig);
    }

    /** Plain display name (no colours) of the template, falling back to the id. */
    public static String displayName(ItemRef ref) {
        return NAME_CACHE.computeIfAbsent(ref.key(), k -> {
            ItemStack s = create(ref);
            if (s != null && s.hasItemMeta()) {
                Component c = s.getItemMeta().displayName();
                if (c != null) return PLAIN.serialize(c);
            }
            return ref.id();
        });
    }

    /** Lore lines as plain text, colour codes removed. */
    public static List<String> plainLore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return List.of();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>(lore.size());
        for (Component c : lore) out.add(PLAIN.serialize(c));
        return out;
    }

    /** Strips &-codes and hex codes, lower-cases and trims (for config text comparison). */
    public static String normalize(String s) {
        return COLOR.matcher(s == null ? "" : s).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }


    /**
     * Best-effort copy of data from the old item to the new one. Raw NBT keys are copied as text, so
     * lore and stats are NOT recomputed (gems / upgrade level may look wrong): this is why every option defaults to off.
     */
    public static ItemStack preserve(ItemStack oldItem, ItemStack fresh, Collection<String> nbtKeys, boolean durability) {
        ItemStack out = fresh;
        try {
            if (!nbtKeys.isEmpty()) {
                NBTItem src = NBTItem.get(oldItem);
                List<ItemTag> tags = new ArrayList<>();
                for (String key : nbtKeys) if (src.hasTag(key)) tags.add(new ItemTag(key, src.getString(key)));
                if (!tags.isEmpty()) out = NBTItem.get(out).addTag(tags).toItem();
            }
        } catch (Throwable t) {
            // keep the item as it is
        }
        if (durability && oldItem.getItemMeta() instanceof Damageable oldDmg && out.getItemMeta() instanceof Damageable newDmg) {
            newDmg.setDamage(oldDmg.getDamage());
            out.setItemMeta(newDmg);
        }
        return out;
    }

    /** Gives {@code amount} copies, splitting by max stack size and dropping what does not fit. */
    public static void give(Player player, ItemStack single, int amount) {
        int max = Math.max(1, single.getMaxStackSize());
        while (amount > 0) {
            int n = Math.min(max, amount);
            ItemStack copy = single.clone();
            copy.setAmount(n);
            player.getInventory().addItem(copy).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            amount -= n;
        }
    }
}
