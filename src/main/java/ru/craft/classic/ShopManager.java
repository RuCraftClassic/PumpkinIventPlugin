package ru.craft.classic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopManager implements Listener {

    private static final int[] REWARD_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final PumpkinEventPlugin plugin;
    private final EventItemManager items;
    private final List<Reward> rewards = new ArrayList<>();
    private String title;
    private boolean effects;

    public ShopManager(PumpkinEventPlugin plugin, EventItemManager items) {
        this.plugin = plugin;
        this.items = items;
        reload();
    }

    @SuppressWarnings("unchecked")
    public void reload() {
        rewards.clear();
        title = plugin.color(plugin.getConfig().getString("shop.title", "&6🎃 Хэллоуинская лавка"));
        effects = plugin.getConfig().getBoolean("shop.effects", true);

        for (Map<?, ?> rawAny : plugin.getConfig().getMapList("rewards")) {
            try {
                Map<String,Object> raw = (Map<String,Object>)(Map<?,?>) rawAny;
                int logicalSlot = asInt(raw.get("slot"), 0);
                if (logicalSlot < 0 || logicalSlot >= REWARD_SLOTS.length) continue;

                Map<String,Object> displaySec = (Map<String,Object>) raw.get("display");
                ItemStack display = ItemUtil.fromConfig(displaySec);
                int pumpkinCost = asInt(raw.get("cost-pumpkins"), 0);
                int shardCost = asInt(raw.get("cost-shards"), 0);

                Map<String,Object> giveSec = (Map<String,Object>) raw.get("give");
                if (giveSec == null) throw new IllegalArgumentException("give section missing");

                ItemStack toGive;
                String specialId = giveSec.get("special-id") == null ? null : giveSec.get("special-id").toString();
                int amount = asInt(giveSec.get("amount"), 1);
                if (specialId != null && !specialId.isBlank()) toGive = items.createSpecial(specialId, amount);
                else toGive = ItemUtil.fromConfig(giveSec);

                rewards.add(new Reward(logicalSlot, ItemUtil.withCostLore(display, pumpkinCost, shardCost), toGive, pumpkinCost, shardCost));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load reward: " + ex.getMessage());
            }
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 54, title);
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        inv.setItem(4, playerInfo(player));
        inv.setItem(49, bossInfo());
        inv.setItem(53, named(Material.BARRIER, "&cЗакрыть", List.of("&7Закрыть лавку")));

        for (Reward reward : rewards) inv.setItem(REWARD_SLOTS[reward.slot], reward.display);
        player.openInventory(inv);
    }

    public void closeAll() {
        for (HumanEntity viewer : Bukkit.getOnlinePlayers()) {
            if (title.equals(viewer.getOpenInventory().getTitle())) viewer.closeInventory();
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!title.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getRawSlot() == 53) {
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            return;
        }

        Reward target = null;
        for (Reward reward : rewards) {
            if (REWARD_SLOTS[reward.slot] == event.getRawSlot()) {
                target = reward;
                break;
            }
        }
        if (target == null) return;

        if (items.countEventPumpkins(player) < target.pumpkinCost) {
            player.sendMessage(ItemUtil.color("&cНедостаточно Хэллоуинских тыкв. Нужно: &6" + target.pumpkinCost));
            return;
        }
        if (items.countBossShards(player) < target.shardCost) {
            player.sendMessage(ItemUtil.color("&cНедостаточно Осколков Короны. Нужно: &d" + target.shardCost));
            return;
        }

        if (!items.takeEventPumpkins(player, target.pumpkinCost)) return;
        if (!items.takeBossShards(player, target.shardCost)) {
            if (target.pumpkinCost > 0) items.give(player, items.createEventPumpkin(target.pumpkinCost));
            return;
        }

        items.give(player, target.toGive.clone());
        if (effects) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.sendMessage(ItemUtil.color("&aНаграда получена!"));
        }
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (title.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private ItemStack playerInfo(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ItemUtil.color("&6" + player.getName()));
            meta.setLore(List.of(
                    ItemUtil.color("&7Хэллоуинские тыквы: &6" + items.countEventPumpkins(player) + " 🎃"),
                    ItemUtil.color("&7Осколки Короны: &d" + items.countBossShards(player) + " 👑"),
                    "",
                    ItemUtil.color("&8Обычные ванильные тыквы не принимаются.")
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack bossInfo() {
        List<String> lore = new ArrayList<>();
        lore.add("&7Ежедневный мировой босс");
        lore.add("");
        lore.addAll(plugin.getBossManager().getStatusLines());
        lore.add("");
        lore.add("&8Осколки Короны выдаются за победу.");
        return named(Material.WITHER_SKELETON_SKULL, "&6&lТыквенный Король", lore);
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ItemUtil.color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) colored.add(ItemUtil.color(line));
            meta.setLore(colored);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int asInt(Object value, int def) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return def;
        try { return Integer.parseInt(value.toString()); }
        catch (Exception ignored) { return def; }
    }
}
