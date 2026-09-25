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

        Material mat = Material.matchMaterial(String.valueOf(sec.getOrDefault("material", "STONE")));
        if (mat == null) throw new IllegalArgumentException("Unknown material: " + sec.get("material"));

        int amount = asInt(sec.get("amount"), 1);
        ItemStack is = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            if (sec.containsKey("name")) meta.setDisplayName(color(String.valueOf(sec.get("name"))));
            if (sec.containsKey("lore") && sec.get("lore") instanceof List<?> raw) {
                List<String> lore = new ArrayList<>();
                for (Object line : raw) lore.add(color(String.valueOf(line)));
                meta.setLore(lore);
            }
            if (sec.containsKey("custom-model-data")) {
                int customModelData = asInt(sec.get("custom-model-data"), 0);
                if (customModelData > 0) meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            is.setItemMeta(meta);
        }

        Object ench = sec.get("enchants");
        if (ench instanceof Map<?, ?> rawEnchants) {
            @SuppressWarnings("unchecked")
            Map<String, Object> enchants = (Map<String, Object>) rawEnchants;
            Reward.applyEnchants(is, enchants);
        }
        return is;
    }

    public static ItemStack withCostLore(ItemStack base, int pumpkinCost, int shardCost) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            if (pumpkinCost > 0) lore.add(color("&7Стоимость: &6" + pumpkinCost + " 🎃"));
            if (shardCost > 0) lore.add(color("&7Осколки Короны: &d" + shardCost + " 👑"));
            lore.add(color("&eНажмите, чтобы получить"));
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static int asInt(Object value, int def) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return def;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
