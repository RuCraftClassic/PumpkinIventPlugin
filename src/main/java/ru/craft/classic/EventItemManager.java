package ru.craft.classic;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EventItemManager {

    public static final String SPECIAL_LANTERN = "pumpkin_king_lantern";

    private final PumpkinEventPlugin plugin;
    private final NamespacedKey currencyKey;
    private final NamespacedKey shardKey;
    private final NamespacedKey lanternKey;
    private final NamespacedKey noCurrencyDropKey;

    public EventItemManager(PumpkinEventPlugin plugin) {
        this.plugin = plugin;
        this.currencyKey = new NamespacedKey(plugin, "event_pumpkin");
        this.shardKey = new NamespacedKey(plugin, "crown_shard");
        this.lanternKey = new NamespacedKey(plugin, "pumpkin_king_lantern");
        this.noCurrencyDropKey = new NamespacedKey(plugin, "no_currency_drop");
    }

    public ItemStack createEventPumpkin(int amount) {
        return createTagged("event-items.pumpkin", currencyKey, amount);
    }

    public ItemStack createBossShard(int amount) {
        return createTagged("event-items.crown-shard", shardKey, amount);
    }

    public ItemStack createLantern(int amount) {
        return createTagged("event-items.lantern", lanternKey, amount);
    }

    public ItemStack createSpecial(String id, int amount) {
        if (SPECIAL_LANTERN.equalsIgnoreCase(id)) {
            return createLantern(amount);
        }
        throw new IllegalArgumentException("Unknown special item id: " + id);
    }

    public boolean isEventPumpkin(ItemStack stack) {
        return hasTag(stack, currencyKey);
    }

    public boolean isBossShard(ItemStack stack) {
        return hasTag(stack, shardKey);
    }

    public boolean isLantern(ItemStack stack) {
        return hasTag(stack, lanternKey);
    }

    public NamespacedKey getNoCurrencyDropKey() {
        return noCurrencyDropKey;
    }

    public int countEventPumpkins(Player player) {
        return count(player, currencyKey);
    }

    public int countBossShards(Player player) {
        return count(player, shardKey);
    }

    public boolean takeEventPumpkins(Player player, int amount) {
        return take(player, currencyKey, amount);
    }

    public boolean takeBossShards(Player player, int amount) {
        return take(player, shardKey, amount);
    }

    public void give(Player player, ItemStack stack) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private ItemStack createTagged(String path, NamespacedKey key, int amount) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("Missing config section: " + path);
        }

        Material material = Material.matchMaterial(section.getString("material", "PAPER"));
        if (material == null) material = Material.PAPER;

        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String name = section.getString("name");
        if (name != null) meta.setDisplayName(ItemUtil.color(name));

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(ItemUtil.color(line));
        }
        if (!lore.isEmpty()) meta.setLore(lore);

        int customModelData = section.getInt("custom-model-data", 0);
        if (customModelData > 0) meta.setCustomModelData(customModelData);

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean hasTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte value = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private int count(Player player, NamespacedKey key) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (hasTag(stack, key)) total += stack.getAmount();
        }
        return total;
    }

    private boolean take(Player player, NamespacedKey key, int amount) {
        if (amount <= 0) return true;
        if (count(player, key) < amount) return false;

        int left = amount;
        for (int slot = 0; slot < player.getInventory().getSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!hasTag(stack, key)) continue;

            int take = Math.min(left, stack.getAmount());
            int remain = stack.getAmount() - take;
            if (remain <= 0) player.getInventory().setItem(slot, null);
            else stack.setAmount(remain);
            left -= take;
        }
        player.updateInventory();
        return left == 0;
    }
}
