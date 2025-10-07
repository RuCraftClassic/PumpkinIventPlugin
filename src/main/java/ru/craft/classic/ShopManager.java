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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ShopManager implements Listener {

    private final PumpkinEventPlugin plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private String title;
    private boolean effects;

    public ShopManager(PumpkinEventPlugin plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @SuppressWarnings("unchecked")
    public void reload() {
        rewards.clear();
        title = plugin.color(plugin.getConfig().getString("shop-title", "&6Тыквенный Магазин"));
        effects = plugin.getConfig().getBoolean("effects", true);

        List<Map<?, ?>> list = plugin.getConfig().getMapList("rewards");
        for (Map<?, ?> rawAny : list) {
            try {
                Map<String, Object> raw = (Map<String, Object>) (Map<?, ?>) rawAny;

                int slot = asInt(raw.get("slot"), 0);

                Map<String, Object> dispSec = (Map<String, Object>) raw.get("display");
                ItemStack display = ItemUtil.fromConfig(dispSec);

                int cost = asInt(raw.get("cost-pumpkins"), 1);

                Map<String, Object> giveSec = (Map<String, Object>) raw.get("give");
                ItemStack toGive = ItemUtil.fromConfig(giveSec);

                rewards.add(new Reward(slot, ItemUtil.withCostLore(display, cost), toGive, cost));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load reward: " + ex.getMessage());
            }
        }
    }

    /** Безопасно приводит к int (подходит для Integer, Long, String). */
    private int asInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        }
        catch (Exception ignored) {
            return def;
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, title);
        for (Reward r : rewards) {
            if (r.slot >= 0 && r.slot < inv.getSize()) {
                inv.setItem(r.slot, r.display);
            }
        }
        player.openInventory(inv);
    }

    public void closeAll() {
        for (HumanEntity v : Bukkit.getOnlinePlayers()) {
            if (v.getOpenInventory() != null && title.equals(v.getOpenInventory().getTitle())) {
                v.closeInventory();
            }
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null || !e.getView().getTitle().equals(title)) return;
        if (e.getClickedInventory() == null) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Найти награду по слоту
        Reward target = null;
        for (Reward r : rewards) {
            if (r.slot == e.getRawSlot()) { target = r; break; }
        }
        if (target == null) return;

        int pumpkins = count(p, Material.PUMPKIN);
        if (pumpkins < target.cost) {
            p.sendMessage("§cНедостаточно тыкв. Нужно: " + target.cost);
            return;
        }
        remove(p, Material.PUMPKIN, target.cost);

        // Выдать награду
        HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(target.toGive.clone());
        if (!overflow.isEmpty()) {
            // Если не влезло — дропнуть рядом
            overflow.values().forEach(is -> p.getWorld().dropItemNaturally(p.getLocation(), is));
        }

        if (effects) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            p.sendMessage("§aВы получили награду!");
        }
    }

    private int count(Player p, Material mat) {
        int sum = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == mat) sum += is.getAmount();
        }
        return sum;
    }

    private void remove(Player p, Material mat, int amount) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack is = p.getInventory().getItem(i);
            if (is == null || is.getType() != mat) continue;
            int take = Math.min(amount, is.getAmount());
            is.setAmount(is.getAmount() - take);
            if (is.getAmount() <= 0) p.getInventory().setItem(i, null);
            amount -= take;
            if (amount <= 0) break;
        }
        p.updateInventory();
    }
}