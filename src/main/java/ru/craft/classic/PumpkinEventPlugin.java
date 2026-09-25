package ru.craft.classic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PumpkinEventPlugin extends JavaPlugin {

    private EventItemManager items;
    private DropListener dropListener;
    private ShopManager shop;
    private BossManager bossManager;
    private LanternListener lanternListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        items = new EventItemManager(this);
        dropListener = new DropListener(this, items);
        bossManager = new BossManager(this, items);
        shop = new ShopManager(this, items);
        lanternListener = new LanternListener(this, items);

        Bukkit.getPluginManager().registerEvents(dropListener, this);
        Bukkit.getPluginManager().registerEvents(bossManager, this);
        Bukkit.getPluginManager().registerEvents(shop, this);
        Bukkit.getPluginManager().registerEvents(lanternListener, this);
        getLogger().info("PumpkinEventPlugin 2.0 enabled");
    }

    @Override
    public void onDisable() {
        if (lanternListener != null) lanternListener.shutdown();
        if (bossManager != null) bossManager.shutdown();
        if (shop != null) {
            shop.closeAll();
            shop.unregister();
        }
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the shop.");
                return true;
            }
            shop.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("pumpkin.reload")) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            shop.closeAll();
            reloadConfig();
            dropListener.reload();
            bossManager.reload();
            shop.reload();
            lanternListener.reload();
            sender.sendMessage(ChatColor.GREEN + "PumpkinEventPlugin перезагружен.");
            return true;
        }

        if (args[0].equalsIgnoreCase("boss")) {
            if (args.length == 1) {
                sender.sendMessage(color("&6&lТыквенный Король"));
                for (String line : bossManager.getStatusLines()) sender.sendMessage(color(line));
                return true;
            }

            String sub = args[1].toLowerCase();
            if (sub.equals("start")) {
                if (!sender.hasPermission("pumpkin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                bossManager.startManual();
                sender.sendMessage(color("&aПопытка запуска Тыквенного Короля выполнена."));
                return true;
            }

            if (sub.equals("stop")) {
                if (!sender.hasPermission("pumpkin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                bossManager.stopManual();
                sender.sendMessage(color("&eБой остановлен."));
                return true;
            }

            if (sub.equals("setspawn")) {
                if (!sender.hasPermission("pumpkin.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Эту команду нужно выполнить игроком на арене.");
                    return true;
                }
                bossManager.setSpawn(player.getLocation());
                sender.sendMessage(color("&aТочка появления Тыквенного Короля сохранена."));
                return true;
            }
        }

        sender.sendMessage(color("&eИспользование: /" + label + " [shop|boss|reload]"));
        return true;
    }
}
