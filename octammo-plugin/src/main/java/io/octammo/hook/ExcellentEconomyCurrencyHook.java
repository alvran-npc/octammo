package io.octammo.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/** Native multi-currency bridge for ExcellentEconomy. The dependency remains optional. */
public final class ExcellentEconomyCurrencyHook {
    private Object api;
    private Class<?> apiType;

    public void reset() {
        api = null;
        apiType = null;
    }

    public boolean available() {
        if (api != null) return true;
        if (!Bukkit.getPluginManager().isPluginEnabled("ExcellentEconomy")) return false;
        try {
            var economyPlugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
            if (economyPlugin == null) return false;
            // Use the provider plugin's class loader so ServicesManager receives the exact
            // ExcellentEconomyAPI Class key used during service registration.
            apiType = Class.forName("su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI",
                    true, economyPlugin.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) apiType);
            if (registration == null) return false;
            api = registration.getProvider();
            return api != null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    public boolean hasCurrency(String currencyId) {
        if (!available() || currencyId == null || currencyId.isBlank()) return false;
        try {
            Object result = apiType.getMethod("hasCurrency", String.class).invoke(api, currencyId);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public double balance(Player player, String currencyId) {
        if (!hasCurrency(currencyId)) return 0;
        try {
            Object result = apiType.getMethod("getBalance", Player.class, String.class).invoke(api, player, currencyId);
            return result instanceof Number n ? n.doubleValue() : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    public boolean canWithdraw(Player player, String currencyId, double amount) {
        if (amount <= 0) return true;
        if (!hasCurrency(currencyId)) return false;
        try {
            Method allowed = apiType.getMethod("canPerformOperations");
            if (Boolean.FALSE.equals(allowed.invoke(api))) return false;
        } catch (NoSuchMethodException ignored) {
            // Older compatible API builds do not expose the operations-lockdown check.
        } catch (ReflectiveOperationException ex) {
            return false;
        }
        return balance(player, currencyId) >= amount;
    }

    public boolean withdraw(Player player, String currencyId, double amount) {
        if (amount <= 0) return true;
        if (!canWithdraw(player, currencyId, amount)) return false;
        try {
            Object result = apiType.getMethod("withdraw", Player.class, String.class, double.class)
                    .invoke(api, player, currencyId, amount);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
