package ru.craft.classic;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LanternListener implements Listener {

    private final PumpkinEventPlugin plugin;
    private final EventItemManager items;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private BukkitTask passiveTask;

    public LanternListener(PumpkinEventPlugin plugin, EventItemManager items) {
        this.plugin = plugin;
        this.items = items;
        reload();
    }

    public void reload() {
        if (passiveTask != null) passiveTask.cancel();
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyPassive, 20L, 40L);
    }

    public void shutdown() {
        if (passiveTask != null) passiveTask.cancel();
    }

    private void applyPassive() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (!items.isLantern(offhand)) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, true, false, false));
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!items.isLantern(event.getItem())) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long until = cooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) {
            long seconds = Math.max(1L, (until - now + 999L) / 1000L);
            player.sendMessage(ItemUtil.color("&7Свет мёртвых восстановится через &e" + seconds + " сек."));
            return;
        }

        int cooldown = Math.max(1, plugin.getConfig().getInt("lantern.ability.cooldown-seconds", 60));
        double radius = Math.max(1.0, plugin.getConfig().getDouble("lantern.ability.radius", 12.0));
        int durationSeconds = Math.max(1, plugin.getConfig().getInt("lantern.ability.duration-seconds", 8));
        int ticks = durationSeconds * 20;
        cooldownUntil.put(player.getUniqueId(), now + cooldown * 1000L);

        int affected = 0;
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Monster monster)) continue;
            monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, true));
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, false, true));
            affected++;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 0, false, true));
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1.0, 0), 60, radius / 3.0, 1.0, radius / 3.0, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SOUL_LANTERN_PLACE, 1.0f, 0.7f);
        player.sendMessage(ItemUtil.color("&6✦ Свет мёртвых &7подсветил монстров: &f" + affected));
    }
}
