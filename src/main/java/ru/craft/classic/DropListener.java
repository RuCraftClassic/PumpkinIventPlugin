package ru.craft.classic;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DropListener implements Listener {

    private final PumpkinEventPlugin plugin;
    private final EventItemManager items;
    private final Random random = new Random();
    private final NamespacedKey spawnerKey;
    private final Set<EntityType> blacklist = new HashSet<>();
    private final Map<EntityType, Double> multipliers = new HashMap<>();

    private int dropMin;
    private int dropMax;
    private double dropChance;
    private boolean hostileOnly;
    private boolean allowSpawnerDrops;

    public DropListener(PumpkinEventPlugin plugin, EventItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.spawnerKey = new NamespacedKey(plugin, "spawned_from_spawner");
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        dropMin = Math.max(1, c.getInt("drops.amount-min", 1));
        dropMax = Math.max(dropMin, c.getInt("drops.amount-max", 3));
        dropChance = Math.max(0.0, Math.min(100.0, c.getDouble("drops.chance", 50.0)));
        hostileOnly = c.getBoolean("drops.hostile-only", true);
        allowSpawnerDrops = c.getBoolean("drops.allow-spawner-mobs", false);

        blacklist.clear();
        List<String> list = c.getStringList("drops.blacklist-mobs");
        for (String s : list) {
            try {
                blacklist.add(EntityType.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown mob in drops.blacklist-mobs: " + s);
            }
        }

        multipliers.clear();
        ConfigurationSection section = c.getConfigurationSection("drops.chance-multipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    multipliers.put(EntityType.valueOf(key.toUpperCase()), section.getDouble(key, 1.0));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown mob in drops.chance-multipliers: " + key);
                }
            }
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().getPersistentDataContainer().set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() == null) return;
        if (blacklist.contains(entity.getType())) return;
        if (hostileOnly && !(entity instanceof Monster)) return;
        if (entity.getPersistentDataContainer().has(items.getNoCurrencyDropKey(), PersistentDataType.BYTE)) return;
        if (!allowSpawnerDrops && entity.getPersistentDataContainer().has(spawnerKey, PersistentDataType.BYTE)) return;

        double multiplier = Math.max(0.0, multipliers.getOrDefault(entity.getType(), 1.0));
        double effectiveChance = Math.min(100.0, dropChance * multiplier);
        if (random.nextDouble() * 100.0 >= effectiveChance) return;

        int amount = dropMin + random.nextInt(dropMax - dropMin + 1);
        event.getDrops().add(items.createEventPumpkin(amount));
    }
}
