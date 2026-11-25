package io.octammo;

import io.octammo.api.OctaMMO;
import io.octammo.api.OctaMMOApi;
import io.octammo.command.ItemUpgradeCommand;
import io.octammo.command.OctaCommand;
import io.octammo.core.AuditLog;
import io.octammo.core.ConfigMigrator;
import io.octammo.core.Lang;
import io.octammo.core.MmoItems;
import io.octammo.core.PlayerStats;
import io.octammo.gui.GuiListener;
import io.octammo.gui.GuiManager;
import io.octammo.gui.OctaGui;
import io.octammo.enchant.EnchantingTableListener;
import io.octammo.enchant.EnchantingSearchListener;
import io.octammo.hook.ExcellentEconomyCurrencyHook;
import io.octammo.recipe.RecipeManager;
import io.octammo.hook.EconomyHook;
import io.octammo.hook.FloodgateHook;
import io.octammo.hook.PapiHook;
import io.octammo.hook.WorldGuardHook;
import io.octammo.interact.InteractLockListener;
import io.octammo.upgrade.CostService;
import io.octammo.upgrade.ExtensionRegistry;
import io.octammo.upgrade.LoadingLockListener;
import io.octammo.upgrade.LoadingManager;
import io.octammo.upgrade.PendingRewards;
import io.octammo.upgrade.UpgradeManager;
import io.octammo.upgrade.UpgradeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OctaMMOPlugin extends JavaPlugin {
    private static OctaMMOPlugin instance;

    private Lang lang;
    private PlayerStats stats;
    private UpgradeManager upgrades;
    private UpgradeService service;
    private LoadingManager loading;
    private CostService costService;
    private ExtensionRegistry registry;
    private GuiManager guis;
    private PendingRewards pending;
    private AuditLog upgradeLog;
    private ExecutorService io;
    private OctaMMOApi api;
    private InteractLockListener interactLock;
    private boolean mmoItems;
    private ExcellentEconomyCurrencyHook excellentEconomy;
    private RecipeManager recipes;
    private EnchantingSearchListener enchantingSearch;

    // optional hooks, null when the plugin is not installed
    private EconomyHook economy;
    private WorldGuardHook worldGuard;
    private FloodgateHook floodgate;
    private PapiHook papi;

    public static OctaMMOPlugin getInstance() { return instance; }

    @Override
    public void onLoad() {
        // the WorldGuard flag must be registered before WorldGuard enables
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) WorldGuardHook.registerFlag(getLogger());
    }

    @Override
    public void onEnable() {
        instance = this;
        PluginManager pm = Bukkit.getPluginManager();

        ConfigMigrator.migrate(this, new File(getDataFolder(), "config.yml"), "config.yml");
        reloadConfig();

        mmoItems = pm.isPluginEnabled("MMOItems") && pm.isPluginEnabled("MythicLib");
        if (!mmoItems) {
            getLogger().warning("MMOItems / MythicLib not found: Item Upgrade is disabled. Install them and restart.");
        }

        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OctaMMO-IO");
            t.setDaemon(true);
            return t;
        });
        registry = new ExtensionRegistry();
        lang = new Lang(this);
        stats = new PlayerStats(this);
        guis = new GuiManager(this);
        guis.saveDefaults();
        pending = new PendingRewards(this, io);
        pending.load();
        upgradeLog = new AuditLog(this, io, "upgrades.log", 2L * 1024 * 1024);
        costService = new CostService(this);
        loading = new LoadingManager(this);
        service = new UpgradeService(this);
        upgrades = new UpgradeManager(this);

        setupHooks();
        excellentEconomy = new ExcellentEconomyCurrencyHook();
        recipes = new RecipeManager(this);
        recipes.saveDefaults();
        recipes.reload();
        lang.load();
        if (mmoItems) upgrades.load();

        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new EnchantingTableListener(this), this);
        enchantingSearch = new EnchantingSearchListener(this);
        pm.registerEvents(enchantingSearch, this);
        pm.registerEvents(new LoadingLockListener(this), this);
        if (mmoItems) {
            interactLock = new InteractLockListener(this);
            pm.registerEvents(interactLock, this);
        }

        PluginCommand octa = getCommand("octammo");
        if (octa != null) {
            OctaCommand handler = new OctaCommand(this);
            octa.setExecutor(handler);
            octa.setTabCompleter(handler);
        }
        PluginCommand upgrade = getCommand("itemupgrade");
        if (upgrade != null) {
            ItemUpgradeCommand handler = new ItemUpgradeCommand(this);
            upgrade.setExecutor(handler);
            upgrade.setTabCompleter(handler);
        }

        api = new io.octammo.upgrade.ApiImpl(this);
        OctaMMO.setApi(api);
        getServer().getServicesManager().register(OctaMMOApi.class, api, this, ServicePriority.Normal);
        printStartupBanner();
        getLogger().info("Enabled. Stations: " + upgrades.stationCount() + ", Public API v" + OctaMMOApi.API_VERSION);
    }

    @Override
    public void onDisable() {
        OctaMMO.setApi(null);
        // 1) finish running upgrades at once so no result is lost, 2) hand back items from open GUIs
        if (loading != null) loading.finishAll();
        if (upgrades != null) upgrades.closeAll();
        // Clear active search prompts before closing inventories so their input items
        // are returned by each GUI's normal close handler during plugin shutdown.
        if (enchantingSearch != null) enchantingSearch.clear();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof OctaGui) {
                player.closeInventory();
            }
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (io != null) {
            io.shutdown();   // lets queued writes / deletes finish
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) getLogger().warning("Pending file writes did not finish in 10s.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        instance = null;
    }

    private void printStartupBanner() {
        if (!getConfig().getBoolean("startup-banner.enabled", true)) return;
        getLogger().info("");
        for (String line : getConfig().getStringList("startup-banner.logo")) {
            // Translate configurable legacy color codes while keeping the normal
            // timestamp / INFO / [OctaMMO] logger prefix for every logo row.
            getLogger().info(ChatColor.translateAlternateColorCodes('&', line));
        }
        String version = getPluginMeta().getVersion();
        getLogger().info("  OCTAMMO v" + version);
        getLogger().info("  Server Version: " + Bukkit.getVersion());
        getLogger().info("");
        if (getConfig().getBoolean("startup-banner.hook-summary", true)) {
            PluginManager pm = Bukkit.getPluginManager();
            getLogger().info("Hook: MMOItems=" + pm.isPluginEnabled("MMOItems")
                    + " MythicLib=" + pm.isPluginEnabled("MythicLib")
                    + " ExcellentEconomy=" + (excellentEconomy != null && excellentEconomy.available())
                    + " Vault=" + (economy != null && economy.available() ? "ok" : "missing")
                    + " PlaceholderAPI=" + (papi != null ? "ok" : "missing")
                    + " WorldGuard=" + (worldGuard != null ? "ok" : "missing")
                    + " Floodgate=" + (floodgate != null ? "ok" : "missing"));
        }
        getLogger().info("");
    }

    private void setupHooks() {
        PluginManager pm = Bukkit.getPluginManager();
        if (pm.isPluginEnabled("Vault")) {
            economy = new EconomyHook();
            if (!economy.available()) getLogger().warning("Vault found but no economy provider is registered yet.");
        }
        worldGuard = pm.isPluginEnabled("WorldGuard") ? new WorldGuardHook() : null;
        floodgate = pm.isPluginEnabled("Floodgate") ? new FloodgateHook() : null;
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            papi = new PapiHook(this);
            papi.register();
        }
    }

    /** Reloads config, lang, guis and upgrades/*.yml. */
    public void reloadAll() {
        ConfigMigrator.migrate(this, new File(getDataFolder(), "config.yml"), "config.yml");
        reloadConfig();
        if (mmoItems) MmoItems.clearCache();
        if (economy != null) economy.reset();
        lang.load();
        guis.reload();
        if (excellentEconomy != null) excellentEconomy.reset();
        if (recipes != null) recipes.reload();
        if (mmoItems) {
            upgrades.load();
        }
    }

    public boolean mmoItems() { return mmoItems; }
    public ExcellentEconomyCurrencyHook excellentEconomy() { return excellentEconomy; }
    public RecipeManager recipes() { return recipes; }
    public EnchantingSearchListener enchantingSearch() { return enchantingSearch; }
    public InteractLockListener interactLock() { return interactLock; }
    public Lang lang() { return lang; }
    public PlayerStats stats() { return stats; }
    public UpgradeManager upgrades() { return upgrades; }
    public UpgradeService service() { return service; }
    public LoadingManager loading() { return loading; }
    public CostService costService() { return costService; }
    public ExtensionRegistry registry() { return registry; }
    public GuiManager guis() { return guis; }
    public PendingRewards pending() { return pending; }
    public AuditLog upgradeLog() { return upgradeLog; }
    public OctaMMOApi api() { return api; }
    public EconomyHook economy() { return economy; }
    public boolean economyAvailable() { return economy != null && economy.available(); }
    public WorldGuardHook worldGuard() { return worldGuard; }
    public FloodgateHook floodgate() { return floodgate; }
    public PapiHook papi() { return papi; }
}
