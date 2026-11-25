package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.event.UpgradeLoadingEndEvent;
import io.octammo.api.event.UpgradeLoadingStartEvent;
import io.octammo.api.event.UpgradeResultEvent;
import io.octammo.core.Items;
import io.octammo.core.PlayerStats;
import io.octammo.core.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The title loading (1% to 100%) and the lock on the player. There is ONE repeating task for all sessions: it only
 * exists while at least one session runs, and a title is only sent when the percent changed.
 * The result is decided before the loading starts and delivered at 100% (or at once on quit / shutdown).
 */
public final class LoadingManager {
    private final OctaMMOPlugin plugin;
    private final Map<UUID, UpgradeSession> sessions = new HashMap<>();
    private BukkitTask task;

    public LoadingManager(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    /** O(1) check used on the first line of every locked listener. */
    public boolean isUpgrading(UUID id) {
        return sessions.containsKey(id);
    }

    public boolean isUpgrading(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public UpgradeSession session(UUID id) {
        return sessions.get(id);
    }

    /** The lock settings of the running session of this player. */
    public LoadingConfig.Lock lockOf(UUID id) {
        UpgradeSession s = sessions.get(id);
        return s == null ? null : s.cfg.lock();
    }

    /**
     * Starts the loading for an outcome that is already decided and paid.
     * @param skipLoading deliver at once (GUI-less API upgrades)
     */
    public void start(Player p, StationData station, PathData path, UpgradeEngine.Outcome outcome, boolean skipLoading) {
        LoadingConfig cfg = LoadingConfig.read(plugin, station, path);
        boolean animate = !skipLoading && cfg.enabled() && cfg.duration() > 0;
        if (animate) {
            UpgradeLoadingStartEvent event = new UpgradeLoadingStartEvent(p, station, path, cfg.duration());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) animate = false;
            else cfg = cfg.withDuration(event.getDurationTicks());
        }
        UpgradeSession s = new UpgradeSession(p.getUniqueId(), p.getName(), station, path, outcome, cfg, skipLoading);
        sessions.put(p.getUniqueId(), s);
        if (!animate) {
            finish(s, true);
            return;
        }
        plugin.pending().save(s);   // safety copy, written async
        applyLock(p, s);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void applyLock(Player p, UpgradeSession s) {
        LoadingConfig.Lock lock = s.cfg.lock();
        int ticks = s.cfg.duration() + 40;
        if (lock.freeze()) {
            s.previousSlow = p.getPotionEffect(PotionEffectType.SLOWNESS);
            s.previousJump = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
        }
        if (lock.invulnerable()) {
            s.previousInvulnerable = p.isInvulnerable();
            p.setInvulnerable(true);
        }
    }

    private void releaseLock(Player p, UpgradeSession s) {
        LoadingConfig.Lock lock = s.cfg.lock();
        if (lock.freeze()) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            if (s.previousSlow != null) p.addPotionEffect(s.previousSlow);
            if (s.previousJump != null) p.addPotionEffect(s.previousJump);
        }
        if (lock.invulnerable()) p.setInvulnerable(s.previousInvulnerable);
    }

    private void tick() {
        if (sessions.isEmpty()) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            return;
        }
        for (UpgradeSession s : new ArrayList<>(sessions.values())) {
            if (s.done) continue;
            Player p = Bukkit.getPlayer(s.playerId);
            if (p == null || !p.isOnline()) {
                finish(s, true);
                continue;
            }
            s.elapsed++;
            int percent = (int) Math.max(1, Math.min(100, 1 + (long) s.elapsed * 99 / Math.max(1, s.cfg.duration())));
            if (percent != s.percent && (percent == 1 || percent == 100 || percent % s.cfg.percentStep() == 0)) {
                s.percent = percent;
                sendTitle(p, s, percent);
                if (s.cfg.soundEnabled() && percent % s.cfg.soundEvery() == 0 && percent != s.lastSoundPercent) {
                    s.lastSoundPercent = percent;
                    Sounds.playRaw(p, s.cfg.tickSound(), 0.6f, 0.5f + percent / 100f * 1.5f);
                }
            }
            if (s.elapsed >= s.cfg.duration()) finish(s, false);
        }
        if (sessions.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void sendTitle(Player p, UpgradeSession s, int percent) {
        Map<String, String> ph = Map.of("percent", String.valueOf(percent));
        p.showTitle(Title.title(plugin.lang().render(s.cfg.title(), p, ph), plugin.lang().render(s.cfg.subtitle(), p, ph),
                Title.Times.times(ticks(s.cfg.fadeIn()), ticks(Math.max(s.cfg.stay(), 2)), ticks(s.cfg.fadeOut()))));
    }

    private static Duration ticks(int t) {
        return Duration.ofMillis(t * 50L);
    }

    /**
     * Delivers the result. {@code instant} skips the title / reopen (quit, shutdown, skip-loading).
     * A player who is not online keeps the pending-rewards file and gets the items on the next join.
     */
    public void finish(UpgradeSession s, boolean instant) {
        if (s.done) return;
        s.done = true;
        sessions.remove(s.playerId);
        UpgradeEngine.Outcome oc = s.outcome;

        Player p = Bukkit.getPlayer(s.playerId);
        boolean online = p != null && p.isOnline();
        if (online) {
            releaseLock(p, s);
            for (ItemStack item : oc.rewards()) Items.giveOrDrop(p, item);
            if (oc.refundPercent() > 0) plugin.costService().refundNonItems(p, oc.costs(), oc.refundPercent());
            plugin.pending().delete(s);

            Map<String, String> ph = Map.of("player", p.getName(), "output-name", oc.outputName(),
                    "path", s.path.displayName(), "station", s.station.displayName(), "amount", String.valueOf(oc.outputAmount()));
            Effects effects = oc.success() ? s.path.onSuccess() : s.path.onFailEffects();
            if (oc.success()) {
                plugin.stats().add(p, PlayerStats.UPGRADE_SUCCESS, 1);
                plugin.lang().send(p, "upgrade.success", "item", oc.outputName(), "amount", String.valueOf(oc.outputAmount()));
                Sounds.play(plugin, p, "success");
            } else {
                plugin.stats().add(p, PlayerStats.UPGRADE_FAIL, 1);
                plugin.lang().send(p, oc.protectionUsed() ? "upgrade.failed-protected" : failKey(s));
                Sounds.play(plugin, p, "fail");
            }
            effects.run(plugin, p, ph);
            if (!instant) {
                String head = effects.title().isBlank()
                        ? (oc.success() ? "{upgrade.loading.result-success}" : "{upgrade.loading.result-fail}") : effects.title();
                Component title = plugin.lang().render(head, p, ph);
                p.showTitle(Title.title(title, Component.empty(),
                        Title.Times.times(Duration.ZERO, ticks(Math.max(2, s.cfg.resultTicks())), ticks(10))));
            }
        }

        plugin.upgradeLog().log(s.playerName + " | " + s.station.id() + "/" + s.path.id()
                + " | " + (oc.success() ? "SUCCESS" : "FAIL") + (oc.protectionUsed() ? " (protected)" : "")
                + " | chance=" + fmt(oc.chance()) + "% (booster +" + fmt(oc.bonus()) + "%)"
                + " | amount=x" + oc.multiplier() + " (" + oc.outputAmount() + " result) | input=" + s.path.input().key() + " | consumed=" + String.join(", ", oc.consumed())
                + " | " + (online ? "delivered" : "DEFERRED to next join") + " | session=" + s.id);

        if (p != null) {
            Bukkit.getPluginManager().callEvent(new UpgradeResultEvent(p, s.station, s.path, oc.success(),
                    oc.inputBefore(), oc.output(), oc.consumed()));
            Bukkit.getPluginManager().callEvent(new UpgradeLoadingEndEvent(p, s.station, s.path, oc.success(), instant));
        }
        if (online && !instant && !s.headless && s.cfg.reopen()) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.upgrades().reopen(p, s.station, s.path));
        }
    }

    private String failKey(UpgradeSession s) {
        return switch (s.path.fail().input()) {
            case KEEP -> "upgrade.failed-keep";
            case DOWNGRADE -> "upgrade.failed-downgrade";
            case CONSUME, BREAK -> "upgrade.failed-destroy";
        };
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** The player left: finish at once, the items go to the inventory that is about to be saved. */
    public void handleQuit(Player p) {
        UpgradeSession s = sessions.get(p.getUniqueId());
        if (s != null) finish(s, true);
    }

    /** Server stop / plugin disable. */
    public void finishAll() {
        for (UpgradeSession s : new ArrayList<>(sessions.values())) finish(s, true);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
