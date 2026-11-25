package io.octammo.upgrade;

import org.bukkit.potion.PotionEffect;

import java.util.UUID;

/** One upgrade in its loading phase. The outcome was decided (and everything taken) before it was created. */
public final class UpgradeSession {
    public final UUID id = UUID.randomUUID();
    public final UUID playerId;
    public final String playerName;
    public final StationData station;
    public final PathData path;
    public final UpgradeEngine.Outcome outcome;
    public final LoadingConfig cfg;
    /** GUI-less upgrades neither reopen the GUI nor show the loading. */
    public final boolean headless;
    public final long startedAt = System.currentTimeMillis();

    public int elapsed;
    public int percent;
    public int lastSoundPercent;
    public boolean persisted;
    public boolean done;
    public boolean previousInvulnerable;
    public PotionEffect previousSlow;
    public PotionEffect previousJump;

    public UpgradeSession(UUID playerId, String playerName, StationData station, PathData path,
                          UpgradeEngine.Outcome outcome, LoadingConfig cfg, boolean headless) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.station = station;
        this.path = path;
        this.outcome = outcome;
        this.cfg = cfg;
        this.headless = headless;
    }
}
