package io.octammo.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault economy. Excellent Economy registers itself as a Vault provider, so it works through here. */
public final class EconomyHook {
    private Economy economy;

    public boolean available() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy != null;
    }

    public void reset() {
        economy = null;
    }

    public boolean has(Player p, double amount) {
        return available() && economy.has(p, amount);
    }

    public boolean withdraw(Player p, double amount) {
        return available() && economy.withdrawPlayer(p, amount).transactionSuccess();
    }

    public void deposit(Player p, double amount) {
        if (available() && amount > 0) economy.depositPlayer(p, amount);
    }

    public String format(double amount) {
        return available() ? economy.format(amount) : String.valueOf(amount);
    }
}
