package io.octammo.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class WorldGuardHook {
    private static StateFlag flag;

    /** Registers the octammo-upgrade flag. Must run in onLoad (before WorldGuard enables). */
    public static void registerFlag(Logger log) {
        try {
            StateFlag f = new StateFlag("octammo-upgrade", true);
            WorldGuard.getInstance().getFlagRegistry().register(f);
            flag = f;
        } catch (Throwable t) {
            log.warning("Could not register the WorldGuard flag octammo-upgrade: " + t.getMessage());
        }
    }

    /** Lowercase ids of the regions at a location. */
    public Set<String> regionsAt(Location loc) {
        Set<String> ids = new HashSet<>();
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));
        for (ProtectedRegion r : set.getRegions()) ids.add(r.getId().toLowerCase(Locale.ROOT));
        return ids;
    }

    /** The octammo-upgrade flag (allow by default). */
    public boolean flagAllows(Player player) {
        if (flag == null) return true;
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            LocalPlayer wrapped = WorldGuardPlugin.inst().wrapPlayer(player);
            return query.testState(BukkitAdapter.adapt(player.getLocation()), wrapped, flag);
        } catch (Throwable t) {
            return true;
        }
    }
}
