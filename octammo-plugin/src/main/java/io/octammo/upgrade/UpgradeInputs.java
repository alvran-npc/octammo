package io.octammo.upgrade;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Everything an upgrade attempt works with, as stacks. For the GUI these are the stacks in the slots,
 * for a GUI-less upgrade they were taken from the inventory.
 *
 * @param runes    one stack (or null) per rune slot of the {@link RunePlan}, same order
 * @param boosters stacks of the active booster slots (nulls allowed)
 */
public record UpgradeInputs(ItemStack input, List<ItemStack> runes, List<ItemStack> boosters, ItemStack protection) {}
