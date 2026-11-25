package io.octammo.enchant;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.OctaGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Six-row custom enchanting interface inspired by the supplied reference GUI. */
public final class EnchantingGui extends OctaGui {
    private final int inputSlot;
    private static final int HEADER_SLOT = 4;
    private static final int ITEM_INFO_SLOT = 10;
    private final int searchSlot, tierSlot, guideSlot, clearSearchSlot, previousSlot, nextSlot, takeInputSlot, closeSlot;
    private final int[] entrySlots;
    private final io.octammo.gui.FeatureGuiConfig guiConfig;
    private static final List<String> TIERS = List.of("ALL", "VANILLA");

    private record Option(String id, String name, String group, int maxLevel, int currentLevel,
                          String description, Enchantment vanilla, boolean compatible, String incompatibility) {}

    private String tier = "ALL";
    private String search = "";
    private int page;
    private boolean transactionPending;
    private boolean searchPending;
    private List<Option> shown = List.of();
    private List<Option> filtered = List.of();

    public EnchantingGui(OctaMMOPlugin plugin, Player player) {
        super(plugin, player);
        plugin.enchantingSearch().cancelPrevious(player);
        this.guiConfig = io.octammo.gui.FeatureGuiConfig.load(plugin, "enchanting.yml", "enchanting.yml");
        this.inputSlot = guiConfig.slot("input", 28);
        this.searchSlot = guiConfig.slot("search", 45);
        this.tierSlot = guiConfig.slot("tier", 46);
        this.guideSlot = guiConfig.slot("guide", 47);
        this.clearSearchSlot = guiConfig.slot("clear-search", 48);
        this.previousSlot = guiConfig.slot("previous", 49);
        this.nextSlot = guiConfig.slot("next", 50);
        this.takeInputSlot = guiConfig.slot("take-input", 51);
        this.closeSlot = guiConfig.slot("close", 53);
        this.entrySlots = guiConfig.slots("enchantments", new int[]{13,14,15,16,22,23,24,25,31,32,33,34,40,41,42,43});
        this.inventory = Bukkit.createInventory(this, 54,
                plugin.lang().render(guiConfig.title("&8✦ &#36D7D2&lENCHANTING SANCTUM"), player, null));
        drawFrame();
        refresh();
    }

    private void drawFrame() {
        // Decorative cyan glass frame; functional slots are painted after the frame.
        Material pane = Material.matchMaterial(guiConfig.fillerMaterial("CYAN_STAINED_GLASS_PANE"));
        if (pane == null) pane = Material.CYAN_STAINED_GLASS_PANE;
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44}) {
            inventory.setItem(slot, button(pane, guiConfig.fillerName("&#36D7D2&l✦"), guiConfig.fillerLore("&8OctaMMO Enchanting")));
        }
        // Functional controls are painted over the decorative frame below.
        inventory.setItem(HEADER_SLOT, button(Material.ENCHANTING_TABLE, "&#36D7D2&lENCHANTING TABLE",
                "&7Place an item in the slot on the left.",
                "&7Compatible enchantments will appear on the right.",
                "&8&m--------------------------------"));
        inventory.setItem(ITEM_INFO_SLOT, button(Material.BOOK, "&f&lITEM INFORMATION",
                "&7The item you place will be checked", "&7against each enchantment's rules."));
        inventory.setItem(inputSlot, inputPlaceholder());
        inventory.setItem(searchSlot, searchButton());
        inventory.setItem(tierSlot, tierButton());
        inventory.setItem(guideSlot, guideButton());
        inventory.setItem(clearSearchSlot, button(Material.MILK_BUCKET, "&c&lCLEAR SEARCH",
                "&7Remove the current search keyword."));
        inventory.setItem(previousSlot, button(Material.ARROW, "&e&l« PREVIOUS PAGE", "&7Browse earlier enchantments."));
        inventory.setItem(nextSlot, button(Material.ARROW, "&e&lNEXT PAGE »", "&7Browse more enchantments."));
        inventory.setItem(takeInputSlot, button(Material.HOPPER, "&6&lTAKE ITEM",
                "&7Return the item from the input slot."));
        inventory.setItem(closeSlot, button(Material.BARRIER, "&c&lCLOSE", "&7Close and return your item."));
    }

    private ItemStack button(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private ItemStack inputPlaceholder() {
        return button(Material.ENCHANTING_TABLE, "&#36D7D2&lITEM INPUT",
                "&7Place one item here to begin.", "&8Only one item is processed at a time.");
    }

    private ItemStack searchButton() {
        String shownSearch = search.isBlank() ? "None" : search;
        return button(Material.SPYGLASS, "&#36D7D2&lSEARCH ENCHANTMENTS",
                "&7Find enchantments by name or ID.",
                "&7Current keyword: &f" + shownSearch,
                "", "&eClick to type a keyword in chat.");
    }

    private ItemStack tierButton() {
        String label = tier.equals("ALL") ? "All Tiers" : pretty(tier);
        return button(Material.HOPPER, "&#36D7D2&lTIER FILTER: &f" + label,
                "&7Current filter: &f" + label,
                "&8&m--------------------------------",
                "&fAvailable filters:", "&7All Enchantments", "&fVanilla",
                "", "&eLeft-click: &7Show Vanilla",
                "&eRight-click: &7Show all");
    }

    private ItemStack guideButton() {
        return button(Material.ENCHANTED_BOOK, "&#36D7D2&lENCHANTMENT GUIDE",
                "&7Browse Vanilla Minecraft enchantments.",
                "&7Review their maximum levels and IDs.",
                "", "&eClick to open the guide.");
    }

    private ItemStack input() {
        ItemStack current = inventory.getItem(inputSlot);
        return Items.isReal(current) ? current : null;
    }

    @Override
    protected void onInputChanged() {
        refresh();
    }

    private void refresh() {
        ItemStack current = input();
        if (current == null) inventory.setItem(inputSlot, inputPlaceholder());

        List<Option> options = new ArrayList<>();
        if (current != null) addVanillaOptions(current, options);

        String needle = search.toLowerCase(Locale.ROOT).trim();
        if (!needle.isEmpty()) {
            options.removeIf(option -> !option.id().toLowerCase(Locale.ROOT).contains(needle)
                    && !option.name().toLowerCase(Locale.ROOT).contains(needle)
                    && !option.description().toLowerCase(Locale.ROOT).contains(needle));
        }
        options.sort(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        filtered = List.copyOf(options);

        int maxPage = Math.max(0, (filtered.size() - 1) / entrySlots.length);
        page = Math.min(page, maxPage);
        int start = page * entrySlots.length;
        shown = filtered.stream().skip(start).limit(entrySlots.length).toList();

        for (int i = 0; i < entrySlots.length; i++) {
            int slot = entrySlots[i];
            if (i < shown.size()) inventory.setItem(slot, enchantIcon(shown.get(i)));
            else inventory.setItem(slot, null);
        }
        if (current == null) {
            inventory.setItem(23, button(Material.ENCHANTED_BOOK, "&#36D7D2&lENCHANTMENT LIST",
                    "&7Insert an item in the left slot to view", "&7compatible Vanilla enchantments."));
        } else if (shown.isEmpty()) {
            inventory.setItem(23, button(Material.BARRIER, "&c&lNO MATCHING ENCHANTMENTS",
                    "&7Try another tier or clear your search."));
        }

        String itemName = current == null ? "No item inserted" : readableName(current);
        inventory.setItem(HEADER_SLOT, button(Material.ENCHANTING_TABLE, "&#36D7D2&lENCHANTING TABLE",
                "&7Item: &f" + itemName,
                "&7Tier: &f" + pretty(tier),
                "&7Matches: &f" + filtered.size(),
                search.isBlank() ? "&7Search: &fNone" : "&7Search: &f" + search,
                "&8&m--------------------------------",
                "&7Click an enchantment to apply the next level.",
                "&7Shift-click to apply its maximum level."));
        inventory.setItem(ITEM_INFO_SLOT, button(current == null ? Material.BOOK : current.getType(),
                "&f&lITEM INFORMATION",
                "&7Item: &f" + itemName,
                current == null ? "&cInsert an item to view compatible enchants." : (mmoItemLabel(current)),
                "&7Red entries are incompatible with this item."));
        inventory.setItem(searchSlot, searchButton());
        inventory.setItem(tierSlot, tierButton());
        inventory.setItem(guideSlot, guideButton());
        inventory.setItem(clearSearchSlot, button(Material.MILK_BUCKET, "&c&lCLEAR SEARCH",
                search.isBlank() ? "&7No search keyword is active." : "&7Current keyword: &f" + search,
                "&eClick to clear the keyword."));
        inventory.setItem(previousSlot, button(Material.ARROW, "&e&l« PREVIOUS PAGE",
                "&7Page: &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
        inventory.setItem(nextSlot, button(Material.ARROW, "&e&lNEXT PAGE »",
                "&7Page: &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
    }

    private void addVanillaOptions(ItemStack current, List<Option> options) {
        ItemMeta meta = current.getItemMeta();
        Map<Enchantment, Integer> existing = meta == null ? Map.of() : meta.getEnchants();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (!enchantment.canEnchantItem(current)) continue;
            String id = enchantment.getKey().getKey();
            int level = existing.getOrDefault(enchantment, 0);
            String conflict = null;
            for (Enchantment present : existing.keySet()) {
                if (!present.equals(enchantment) && (enchantment.conflictsWith(present) || present.conflictsWith(enchantment))) {
                    conflict = pretty(present.getKey().getKey());
                    break;
                }
            }
            options.add(new Option(id, pretty(id), "VANILLA", enchantment.getMaxLevel(), level,
                    "Vanilla Minecraft enchantment.", enchantment, conflict == null,
                    conflict == null ? "" : "Conflicts with " + conflict));
        }
    }

    private ItemStack enchantIcon(Option option) {
        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = icon.getItemMeta();
        String color = option.compatible() ? tierColor(option.group()) : "&c";
        meta.displayName(plugin.lang().render(color + "&l✦ " + option.name(), player, null));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.lang().render("&8&m--------------------------------", player, null));
        lore.add(plugin.lang().render("&7Tier: " + color + pretty(option.group()), player, null));
        lore.add(plugin.lang().render("&7Current Level: &f" + option.currentLevel(), player, null));
        lore.add(plugin.lang().render("&7Maximum Level: &e" + option.maxLevel(), player, null));
        if (!option.compatible()) lore.add(plugin.lang().render("&c✖ " + option.incompatibility(), player, null));
        lore.add(plugin.lang().render("&7Cost: &6" + cost(option.group(), Math.min(option.maxLevel(), option.currentLevel() + 1)) + " XP level(s)", player, null));
        if (!option.description().isBlank()) {
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&7" + option.description(), player, null));
        }
        lore.add(plugin.lang().render("", player, null));
        if (option.compatible()) {
            lore.add(plugin.lang().render("&e➤ Click: &7Apply next level", player, null));
            lore.add(plugin.lang().render("&e➤ Shift-click: &7Apply maximum level", player, null));
        } else {
            lore.add(plugin.lang().render("&cThis enchantment cannot be applied.", player, null));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
        return Items.markVirtual(icon);
    }

    private int cost(String group, int level) {
        int perLevel = Math.max(0, plugin.getConfig().getInt("enchanting.costs.vanilla-per-level", 1));
        return perLevel * Math.max(1, level);
    }

    private String tierColor(String group) {
        return switch (group.toUpperCase(Locale.ROOT)) {
            case "VANILLA" -> "&f";
            default -> "&f";
        };
    }

    private String pretty(String id) {
        if (id == null || id.isBlank()) return "All Tiers";
        String[] parts = id.toLowerCase(Locale.ROOT).split("[_ -]+");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (name.length() > 0) name.append(' ');
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private String mmoItemLabel(ItemStack stack) {
        if (!plugin.mmoItems()) return "&eVanilla / non-MMOItems item";
        io.octammo.core.ItemRef ref = io.octammo.core.MmoItems.identify(stack);
        return ref == null ? "&eVanilla / non-MMOItems item" : "&aMMOItems: &f" + ref.type() + " / " + ref.id();
    }

    private String readableName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().displayName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
        }
        return pretty(stack.getType().name());
    }

    @Override
    protected int inputSlot() { return inputSlot; }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (transactionPending) { e.setCancelled(true); return; }
        if (e.getClickedInventory() != inventory) {
            if (e.getClickedInventory() == null) { e.setCancelled(true); return; }
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                shiftInsert(e);
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == closeSlot) {
            closeNextTick();
            return;
        }
        if (slot == inputSlot) {
            swapInput(e);
            return;
        }
        if (slot == searchSlot) {
            searchPending = true;
            plugin.enchantingSearch().begin(player, this);
            plugin.lang().send(player, "enchanting.search-prompt");
            return;
        }
        if (slot == tierSlot) {
            if (e.isRightClick()) tier = "ALL";
            else {
                int current = TIERS.indexOf(tier);
                tier = TIERS.get((current + 1) % TIERS.size());
            }
            page = 0;
            refresh();
            return;
        }
        if (slot == guideSlot) {
            switchTo(new EnchantmentGuideGui(plugin, player, this, 0));
            return;
        }
        if (slot == clearSearchSlot) {
            search = "";
            page = 0;
            refresh();
            return;
        }
        if (slot == previousSlot) {
            page = Math.max(0, page - 1);
            refresh();
            return;
        }
        if (slot == nextSlot) {
            if ((long) (page + 1) * entrySlots.length < filtered.size()) page++;
            refresh();
            return;
        }
        if (slot == takeInputSlot) {
            ItemStack current = input();
            if (current != null) {
                inventory.setItem(inputSlot, inputPlaceholder());
                Items.giveOrDrop(player, current);
                refresh();
            }
            return;
        }
        for (int i = 0; i < entrySlots.length; i++) {
            if (slot == entrySlots[i] && i < shown.size()) {
                apply(shown.get(i), e.isShiftClick());
                return;
            }
        }
    }

    private void shiftInsert(InventoryClickEvent event) {
        if (player.getGameMode() == GameMode.CREATIVE || input() != null || transactionPending) return;
        int sourceSlot = event.getSlot();
        org.bukkit.inventory.Inventory sourceInventory = event.getClickedInventory();
        ItemStack clicked = event.getCurrentItem();
        if (sourceInventory == null || !Items.isReal(clicked)) return;
        ItemStack snapshot = clicked.clone();
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen()) return;
                ItemStack currentSource = sourceInventory.getItem(sourceSlot);
                if (!Items.isReal(currentSource) || !currentSource.isSimilar(snapshot) || input() != null) return;
                ItemStack one = currentSource.clone();
                one.setAmount(1);
                inventory.setItem(inputSlot, one);
                ItemStack remainder = currentSource.clone();
                remainder.setAmount(currentSource.getAmount() - 1);
                sourceInventory.setItem(sourceSlot, Items.isEmpty(remainder) ? null : remainder);
                onInputChanged();
            } finally {
                transactionPending = false;
            }
        });
    }

    private boolean isStillOpen() {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == inventory;
    }

    private void swapInput(InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND) return;

        if (click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) {
            boolean hotbar = click == ClickType.NUMBER_KEY;
            int hotbarSlot = event.getHotbarButton();
            ItemStack external = hotbar ? player.getInventory().getItem(hotbarSlot)
                    : player.getInventory().getItemInOffHand();
            ItemStack capturedExternal = Items.isReal(external) ? external.clone() : null;
            ItemStack capturedInput = input() == null ? null : input().clone();
            transactionPending = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!isStillOpen()) return;
                    ItemStack latestExternal = hotbar ? player.getInventory().getItem(hotbarSlot)
                            : player.getInventory().getItemInOffHand();
                    if (!sameItem(latestExternal, capturedExternal)) return;
                    if (capturedExternal == null) {
                        if (capturedInput == null) return;
                        if (hotbar) player.getInventory().setItem(hotbarSlot, capturedInput);
                        else player.getInventory().setItemInOffHand(capturedInput);
                        inventory.setItem(inputSlot, inputPlaceholder());
                        onInputChanged();
                        return;
                    }
                    if (capturedInput == null) {
                        ItemStack one = capturedExternal.clone();
                        one.setAmount(1);
                        ItemStack remainder = capturedExternal.clone();
                        remainder.setAmount(capturedExternal.getAmount() - 1);
                        if (hotbar) player.getInventory().setItem(hotbarSlot, Items.isEmpty(remainder) ? null : remainder);
                        else player.getInventory().setItemInOffHand(Items.isEmpty(remainder) ? null : remainder);
                        inventory.setItem(inputSlot, one);
                        onInputChanged();
                    } else if (capturedExternal.getAmount() == 1) {
                        if (hotbar) player.getInventory().setItem(hotbarSlot, capturedInput);
                        else player.getInventory().setItemInOffHand(capturedInput);
                        inventory.setItem(inputSlot, capturedExternal);
                        onInputChanged();
                    } else {
                        plugin.lang().send(player, "enchanting.single-item-only");
                    }
                } finally {
                    transactionPending = false;
                }
            });
            return;
        }

        ItemStack cursor = event.getView().getCursor();
        ItemStack capturedCursor = Items.isReal(cursor) ? cursor.clone() : null;
        ItemStack capturedInput = input() == null ? null : input().clone();
        if (capturedCursor != null && capturedInput != null && capturedCursor.getAmount() != 1) {
            plugin.lang().send(player, "enchanting.single-item-only");
            return;
        }
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen()) return;
                ItemStack latestInput = input();
                if (!sameItem(latestInput, capturedInput)) return;
                if (capturedCursor == null) {
                    if (capturedInput != null) {
                        inventory.setItem(inputSlot, inputPlaceholder());
                        player.setItemOnCursor(capturedInput);
                        onInputChanged();
                    }
                    return;
                }
                if (capturedInput == null) {
                    ItemStack one = capturedCursor.clone();
                    one.setAmount(1);
                    ItemStack remainder = capturedCursor.clone();
                    remainder.setAmount(capturedCursor.getAmount() - 1);
                    inventory.setItem(inputSlot, one);
                    player.setItemOnCursor(Items.isEmpty(remainder) ? null : remainder);
                    onInputChanged();
                } else {
                    inventory.setItem(inputSlot, capturedCursor);
                    player.setItemOnCursor(capturedInput);
                    onInputChanged();
                }
            } finally {
                transactionPending = false;
            }
        });
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!touchesTop) return;
        event.setCancelled(true);
        if (transactionPending || event.getRawSlots().size() != 1
                || !event.getRawSlots().contains(inputSlot)) return;
        ItemStack oldCursor = event.getOldCursor();
        if (!Items.isReal(oldCursor) || input() != null) return;
        ItemStack captured = oldCursor.clone();
        ItemStack one = captured.clone();
        one.setAmount(1);
        ItemStack remainder = captured.clone();
        remainder.setAmount(captured.getAmount() - 1);
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isStillOpen() || input() != null) return;
                inventory.setItem(inputSlot, one);
                player.setItemOnCursor(Items.isEmpty(remainder) ? null : remainder);
                onInputChanged();
            } finally {
                transactionPending = false;
            }
        });
    }

    private boolean sameItem(ItemStack first, ItemStack second) {
        if (!Items.isReal(first) && !Items.isReal(second)) return true;
        return Items.isReal(first) && Items.isReal(second)
                && first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    private void apply(Option option, boolean maximum) {
        ItemStack current = input();
        if (current == null) return;
        if (!option.compatible()) {
            plugin.lang().send(player, "enchanting.incompatible", "enchant", option.name(), "reason", option.incompatibility());
            return;
        }
        int targetLevel = maximum ? option.maxLevel() : Math.min(option.maxLevel(), option.currentLevel() + 1);
        if (option.currentLevel() >= option.maxLevel()) {
            plugin.lang().send(player, "enchanting.max-level", "enchant", option.name());
            return;
        }
        int price = cost(option.group(), targetLevel);
        if (player.getLevel() < price) {
            plugin.lang().send(player, "enchanting.not-enough-xp", "cost", String.valueOf(price));
            return;
        }

        ItemStack updated = current.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null || option.vanilla() == null) return;
        meta.addEnchant(option.vanilla(), targetLevel, true);
        updated.setItemMeta(meta);
        if (price > 0) player.giveExpLevels(-price);
        updated.setAmount(1);
        inventory.setItem(inputSlot, updated);
        plugin.lang().send(player, "enchanting.applied", "enchant", option.name(), "level", String.valueOf(targetLevel));
        refresh();
    }

    /** Cancels a pending prompt without leaving its item trapped in a closed GUI. */
    public void cancelSearchPrompt() {
        searchPending = false;
        if (!isStillOpen()) returnInputToPlayer();
    }

    /** Ends a search prompt that was left unanswered for one minute. */
    public void searchExpired() {
        searchPending = false;
        if (!player.isOnline()) return;
        plugin.lang().send(player, "enchanting.search-expired");
        if (player.getOpenInventory().getTopInventory() != inventory) returnInputToPlayer();
    }

    /** Called by the chat listener after the player finishes a search prompt. */
    public void acceptSearch(String keyword) {
        searchPending = false;
        if (!player.isOnline()) return;
        String value = keyword == null ? "" : keyword.trim();
        if (value.equalsIgnoreCase("cancel")) {
            plugin.lang().send(player, "enchanting.search-cancelled");
            returnInputToPlayer();
            return;
        }
        search = value;
        page = 0;
        plugin.lang().send(player, "enchanting.search-updated", "keyword", value.isBlank() ? "None" : value);
        refresh();
        // Some chat/command plugins close inventories while taking input. Re-open the
        // same inventory after the query has been applied so the result is visible.
        if (player.isOnline() && player.getOpenInventory().getTopInventory() != inventory) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory() != inventory) {
                    player.openInventory(inventory);
                }
            });
        }
    }

    /** Reopens this GUI when a child page returns to it. */
    public void showFromChild() {
        switchTo(this);
    }

    /** Returns the held input when the guide is closed without using its Back button. */
    public void returnInputToPlayer() {
        searchPending = false;
        plugin.enchantingSearch().end(player);
        ItemStack current = input();
        if (current != null) Items.giveOrDrop(player, current);
        inventory.setItem(inputSlot, inputPlaceholder());
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (transition) return;
        // A chat-capable plugin may close the inventory while the player types.
        // Keep the session and input item until the pending search is consumed.
        if (searchPending || plugin.enchantingSearch().isWaiting(player)) return;
        plugin.enchantingSearch().end(player);
        returnInputToPlayer();
    }
}
