## [1.2.1-2026-dev-1] - 2026-11-25

### Changed
- Removed the direct AdvancedEnchantments hook, lifecycle setup, startup status, soft dependency, and Item Fixer extraction action.
- Kept the Custom Enchanting interface limited to Vanilla Minecraft enchantments through Bukkit's registry and native compatibility checks.

### Fixed
- Item Fixer now reads installed MMOItems gemstones through `LiveMMOItem.extractGemstones()` and restores their original MMOItems data as separate items.
- Item Fixer detects applied skins using MMOItems skin ID/type tags and returns the matching skin item as a separate MMOItem.
- Item Fixer disables MMOItems upgrade, gemstone, and skin transfer during native reforge, removing the installed `(+N)` upgrade state while rebuilding the base item.
- The repair transaction is cancelled without consuming the original item when a stored gemstone or skin cannot be restored.
- Combine and Crafting now use the same `upgrade.allow-creative` setting and `upgrade.creative-denied` message as Item Upgrade, with checks at menu opening and before processing.

### Compatibility and scope
- No Altar Forge behavior, public API contract, interaction-lock behavior, or permission defaults were intentionally changed.
- Gemstone recovery uses MMOItems' LiveMMOItem API only when a player confirms Item Fixer repair; it is not run during inventory refreshes or repeating tasks.

### Verification
- YAML and Maven descriptor validation are included in this source-package check.
- A dependency-backed Maven build and live Paper/MMOItems test must still be run on the target server before production use.

---

# Changelog

## [1.2.0-SNAPSHOT] - 2026-11-14

### Added
- Added a six-row Custom Enchanting GUI that replaces enchanting-table interaction when enabled.
- Added vanilla enchantment discovery and item-applicability filtering through the Bukkit registry.
- Added AdvancedEnchantments group shortcuts for Simple, Unique, Elite, Ultimate, Legendary, Fabled, and Heroic tiers.
- Added a paged Enchantment Guide that displays custom enchantment descriptions, tiers, maximum levels, and applicability rules.
- Added chat-based enchantment search, tier cycling, page controls, and search reset behavior.
- Added per-session AE level tracking so applied custom enchantments advance correctly even when an AE build exposes no readable enchant map.
- Added support for modern nested `Enchantments/<folder>/enchantments.yml` definitions and item compatibility filtering through AE `applies` rules.
- Added AdvancedEnchantments integration through the documented `AEAPI.applyEnchant` method.
- Added experience-level costs for vanilla and AdvancedEnchantments application.
- Added single-item enchanting safeguards for stackable inputs and safe cursor-stack splitting.
- Added Combine/Crafting input type validation and partial-stack insertion to prevent wrong ingredients from being accepted.
- Added `/octammo combine` with recipe-defined vanilla and MMOItems ingredients/results.
- Added `/octammo crafting` with recipe-defined ingredients/results and optional ExcellentEconomy custom-currency costs.
- Added a native ExcellentEconomy currency bridge using its Bukkit service API instead of relying only on Vault's primary currency.
- Added `/octammo fixer` with MMOItems template rebuilding, vanilla-enchantment restoration, and AdvancedEnchantments book extraction when enchant data can be read.
- Added MMOItems native Revision/Reforger integration for Item Fixer lore reconstruction, with the old lore transfer explicitly disabled.
- Added preservation of supported MMOItems data according to `item-revision.keep-data`, including returning reforge outputs such as removed gems.
- Set the Item Fixer permission to default true for consistency with the other player-facing modules; administrators can restrict it through permissions.
- Added module toggles and feature permissions for Custom Enchanting, Combine, Crafting, and Item Fixer.
- Added working vanilla-material sample recipes for both Combine and Crafting, plus MMOItems and ExcellentEconomy configuration examples.
- Added English recipe files, GUI messages, README, and this changelog.

### Changed
- Updated the Maven project version to `1.2.0-SNAPSHOT`.
- Updated command help, plugin metadata, and dependency declarations.
- Standardized shipped language/configuration resources to English-only.
- Kept the existing Altar Forge implementation and public API version 2 contracts.

### Important behavior notes
- Item Fixer repair uses MMOItems native data-transfer options while regenerating lore; extraction intentionally creates a clean Type/ID template and can reset item-specific data.
- The Item Fixer has not been runtime-tested against every MMOItems/AdvancedEnchantments release; test on copies of production items before enabling player use.
- AdvancedEnchantments extraction uses an ItemStack-based getter if the installed build exposes one and otherwise falls back to matching configured enchantment IDs/display names against item lore. Validate extraction against the exact AdvancedEnchantments build installed on the server.
- Extracted custom enchant books are created as `ENCHANTED_BOOK` items using `AEAPI.applyEnchant`, matching the standard AdvancedEnchantments custom-book item type.
- Run a staging-server test before production deployment.


## 1.2.0-SNAPSHOT — GUI interaction and compatibility hotfix

- Fixed cursor handling for input slots by using the active InventoryView cursor instead of deprecated event cursor mutation.
- Added controlled drag-and-drop insertion into the configured input slot; decorative and action slots remain protected.
- Added number-key and offhand input swapping, plus safe outside-click handling.
- Added cyan/purple stained-glass GUI frames to Enchanting, Enchantment Guide, and Item Fixer.
- Moved Enchanting and Item Fixer titles, decoration, and slot layouts into `guis/enchanting.yml` and `guis/item-fixer.yml`.
- Added MMOItems TYPE/ID-aware AdvancedEnchantments `applies` matching and visible item identity information.
- Incompatible enchantments now appear red and cannot be applied; vanilla conflicts use Bukkit conflict rules and AE conflicts use configured `conflicts` lists.
- Added safer parsing for both list and comma-separated AdvancedEnchantments conflict definitions.


## [1.2.0-BETA] - 2026-11-15

### Added
- Added a configurable startup banner that prints the full OCTAMMO ASCII wordmark, plugin version, server version, and detected hook status.
- Added `/octammo search <keyword>` as a fallback path when chat plugins intercept or suppress the enchanting search chat event; the prompt timeout is configurable in `config.yml`.
- Added separate `guis/combine.yml` and `guis/crafting.yml` files for workstation titles, primary/accent glass-pane decoration, recipe entries, ingredient slots, output, action controls, and configurable sounds.
- Added concise anvil sounds for recipe selection, ingredient insertion, failed validation, and successful crafting/combining.

### Fixed
- Replaced the previous synthetic AdvancedEnchantments book construction with AE's own `/ae give <player> <enchantment> <level>` command. OctaMMO captures the exact book item created by AE instead of applying the enchant to a newly constructed generic book.
- Added an inventory-delta capture/restore routine so temporarily generated AE books are removed from the player's inventory and delivered through the Item Fixer transaction only after all books have been prepared.
- Ensured already prepared AE books are returned if a later extraction in the same operation fails; the source MMOItem remains in the GUI and is not consumed.
- Reworked recipe input clicks, shift-clicks, and single-slot drags to apply inventory changes on the next server tick after the click/drag transaction is cancelled.
- Added explicit input validation for MMOItems in the Item Fixer and safe cursor handling for click and drag operations.
- Preserved pending search state when another plugin closes the enchanting view, added a `/octammo search <keyword>` fallback, added a one-minute prompt timeout, and returned held input safely on cancel, new GUI sessions, quit, or plugin shutdown.
- Updated the Item Fixer AE book failure message to explain the required free inventory slot and AE command availability.

### Performance and safety
- Kept all workbench GUIs static; no repeating animation tasks or per-tick GUI updates were added.
- Recipe lists are rendered only when a menu is created or a page is changed.
- Anvil sound type, volume, pitch, and accent-pane material are read from each workstation GUI YAML file.
- Enchant compatibility checks build a single per-refresh conflict index rather than rescanning every loaded AE enchant for each displayed entry.
- Recipe validation and currency withdrawal occur once per confirmed transaction, after ingredient checks.
- Added a startup banner toggle and hook-summary toggle under `startup-banner`.

### Verification notes
- AdvancedEnchantments documents `/ae give <player> <enchantment> <level>` as the command that gives a custom enchanted book. Runtime capture behavior must still be checked with the exact AE version and book configuration on the target server.
- Maven compilation and live Paper-server testing remain required before production deployment.


## Startup Wordmark Correction

- Replaced the compact dotted placeholder with a large, clearly readable ASCII OCTAMMO wordmark inspired by the supplied reference.
- Added configurable cyan (`&b`) coloring while retaining the normal timestamp and `[OctaMMO]` logger prefix.
- Bumped the config schema to version 8. The known previous default logo is migrated automatically; custom administrator logos are preserved.
- The banner remains static and does not schedule animation or repeating tasks.
