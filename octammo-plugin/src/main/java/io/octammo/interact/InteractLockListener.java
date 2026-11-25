package io.octammo.interact;

import io.octammo.OctaMMOPlugin;
import io.octammo.core.Items;
import io.octammo.core.MmoItems;
import io.octammo.util.ToolUse;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MMOItems items cannot place blocks, break blocks, till farmland (hoe) or make dirt paths (shovel), in either hand.
 *
 * Opening chests, anvils, workbenches, doors, gates and putting items in item frames still work: those are block /
 * entity interactions, which are never cancelled here. Sneaking + right click is blocked on purpose: vanilla then
 * skips the block interaction and tries to PLACE the item, and that placement is what gets cancelled.
 *
 * Staff with octammo.interact.bypass are not affected.
 */
public final class InteractLockListener implements Listener {
    private static final String BYPASS = "octammo.interact.bypass";

    private final OctaMMOPlugin plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private boolean enabled, place, brk, hoe, shovel, other, notify;

    public InteractLockListener(OctaMMOPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("interact-lock.enabled", true);
        place = c.getBoolean("interact-lock.block-place", true);
        brk = c.getBoolean("interact-lock.block-break", true);
        hoe = c.getBoolean("interact-lock.hoe-farmland", true);
        shovel = c.getBoolean("interact-lock.shovel-path", true);
        other = c.getBoolean("interact-lock.other-placement", true);
        notify = c.getBoolean("interact-lock.notify", true);
    }

    public void forget(UUID id) {
        lastNotice.remove(id);
    }

    /** Cheap checks first: no meta means it cannot be an MMOItem, the NBT read and the permission come last. */
    private boolean locked(Player p, ItemStack item) {
        if (!enabled || Items.isEmpty(item) || !item.hasItemMeta()) return false;
        return MmoItems.identify(item) != null && !p.hasPermission(BYPASS);
    }

    private void notice(Player p) {
        if (!notify) return;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastNotice.put(p.getUniqueId(), now);
        p.sendActionBar(plugin.lang().component("interact.blocked"));
    }

    // ------------------------------------------------------------------ placement

    /** Every block placement, main hand and offhand (also sneaking + right click on a chest). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!place || !locked(e.getPlayer(), e.getItemInHand())) return;
        e.setCancelled(true);
        notice(e.getPlayer());
    }

    /** Item frames and paintings. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHanging(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (!other || p == null || !locked(p, e.getItemStack())) return;
        e.setCancelled(true);
        notice(p);
    }

    /** Boats, minecarts, armor stands, end crystals. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        Player p = e.getPlayer();
        if (!other || p == null || !locked(p, p.getInventory().getItem(e.getHand()))) return;
        e.setCancelled(true);
        notice(p);
    }

    /** Pouring a liquid out of a bucket. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        if (!other || !locked(p, p.getInventory().getItem(e.getHand()))) return;
        e.setCancelled(true);
        notice(p);
    }

    // ------------------------------------------------------------------ breaking

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!brk || !locked(p, p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        notice(p);
    }

    // ------------------------------------------------------------------ hoe and shovel

    /**
     * Farmland and dirt paths have no event of their own, so only the USE OF THE ITEM is denied. The block's own
     * interaction stays allowed, which is why a hoe or shovel never blocks opening a chest or a door.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        String tool = item.getType().name();
        String block = e.getClickedBlock().getType().name();
        boolean hits = (hoe && ToolUse.tills(tool, block)) || (shovel && ToolUse.flattens(tool, block));
        if (!hits || !locked(e.getPlayer(), item)) return;
        e.setUseItemInHand(Event.Result.DENY);
        notice(e.getPlayer());
    }
}
