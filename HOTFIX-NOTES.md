# OctaMMO 1.2.1-2026-dev-1

## Requested changes

### AdvancedEnchantments

- Removed the direct AdvancedEnchantments hook class and its initialization.
- Removed AdvancedEnchantments from the plugin soft-dependency list and OctaMMO startup hook summary.
- Removed AE-specific enchanting and Item Fixer behavior. Custom Enchanting now lists Vanilla Minecraft enchantments through Bukkit only.
- MMOItems' own `ReforgeOptions` preservation value for external enchantment metadata remains enabled. This is an MMOItems data-preservation option, not an OctaMMO AdvancedEnchantments hook.

### MMOItems Item Fixer

- Reads gemstone socket data and restores each recoverable gemstone as its original MMOItems Type/ID item, including MMOItems-provided historical stat data.
- Detects the applied skin using MMOItems' `MMOITEMS_SKIN_ID` and `MMOITEMS_SKIN_TYPE` tags and returns the matching skin template as a separate item.
- Rebuilds through MMOItems' native Reforger with upgrades, gemstones, and skins disabled, removing the installed `(+N)` upgrade state.
- Verifies the rebuilt item retains its original Type/ID and no longer contains gemstones, an installed skin, or a positive upgrade level.
- Cancels the repair and keeps the original input item if a gemstone/skin cannot be restored or the rebuilt output fails verification.
- The full gemstone recovery read runs only after the player confirms Repair.

### Creative mode

- Combine and Crafting use the existing `upgrade.allow-creative` setting and `upgrade.creative-denied` message.
- The restriction is applied when opening the command, interacting with the recipe GUI, and immediately before processing.
- No new permission or configuration key was added.

## Preserved behavior

- Existing Altar Forge and public API behavior is not intentionally changed.
- Existing command/permission definitions are retained.
- Existing MMOItems interaction lock behavior and bypass permission are retained exactly as present in the source being updated.
- GUI layouts, startup wordmark, and unrelated recipe behavior are not changed by this update.

## Validation

- YAML and Maven POM structure are validated before packaging.
- Java source structure is checked separately from dependency-backed compilation.
- A full Maven build and live Paper/MMOItems gameplay test must still be run with the exact server dependencies. This environment does not have Maven installed, so no successful compile or runtime test is claimed.
