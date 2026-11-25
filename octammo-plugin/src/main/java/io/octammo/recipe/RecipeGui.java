package io.octammo.recipe;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.gui.FeatureGuiConfig;
import io.octammo.gui.OctaGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Configurable, static (non-animated) Combine and Crafting recipe interface. */
public final class RecipeGui extends OctaGui {
    private static final int[] DEFAULT_ENTRY_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
    private static final int[] DEFAULT_INPUT_SLOTS = {19, 20, 21, 28, 29, 30};

    private final RecipeManager.Kind kind;
    private final List<RecipeManager.Recipe> recipes;
    private final RecipeManager.Recipe selected;
    private final int page;
    private final FeatureGuiConfig layout;
    private final Map<Integer, RecipeManager.Recipe> bySlot = new HashMap<>();
    private final int headerSlot, previousSlot, nextSlot, closeSlot, outputSlot, currencySlot, actionSlot, backSlot;
    private final int[] entrySlots, inputSlots;
    private final Material paneMaterial, accentPaneMaterial;
    private boolean transactionPending;

    public RecipeGui(OctaMMOPlugin plugin, Player player, RecipeManager.Kind kind) {
        this(plugin, player, kind, null, 0);
    }

    private RecipeGui(OctaMMOPlugin plugin, Player player, RecipeManager.Kind kind,
                      RecipeManager.Recipe selected, int page) {
        super(plugin, player);
        this.kind = kind;
        this.recipes = plugin.recipes().recipes(kind);
        this.selected = selected;
        this.page = Math.max(0, page);
        String configName = kind == RecipeManager.Kind.COMBINE ? "combine.yml" : "crafting.yml";
        this.layout = FeatureGuiConfig.load(plugin, configName, configName);
        this.headerSlot = layout.slot("header", 4);
        this.previousSlot = layout.slot("previous", 46);
        this.nextSlot = layout.slot("next", 52);
        this.closeSlot = layout.slot("close", 53);
        this.outputSlot = layout.slot("output", 25);
        this.currencySlot = layout.slot("currency", 38);
        this.actionSlot = layout.slot("action", 40);
        this.backSlot = layout.slot("back", 45);
        this.entrySlots = layout.slots("entries", DEFAULT_ENTRY_SLOTS);
        this.inputSlots = layout.slots("inputs", DEFAULT_INPUT_SLOTS);
        this.paneMaterial = safeMaterial(layout.fillerMaterial(kind == RecipeManager.Kind.COMBINE
                ? "PURPLE_STAINED_GLASS_PANE" : "CYAN_STAINED_GLASS_PANE"),
                kind == RecipeManager.Kind.COMBINE ? Material.PURPLE_STAINED_GLASS_PANE : Material.CYAN_STAINED_GLASS_PANE);
        this.accentPaneMaterial = safeMaterial(layout.accentMaterial(kind == RecipeManager.Kind.COMBINE
                ? "MAGENTA_STAINED_GLASS_PANE" : "LIGHT_BLUE_STAINED_GLASS_PANE"),
                kind == RecipeManager.Kind.COMBINE ? Material.MAGENTA_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        String defaultTitle = kind == RecipeManager.Kind.COMBINE
                ? "&8✦ &#B76CFF&lOCTAMMO &8» &#D9A7FF&lCOMBINE FORGE"
                : "&8✦ &#36D7D2&lOCTAMMO &8» &#8BE9E4&lARCANE CRAFTING";
        String title = selected == null ? defaultTitle : defaultTitle + " &8» &f" + selected.name();
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().render(layout.title(title), player, null));
        drawFrame();
        if (selected == null) drawMenu(); else drawRecipe();
    }

    private Material safeMaterial(String name, Material fallback) {
        Material found = Material.matchMaterial(name == null ? "" : name);
        return found == null || !found.isItem() ? fallback : found;
    }

    private ItemStack button(Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(plugin.lang().render(line, player, null));
        return Items.make(material, plugin.lang().render(name, player, null), lines);
    }

    private String accent() {
        return kind == RecipeManager.Kind.COMBINE ? "&#C78AFF" : "&#54D8D2";
    }

    private void drawFrame() {
        inventory.clear();
        String decorationName = layout.fillerName(accent() + "&l✦");
        String decorationLore = layout.fillerLore("&8OctaMMO " + (kind == RecipeManager.Kind.COMBINE ? "Combine Forge" : "Crafting Atelier"));
        for (int slot : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53}) {
            Material pane = (slot % 2 == 0) ? paneMaterial : accentPaneMaterial;
            inventory.setItem(slot, button(pane, decorationName, decorationLore, "&8&m----------------"));
        }
        inventory.setItem(headerSlot, button(kind == RecipeManager.Kind.COMBINE ? Material.ANVIL : Material.SMITHING_TABLE,
                accent() + "&l" + (kind == RecipeManager.Kind.COMBINE ? "COMBINE FORGE" : "CRAFTING ATELIER"),
                "&7A curated workstation for custom equipment.",
                "&7Select a recipe to view its materials.",
                "", "&8✦ &f" + recipes.size() + " &7recipe(s) available"));
        inventory.setItem(closeSlot, button(Material.BARRIER, "&c&l✕ CLOSE", "&7Close this workstation."));
    }

    private void drawMenu() {
        inventory.setItem(previousSlot, button(Material.SPECTRAL_ARROW, "&e&l« PREVIOUS PAGE",
                "&7Current page: &f" + (page + 1)));
        inventory.setItem(nextSlot, button(Material.SPECTRAL_ARROW, "&e&lNEXT PAGE »", "&7Browse the next recipe page."));
        inventory.setItem(layout.slot("page-info", 49), button(Material.WRITABLE_BOOK,
                accent() + "&lRECIPE INDEX", "&7Page: &f" + (page + 1), "&7Total recipes: &f" + recipes.size(),
                "", "&eSelect any recipe to continue."));
        if (recipes.isEmpty()) {
            inventory.setItem(layout.slot("empty-info", 22), button(Material.BARRIER, "&c&lNO RECIPES FOUND",
                    "&7Add recipe definitions in the recipes folder."));
            return;
        }

        int index = 0;
        for (RecipeManager.Recipe recipe : recipes.stream()
                .skip((long) page * entrySlots.length).limit(entrySlots.length).toList()) {
            int slot = entrySlots[index++];
            bySlot.put(slot, recipe);
            ItemStack icon = recipe.result();
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(plugin.lang().render(accent() + "&l✦ " + recipe.name(), player, null));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.lang().render("&8&m--------------------------------", player, null));
            for (String line : recipe.description()) lore.add(plugin.lang().render("&7" + line, player, null));
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&7Required materials: &f" + recipe.ingredients().size(), player, null));
            lore.add(plugin.lang().render("&7Output: &f" + recipe.result().getAmount() + "x", player, null));
            if (recipe.currencyAmount() > 0) {
                lore.add(plugin.lang().render("&7Currency cost: &6" + recipe.currencyAmount() + " " + recipe.currencyId(), player, null));
            }
            lore.add(plugin.lang().render("", player, null));
            lore.add(plugin.lang().render("&e➤ Click to inspect recipe", player, null));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, Items.markVirtual(icon));
        }
    }

    private void drawRecipe() {
        inventory.setItem(headerSlot, button(kind == RecipeManager.Kind.COMBINE ? Material.ANVIL : Material.SMITHING_TABLE,
                accent() + "&l✦ " + selected.name(),
                "&7Insert the required materials below.",
                "&7The output is delivered after validation.",
                "", "&8Recipe ID: &7" + selected.id()));

        for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
            int slot = inputSlots[i];
            ItemStack current = inventory.getItem(slot);
            if (!Items.isReal(current)) inventory.setItem(slot, ingredientPlaceholder(i, 0));
        }
        inventory.setItem(outputSlot, outputDisplay());
        inventory.setItem(currencySlot, selected.currencyAmount() > 0
                ? button(Material.GOLD_INGOT, "&6&lCURRENCY REQUIREMENT",
                    "&7Currency: &f" + selected.currencyId(), "&7Cost: &6" + selected.currencyAmount(),
                    "", "&eCharged only after all materials are valid.")
                : button(Material.LIME_STAINED_GLASS_PANE, "&a&lNO CURRENCY COST", "&7This recipe has no custom-currency fee."));
        inventory.setItem(layout.slot("process-info", 13), button(Material.ANVIL,
                accent() + "&lFORGE PROCESS", "&7Check each ingredient slot.",
                "&7Then press the button below.", "", "&8No GUI animations are used."));
        inventory.setItem(actionSlot, button(kind == RecipeManager.Kind.COMBINE ? Material.ANVIL : Material.SMITHING_TABLE,
                kind == RecipeManager.Kind.COMBINE ? "&#D9A7FF&l✦ CONFIRM COMBINATION" : "&#8BE9E4&l✦ CRAFT ITEM",
                "&8&m--------------------------------",
                "&7Materials: &f" + selected.ingredients().size(),
                "&7Output: &f" + selected.result().getAmount() + "x " + selected.result().getType().name(),
                selected.currencyAmount() > 0 ? "&7Fee: &6" + selected.currencyAmount() + " " + selected.currencyId() : "&7Fee: &aNone",
                "", "&e➤ Click to process"));
        inventory.setItem(backSlot, button(Material.ARROW, "&e&l« BACK TO RECIPES", "&7Return to the recipe index."));
        redrawInputHints();
    }

    private ItemStack outputDisplay() {
        ItemStack output = selected.result();
        ItemMeta meta = output.getItemMeta();
        meta.displayName(plugin.lang().render("&a&l✦ OUTPUT PREVIEW", player, null));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.lang().render("&8&m--------------------------------", player, null));
        lore.add(plugin.lang().render("&7Result amount: &f" + output.getAmount(), player, null));
        lore.add(plugin.lang().render("&7Item: &f" + selected.name(), player, null));
        lore.add(plugin.lang().render("", player, null));
        lore.add(plugin.lang().render("&aThis item will be delivered after processing.", player, null));
        meta.lore(lore);
        output.setItemMeta(meta);
        return Items.markVirtual(output);
    }

    private ItemStack ingredientPlaceholder(int index, int inserted) {
        RecipeManager.Ingredient ingredient = selected.ingredients().get(index);
        ItemStack display = ingredient.sample();
        if (display == null || display.getType().isAir()) display = new ItemStack(Material.BARRIER);
        display.setAmount(Math.max(1, Math.min(ingredient.amount(), display.getMaxStackSize())));
        ItemMeta meta = display.getItemMeta();
        meta.displayName(plugin.lang().render("&e&l" + ingredient.displayName(), player, null));
        meta.lore(List.of(
                plugin.lang().render("&8&m--------------------------------", player, null),
                plugin.lang().render("&7Required: &f" + ingredient.amount() + "x", player, null),
                plugin.lang().render("&7Inserted: " + (inserted >= ingredient.amount() ? "&a" : "&c") + inserted + "&7/&f" + ingredient.amount(), player, null),
                plugin.lang().render("", player, null),
                plugin.lang().render("&e➤ Place matching materials here", player, null)));
        display.setItemMeta(meta);
        return Items.markVirtual(display);
    }

    private void redrawInputHints() {
        if (selected == null) return;
        for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
            int slot = inputSlots[i];
            ItemStack current = inventory.getItem(slot);
            int inserted = Items.isReal(current) ? current.getAmount() : 0;
            if (inserted == 0) inventory.setItem(slot, ingredientPlaceholder(i, 0));
            else {
                RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
                ItemMeta meta = current.getItemMeta();
                if (meta != null) {
                    meta.lore(List.of(
                            plugin.lang().render("&8&m--------------------------------", player, null),
                            plugin.lang().render("&7Required: &f" + ingredient.amount() + "x", player, null),
                            plugin.lang().render("&7Inserted: &a" + inserted + "&7/&f" + ingredient.amount(), player, null),
                            plugin.lang().render("&a✔ Matching ingredient", player, null)));
                    current.setItemMeta(meta);
                }
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent e) {
        if (creativeDenied()) {
            e.setCancelled(true);
            rejectCreativeMode();
            return;
        }
        if (transactionPending) { e.setCancelled(true); return; }
        if (e.getClickedInventory() != inventory) {
            if (e.getClickedInventory() == null || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                if (selected != null) shiftIngredientFromPlayerInventory(e);
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == closeSlot) { closeNextTick(); return; }
        if (selected == null) {
            if (slot == previousSlot && page > 0) { playClick(); switchTo(new RecipeGui(plugin, player, kind, null, page - 1)); return; }
            if (slot == nextSlot && (long) (page + 1) * entrySlots.length < recipes.size()) { playClick(); switchTo(new RecipeGui(plugin, player, kind, null, page + 1)); return; }
            RecipeManager.Recipe recipe = bySlot.get(slot);
            if (recipe != null) {
                playClick();
                switchTo(new RecipeGui(plugin, player, kind, recipe, page));
            }
            return;
        }

        if (slot == backSlot) {
            returnInputs();
            playClick();
            switchTo(new RecipeGui(plugin, player, kind, null, page));
            return;
        }
        if (slot == actionSlot) { beginProcess(); return; }
        for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
            if (slot == inputSlots[i]) { handleInputClick(i, e); return; }
        }
    }

    private void handleInputClick(int index, InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.NUMBER_KEY && click != ClickType.SWAP_OFFHAND) return;
        int slot = inputSlots[index];
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            boolean hotbar = event.getClick() == ClickType.NUMBER_KEY;
            int hotbarSlot = event.getHotbarButton();
            ItemStack external = hotbar ? player.getInventory().getItem(hotbarSlot)
                    : player.getInventory().getItemInOffHand();
            if (!Items.isReal(external)) return;
            ItemStack capturedExternal = external.clone();
            ItemStack capturedCurrent = inventory.getItem(slot);
            if (Items.isReal(capturedCurrent)) {
                plugin.lang().send(player, "recipe.slot-has-item");
                return;
            }
            RecipeManager.Ingredient ingredient = selected.ingredients().get(index);
            if (!ingredient.matchesType(capturedExternal)) {
                plugin.lang().send(player, "recipe.wrong-ingredient", "ingredient", ingredient.displayName());
                return;
            }
            transactionPending = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!isStillOpen()) return;
                    ItemStack latest = hotbar ? player.getInventory().getItem(hotbarSlot)
                            : player.getInventory().getItemInOffHand();
                    if (!Items.isReal(latest) || !latest.isSimilar(capturedExternal)
                            || latest.getAmount() != capturedExternal.getAmount()
                            || Items.isReal(inventory.getItem(slot))) return;
                    int moved = Math.min(ingredient.amount(), capturedExternal.getAmount());
                    ItemStack placed = capturedExternal.clone();
                    placed.setAmount(moved);
                    ItemStack remainder = decrease(capturedExternal, moved);
                    if (hotbar) player.getInventory().setItem(hotbarSlot, remainder);
                    else player.getInventory().setItemInOffHand(remainder);
                    inventory.setItem(slot, placed);
                    redrawInputHints();
                    playInsert();
                } finally {
                    transactionPending = false;
                }
            });
            return;
        }

        ItemStack cursor = event.getView().getCursor();
        ItemStack capturedCursor = Items.isReal(cursor) ? cursor.clone() : null;
        ItemStack current = inventory.getItem(slot);
        ItemStack capturedCurrent = Items.isReal(current) ? current.clone() : null;
        RecipeManager.Ingredient ingredient = selected.ingredients().get(index);

        if (capturedCursor != null && !ingredient.matchesType(capturedCursor)) {
            plugin.lang().send(player, "recipe.wrong-ingredient", "ingredient", ingredient.displayName());
            return;
        }
        if (capturedCursor != null && capturedCurrent != null && !capturedCurrent.isSimilar(capturedCursor)) {
            plugin.lang().send(player, "recipe.slot-has-item");
            return;
        }
        int already = capturedCurrent == null ? 0 : capturedCurrent.getAmount();
        int room = Math.max(0, ingredient.amount() - already);
        if (capturedCursor != null && room <= 0) {
            plugin.lang().send(player, "recipe.slot-full", "ingredient", ingredient.displayName());
            return;
        }
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isStillOpen()) { transactionPending = false; return; }
            if (capturedCursor == null) {
                if (capturedCurrent != null) {
                    inventory.setItem(slot, ingredientPlaceholder(index, 0));
                    player.setItemOnCursor(capturedCurrent);
                }
            } else {
                int moved = Math.min(room, capturedCursor.getAmount());
                if (capturedCurrent != null) {
                    capturedCurrent.setAmount(already + moved);
                    inventory.setItem(slot, capturedCurrent);
                } else {
                    ItemStack placed = capturedCursor.clone();
                    placed.setAmount(moved);
                    inventory.setItem(slot, placed);
                }
                player.setItemOnCursor(decrease(capturedCursor, moved));
            }
            redrawInputHints();
            playInsert();
            transactionPending = false;
        });
    }

    private ItemStack decrease(ItemStack source, int amount) {
        int left = source.getAmount() - amount;
        if (left <= 0) return null;
        ItemStack remainder = source.clone();
        remainder.setAmount(left);
        return remainder;
    }

    private boolean isStillOpen() {
        return player.isOnline() && player.getOpenInventory().getTopInventory() == inventory;
    }

    private boolean creativeDenied() {
        return player.getGameMode() == GameMode.CREATIVE
                && !plugin.getConfig().getBoolean("upgrade.allow-creative", false);
    }

    private boolean rejectCreativeMode() {
        if (!creativeDenied()) return false;
        plugin.lang().send(player, "upgrade.creative-denied");
        closeNextTick();
        return true;
    }

    private void placeInput(int index, ItemStack item) {
        int slot = inputSlots[index];
        int required = selected.ingredients().get(index).amount();
        int amount = Math.min(required, item.getAmount());
        item.setAmount(amount);
        inventory.setItem(slot, item);
        redrawInputHints();
        playInsert();
    }

    private void shiftIngredientFromPlayerInventory(InventoryClickEvent e) {
        int sourceSlot = e.getSlot();
        Inventory bottom = e.getClickedInventory();
        ItemStack clicked = e.getCurrentItem();
        if (!Items.isReal(clicked) || bottom == null) return;
        ItemStack snapshot = clicked.clone();
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isStillOpen()) { transactionPending = false; return; }
            ItemStack source = bottom.getItem(sourceSlot);
            if (!Items.isReal(source) || !source.isSimilar(snapshot)) { transactionPending = false; return; }
            for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
                RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
                if (!ingredient.matchesType(source)) continue;
                ItemStack existing = inventory.getItem(inputSlots[i]);
                int existingAmount = Items.isReal(existing) ? existing.getAmount() : 0;
                int room = ingredient.amount() - existingAmount;
                if (room <= 0 || (Items.isReal(existing) && !existing.isSimilar(source))) continue;
                int moved = Math.min(room, source.getAmount());
                if (Items.isReal(existing)) existing.setAmount(existingAmount + moved);
                else {
                    ItemStack placed = source.clone();
                    placed.setAmount(moved);
                    inventory.setItem(inputSlots[i], placed);
                }
                ItemStack remaining = decrease(source, moved);
                bottom.setItem(sourceSlot, remaining);
                redrawInputHints();
                playInsert();
                transactionPending = false;
                return;
            }
            transactionPending = false;
        });
    }

    @Override
    public void handleDrag(InventoryDragEvent e) {
        if (creativeDenied()) {
            e.setCancelled(true);
            rejectCreativeMode();
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!touchesTop) return;
        e.setCancelled(true);
        if (selected == null || transactionPending || e.getRawSlots().size() != 1) return;
        int raw = e.getRawSlots().iterator().next();
        int index = indexOfInputSlot(raw);
        if (index < 0) return;
        ItemStack oldCursor = e.getOldCursor();
        if (!Items.isReal(oldCursor) || !selected.ingredients().get(index).matchesType(oldCursor)) return;
        ItemStack captured = oldCursor.clone();
        ItemStack originalCursor = oldCursor.clone();
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isStillOpen()) { transactionPending = false; return; }
            ItemStack current = inventory.getItem(raw);
            int existing = Items.isReal(current) ? current.getAmount() : 0;
            if (Items.isReal(current) && !current.isSimilar(captured)) { transactionPending = false; return; }
            int room = selected.ingredients().get(index).amount() - existing;
            if (room <= 0) { transactionPending = false; return; }
            int moved = Math.min(room, captured.getAmount());
            if (Items.isReal(current)) current.setAmount(existing + moved);
            else {
                captured.setAmount(moved);
                inventory.setItem(raw, captured);
            }
            ItemStack remainder = decrease(originalCursor, moved);
            player.setItemOnCursor(remainder);
            redrawInputHints();
            playInsert();
            transactionPending = false;
        });
    }

    private int indexOfInputSlot(int raw) {
        for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
            if (inputSlots[i] == raw) return i;
        }
        return -1;
    }

    private void beginProcess() {
        if (rejectCreativeMode() || transactionPending) return;
        transactionPending = true;
        Bukkit.getScheduler().runTask(plugin, this::processRecipe);
    }

    private void processRecipe() {
        if (creativeDenied()) {
            plugin.lang().send(player, "upgrade.creative-denied");
            closeNextTick();
            transactionPending = false;
            return;
        }
        if (!isStillOpen() || selected == null) { transactionPending = false; return; }
        for (int i = 0; i < selected.ingredients().size(); i++) {
            ItemStack input = inventory.getItem(inputSlots[i]);
            RecipeManager.Ingredient ingredient = selected.ingredients().get(i);
            if (!ingredient.matches(input)) {
                plugin.lang().send(player, "recipe.missing-ingredient", "ingredient", ingredient.displayName(), "amount", String.valueOf(ingredient.amount()));
                playFail();
                transactionPending = false;
                return;
            }
        }
        if (selected.currencyAmount() > 0) {
            if (!plugin.excellentEconomy().canWithdraw(player, selected.currencyId(), selected.currencyAmount())) {
                plugin.lang().send(player, "recipe.currency-unavailable", "currency", selected.currencyId(), "amount", String.valueOf(selected.currencyAmount()));
                playFail();
                transactionPending = false;
                return;
            }
            if (!plugin.excellentEconomy().withdraw(player, selected.currencyId(), selected.currencyAmount())) {
                plugin.lang().send(player, "recipe.payment-failed");
                playFail();
                transactionPending = false;
                return;
            }
        }
        for (int i = 0; i < selected.ingredients().size(); i++) {
            int slot = inputSlots[i];
            ItemStack stack = inventory.getItem(slot);
            int remaining = stack.getAmount() - selected.ingredients().get(i).amount();
            if (remaining <= 0) inventory.setItem(slot, ingredientPlaceholder(i, 0));
            else stack.setAmount(remaining);
        }
        Items.giveOrDrop(player, selected.result());
        plugin.lang().send(player, kind == RecipeManager.Kind.COMBINE ? "recipe.combine-success" : "recipe.craft-success", "recipe", selected.name());
        playConfiguredSound("success", Sound.BLOCK_ANVIL_USE, 0.8f,
                kind == RecipeManager.Kind.COMBINE ? 0.85f : 1.05f);
        closeNextTick();
        transactionPending = false;
    }

    private void playClick() {
        playConfiguredSound("select", Sound.BLOCK_ANVIL_PLACE, 0.45f, 1.25f);
    }

    private void playInsert() {
        playConfiguredSound("insert", Sound.BLOCK_ANVIL_LAND, 0.28f, 1.55f);
    }

    private void playFail() {
        playConfiguredSound("fail", Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
    }

    private void playConfiguredSound(String key, Sound fallback, float volume, float pitch) {
        player.playSound(player.getLocation(), layout.sound(key, fallback),
                layout.soundVolume(key, volume), layout.soundPitch(key, pitch));
    }

    private void returnInputs() {
        if (selected == null) return;
        for (int i = 0; i < Math.min(inputSlots.length, selected.ingredients().size()); i++) {
            int slot = inputSlots[i];
            ItemStack item = inventory.getItem(slot);
            if (Items.isReal(item)) Items.giveOrDrop(player, item);
            inventory.setItem(slot, ingredientPlaceholder(i, 0));
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent e) {
        if (transition || selected == null) return;
        returnInputs();
    }
}
