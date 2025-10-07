package ru.craft.classic;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemUtil {

    public static ItemStack fromConfig(Map<String, Object> sec) {
        if (sec == null) throw new IllegalArgumentException("display/give section missing");
        Material mat = Material.valueOf(((String) sec.getOrDefault("material", "STONE")).toUpperCase());
        int amount = (int) sec.getOrDefault("amount", 1);

        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            if (sec.containsKey("name")) meta.setDisplayName(color((String) sec.get("name")));
            if (sec.containsKey("lore")) {
                List<String> raw = (List<String>) sec.get("lore");
                List<String> lore = new ArrayList<>();
                for (String line : raw) lore.add(color(line));
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            is.setItemMeta(meta);
        }

        Object ench = sec.get("enchants");
        if (ench instanceof Map) {
            Reward.applyEnchants(is, (Map<String, Object>) ench);
        }
        return is;
    }

    public static ItemStack withCostLore(ItemStack base, int cost) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add(color("&7Стоимость: &e" + cost + " &7тыкв"));
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}