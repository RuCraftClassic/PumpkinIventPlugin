package ru.craft.classic;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class Reward {
    public final int slot;
    public final ItemStack display;
    public final ItemStack toGive;
    public final int cost;

    public Reward(int slot, ItemStack display, ItemStack toGive, int cost) {
        this.slot = slot;
        this.display = display;
        this.toGive = toGive;
        this.cost = cost;
    }

    @SuppressWarnings("deprecation")
    public static void applyEnchants(ItemStack stack, Map<String, Object> ench) {
        if (ench == null) return;
        for (Map.Entry<String, Object> e : ench.entrySet()) {
            try {
                Enchantment en = Enchantment.getByName(e.getKey());
                if (en != null) {
                    int lvl = Integer.parseInt(e.getValue().toString());
                    stack.addUnsafeEnchantment(en, lvl);
                }
            } catch (Exception ignored) {}
        }
    }
}