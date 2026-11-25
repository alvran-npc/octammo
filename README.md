# OctaMMO 1.2.1-2026-dev-1

This development update removes the direct AdvancedEnchantments integration, improves MMOItems Item Fixer enhancement recovery, and blocks Combine/Crafting in Creative mode using the existing Item Upgrade setting. Recipe interactions remain static; no GUI animation loops are used.

OctaMMO is a modular Paper plugin for MMOItems servers. This release retains the existing Altar Forge and public API while removing the unused direct AdvancedEnchantments integration and improving MMOItems enhancement recovery.

## Compatibility

| Component | Requirement |
|---|---|
| Minecraft server | Paper 1.21.11 |
| Java runtime | Java 21 |
| MMOItems | Required for Altar Forge, MMOItems recipe ingredients/results, and Item Fixer |
| MythicLib | Required by MMOItems integration |
| ExcellentEconomy | Optional; required only by recipes that charge a configured ExcellentEconomy currency |
| Vault | Optional; continues to support the existing Altar Forge money cost |
| PlaceholderAPI | Optional |
| WorldGuard | Optional |
| Floodgate | Optional; Bedrock players are not supported by the custom GUI flows |

Optional integrations are loaded through soft dependencies or reflection. OctaMMO can still start when ExcellentEconomy or other optional providers are not installed; the related features report unavailable providers when used.

## Build

Requirements: JDK 21 and Apache Maven 3.9 or newer.

```bash
mvn -B clean test package
```

The plugin jar is generated at:

```text
octammo-plugin/target/OctaMMO-1.2.1-2026-dev-1.jar
```

The jar shades the `octammo-api` module. The API classes are included in the plugin jar and are not shaded from third-party plugins.

## Installation

1. Install Paper 1.21.11 and Java 21.
2. Install MMOItems and MythicLib for MMOItems functionality.
3. Optionally install ExcellentEconomy before OctaMMO when recipes use custom-currency costs.
4. Place `OctaMMO-1.2.1-2026-dev-1.jar` in the server's `plugins` directory and restart.
5. Review `plugins/OctaMMO/config.yml`, `recipes/combine.yml`, and `recipes/crafting.yml`.
6. Restart or run `/octammo reload` after editing plugin configuration files.

The first start creates the default configuration, GUI layouts, language file, and recipe files. Existing config values are retained during migration; newly introduced keys are appended and the previous file is backed up under `plugins/OctaMMO/backups/`. When an older development build left `recipes/combine.yml` or `recipes/crafting.yml` with an empty `recipes:` section, OctaMMO backs up that empty file and seeds the working example recipe. Non-empty recipe files are never overwritten.

**Language files are English-only in this release.** If you are upgrading from an older version, change `language: en` in `config.yml`. The old `lang/id.yml` file is no longer used.

## Commands and permissions

| Command | Permission | Default | Description |
|---|---|---:|---|
| `/octammo help` | `octammo.use` | true | Show command help |
| `/octammo version` | none | all | Show plugin version and authors |
| `/octammo reload` | `octammo.admin` | op | Reload supported configuration files |
| `/octammo list` | `octammo.admin` | op | List Altar Forge stations |
| `/octammo upgrade [station] [player] [path]` | `octammo.upgrade` | true | Open an Altar Forge station |
| `/itemupgrade [station] [path]` | `octammo.upgrade` | true | Shortcut for Altar Forge |
| `/octammo combine` | `octammo.combine` | true | Open the Combine Station |
| `/octammo crafting` | `octammo.crafting` | true | Open the Crafting Station |
| `/octammo fixer` | `octammo.fixer` | true | Open the MMOItems Item Fixer |
| `/octammo enchanting` | `octammo.enchant` | true | Open the custom enchanting GUI |
| `/octammo search <keyword>` | `octammo.enchant` | true | Submit a search while the enchanting GUI is waiting for input |

Right-clicking an enchanting table also opens the custom enchanting GUI when `modules.custom-enchanting` is enabled. The aliases `/octa` and `/omm` are available for `/octammo`.

Additional permissions:

- `octammo.upgrade.<station-id>` can be required per station when `upgrade.require-station-permission` is enabled.
- `octammo.upgrade.*` is available for permission managers that grant all station permissions.
- `octammo.interact.bypass` bypasses the MMOItems interaction lock.

## Modules

### 1. Altar Forge — Item Upgrade

The existing 54-slot Altar Forge, configurable stations and paths, upgrade requirements, runes, boosters, protection items, result chances, loading sequence, pending rewards, audit log, and public API are retained.

- One YAML file in `upgrades/` defines a station; each `paths:` entry defines a path.
- See `upgrades/example-sword.yml` for the supported station schema.
- GUI layouts are located in `guis/`.
- Item-upgrade behavior is enabled by `modules.item-upgrade`.

The existing public API remains at API version 2. Existing integrations using `OctaMMO.getApi()` or the Bukkit Services Manager can continue to use the published API types.

### 2. Custom Enchanting

When enabled, interacting with an enchanting table opens the six-row, 54-slot OctaMMO GUI. The input slot is on the left and compatible Vanilla enchantments are displayed on the right. Controls provide keyword search, the Vanilla filter, a paged enchantment guide, previous/next page navigation, input return, and close. Search is entered in chat after clicking the spyglass; `/octammo search <keyword>` is also available while a search prompt is active. The pending search state survives a chat plugin closing the inventory, expires after the configured timeout, and returns the input item on cancellation, timeout, quit, or shutdown.

This release supports Vanilla Minecraft enchantments through the Bukkit enchantment registry only. Applicability is checked with `Enchantment.canEnchantItem`, and existing enchantments are checked with Bukkit's conflict rules (for example, Sharpness and Smite cannot be applied together). MMOItems items are identified by their `TYPE/ID` for display and traceability; an enchantment is not shown as applicable unless Bukkit accepts its material and the conflict check passes. The direct AdvancedEnchantments hook and all AE-specific GUI behavior have been removed.

A normal click applies the next level; shift-click applies the enchantment's maximum level. Enchanting takes one item per operation. Costs are charged in player experience levels:

```yaml
enchanting:
  costs:
    vanilla-per-level: 1
```

The enchantment guide lists Vanilla enchantments registered by the running server. Search matches the enchantment ID, display name, and description.

### 3. Combine Station

Open with `/octammo combine`. Recipes are declared in `recipes/combine.yml`. Combine is blocked for Creative-mode players whenever the existing `upgrade.allow-creative` setting is `false`, matching Item Upgrade. A recipe may use up to six input slots and can consume vanilla materials, MMOItems identified by `TYPE/ID`, or a mixture of both. The recipe result can also be a vanilla material or an MMOItem.

Example:

```yaml
recipes:
  reforged_blade:
    name: "Reforged Blade"
    description:
      - "Combine two fragments into a new weapon."
    ingredients:
      - type: MMOITEMS
        item-type: SWORD
        item-id: BLADE_FRAGMENT
        amount: 2
      - type: MATERIAL
        material: NETHERITE_INGOT
        amount: 1
    result:
      type: MMOITEMS
      item-type: SWORD
      item-id: REFORGED_BLADE
      amount: 1
```

Replace the sample Type and Item ID values with IDs that exist on your server. The shipped `combine.yml` contains a working vanilla-material example so the menu is usable immediately. Replace or extend it with MMOItems recipes using the exact Type and Item ID values from your server.

### 4. Crafting Station

Open with `/octammo crafting`. Recipes are declared in `recipes/crafting.yml` and use the same ingredient/result format as Combine. Crafting is blocked for Creative-mode players whenever the existing `upgrade.allow-creative` setting is `false`, matching Item Upgrade. Each crafting recipe may also charge a native ExcellentEconomy currency:

```yaml
currency:
  id: octacoin
  amount: 1000
```

`id` must be the actual ExcellentEconomy currency ID, not its display name or symbol. OctaMMO uses ExcellentEconomy's service API and currency-specific `getBalance(Player, String)` / `withdraw(Player, String, double)` operations. It does not route these recipe costs through Vault's primary economy provider.

The workstation GUIs use separate colored-glass frames and static recipe/output panels. Their select, insert, fail, and success anvil sounds can be changed in `guis/combine.yml` and `guis/crafting.yml`. No GUI animation loop is used. The shipped `crafting.yml` contains a working vanilla-material example with no currency requirement, so the menu is usable immediately. Add the optional currency section to any recipe after replacing `octacoin` with an existing currency ID. If the currency is not registered or the provider is unavailable, the craft is refused and the ingredients are not consumed.

### 5. MMOItems Item Fixer

Open with `/octammo fixer`. Place one MMOItems item in the input slot and use **Repair & Restore**.

- Rebuilds the item through MMOItems' native `MMOItemReforger` using its canonical Type and ID.
- Detects installed gemstones and restores each original gemstone MMOItem using `LiveMMOItem.extractGemstones()`, including historic gemstone data where MMOItems can recover it.
- Detects applied skins through `MMOITEMS_SKIN_ID` and `MMOITEMS_SKIN_TYPE`, then returns the matching skin MMOItem as a separate item.
- Removes the installed MMOItems upgrade state (`+N`), gemstones, and skin from the rebuilt base item.
- If MMOItems reports more installed gemstones than it can restore, or the skin template is missing, the operation is cancelled and the original item remains in the input slot.
- Native Minecraft enchantments are preserved when `item-fixer.preserve-vanilla-enchantments` is enabled. Other supported MMOItems data follows the server's `item-revision.keep-data` settings except upgrades, gemstones and skins, which are deliberately disabled for this operation.
- The LiveMMOItem gemstone read is performed only after the player clicks Repair; it is not run during GUI refreshes or by repeating tasks.

Test with copies of items first. Legacy gemstones that do not contain sufficient historic data may not be recoverable; in that case the fixer refuses to consume the original item.

### 6. Startup Banner

The enable banner prints a static cyan, line-art OCTAMMO wordmark (large outlined letters), the resolved plugin version, the server version, and a summary of optional integrations. Configure it under `startup-banner` in `config.yml`. The previous built-in placeholder wordmark is automatically replaced on upgrade; a manually customized logo is preserved. It does not use animations or repeating tasks.

### 7. MMOItems Interaction Lock

When enabled, the interaction listener prevents MMOItems items in the main/off hand from placing blocks, breaking blocks, tilling farmland, or creating dirt paths. The behavior is controlled by `interact-lock:` in `config.yml`; players with `octammo.interact.bypass` are exempt.

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Module toggles, enchanting costs, fixer behavior, interaction lock, Altar Forge settings |
| `lang/en.yml` | English chat messages and GUI text |
| `guis/*.yml` | Enchanting, Item Fixer, Combine, Crafting, and Altar Forge GUI titles, decoration, slot layouts, and workstation sounds |
| `recipes/combine.yml` | Combine recipes |
| `recipes/crafting.yml` | Crafting recipes and currency costs |
| `upgrades/*.yml` | Altar Forge stations and paths |

All shipped configuration comments, messages, GUI labels, lore, and documentation are in English.

## Developer API

The public API is provided by the `octammo-api` Maven module and is included in the plugin jar. For another plugin project, install the API module locally:

```bash
mvn -pl octammo-api install
```

Then use it as a `provided` dependency with version `1.2.1-2026-dev-1` and declare OctaMMO as a soft dependency. The existing API version is `2`; this release keeps its current method/event contracts.

## Validation status for this source package

- YAML resource parsing and Maven descriptor parsing are checked before packaging.
- Java source syntax is checked separately from dependency-backed compilation.
- Maven compilation and JUnit execution must be run with JDK 21 and Maven against the configured Paper and optional-plugin repositories.
- Live Paper-server integration still requires testing against the exact Paper, MMOItems, MythicLib, and ExcellentEconomy builds installed on your server.

The GitHub Actions workflow runs `mvn -B clean test package` on pushes and pull requests. Treat the plugin as a staging build until that workflow or a local Maven build succeeds and the GUI operations have been tested on a copy of your server data.

## Notes and testing

- Always test recipe and fixer behavior on a staging server with copies of production items.
- ExcellentEconomy currency transactions require the currency to exist and operations not to be locked by ExcellentEconomy.
- This source bundle was prepared without Maven installed or dependency repository access in the build environment. Run the Maven command above or use the included GitHub Actions workflow to perform the authoritative dependency-backed compile and test.


## External API references

- [ExcellentEconomy — Developer API](https://nightexpressdev.com/excellenteconomy/utility/developer-api/)


## Feature GUI configuration

The following files are generated in `plugins/OctaMMO/guis/` and can be edited without rebuilding the plugin:

- `enchanting.yml` — title, glass-pane decoration, input/control slots, and enchantment grid slots.
- `item-fixer.yml` — title, glass-pane decoration, and input/action slots.
- `combine.yml` — Combine Forge title, glass-pane palette, recipe slots, input slots, and action slots.
- `crafting.yml` — Crafting Atelier title, glass-pane palette, recipe slots, input slots, and action slots.
- `default-upgrade.yml`, `path-select.yml`, and `confirm.yml` — Altar Forge layouts.

All slot values use the Bukkit inventory index range `0-53`. Keep interactive slots unique and do not overlap the enchantment grid with the control slots.

The enchanting input supports normal cursor placement, drag-and-drop into the configured input slot, shift-click from the player inventory, number-key swaps, and offhand swaps. Search is entered in chat after clicking the search icon; `/octammo search <keyword>` is available as a command fallback while a search prompt is active.

Crafting and Combine use separate configurable GUI layouts. Both use static glass-pane frames and decorative workbench sections; no per-tick GUI animation or repeating animation task is scheduled. Ingredient insertion, recipe selection, and successful processing use short anvil sounds.

Enchantment entries are checked against the inserted item's Bukkit material and, when MMOItems is installed, its MMOItems `TYPE/ID`. Incompatible entries are shown in red and cannot be applied. Vanilla enchantment conflicts are checked using Bukkit's enchantment conflict rules.
