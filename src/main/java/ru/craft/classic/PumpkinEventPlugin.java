package ru.craft.classic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PumpkinEventPlugin extends JavaPlugin {

    private ShopManager shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.shop = new ShopManager(this);
        Bukkit.getPluginManager().registerEvents(new DropListener(this), this);
        getLogger().info("PumpkinEventPlugin запущен!");
    }

    @Override
    public void onDisable() {
        if (shop != null) shop.closeAll();
    }

    public ShopManager getShop() {
        return shop;
    }

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            shop.open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!p.hasPermission("pumpkin.reload")) {
                p.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            reloadConfig();
            shop.reload();
            p.sendMessage(ChatColor.GREEN + "Конфиг перезагружен.");
            return true;
        }
        p.sendMessage(ChatColor.YELLOW + "Использование: /" + label + " [shop|reload]");
        return true;
    }
}