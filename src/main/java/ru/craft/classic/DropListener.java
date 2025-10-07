package ru.craft.classic;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DropListener implements Listener {

    private final PumpkinEventPlugin plugin;
    private final Random random = new Random();
    private Set<EntityType> blacklist = new HashSet<>();
    private int dropMin, dropMax;
    private double dropChance;

    public DropListener(PumpkinEventPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        FileConfiguration c = plugin.getConfig();
        dropMin = Math.max(1, c.getInt("drop-min", 1));
        dropMax = Math.max(dropMin, c.getInt("drop-max", 3));
        dropChance = c.getDouble("drop-chance", 100.0);

        blacklist.clear();
        List<String> list = c.getStringList("blacklist-mobs");
        for (String s : list) {
            try {
                blacklist.add(EntityType.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.getKiller() == null) {
            return; // только если убил игрок
        }
        if (blacklist.contains(entity.getType())) {
            return;
        }

        double chance = random.nextDouble();
        if (chance > dropChance / 100.0) {
            return;
        }

        int amount = dropMin + random.nextInt(dropMax - dropMin + 1);
        e.getDrops().add(new ItemStack(Material.PUMPKIN, amount));
    }
}