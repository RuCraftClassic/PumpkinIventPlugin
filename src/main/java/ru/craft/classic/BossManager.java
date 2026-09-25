package ru.craft.classic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BossManager implements Listener {

    private final PumpkinEventPlugin plugin;
    private final EventItemManager items;
    private final Random random = new Random();
    private final org.bukkit.NamespacedKey bossKey;
    private final org.bukkit.NamespacedKey minionKey;
    private final File stateFile;
    private final YamlConfiguration state;

    private ZoneId zoneId;
    private LocalTime spawnTime;
    private boolean enabled;
    private int graceMinutes;
    private int despawnMinutes;
    private double configuredMaxHealth;

    private LivingEntity boss;
    private BossBar bossBar;
    private double bossHealth;
    private double bossMaxHealth;
    private boolean phaseLocked;
    private boolean minionPhaseTriggered;
    private boolean phaseTwoTriggered;
    private boolean enrageTriggered;

    private final Set<UUID> minions = new HashSet<>();
    private final Map<UUID,Double> damage = new HashMap<>();
    private final Set<String> warningsSent = new HashSet<>();

    private BukkitTask scheduleTask;
    private BukkitTask uiTask;
    private BukkitTask timeoutTask;

    private LocalDate lastScheduledSpawnDate;
    private LocalDate lastDefeatDate;

    public BossManager(PumpkinEventPlugin plugin, EventItemManager items) {
        this.plugin = plugin;
        this.items = items;
        this.bossKey = new org.bukkit.NamespacedKey(plugin, "pumpkin_king");
        this.minionKey = new org.bukkit.NamespacedKey(plugin, "pumpkin_king_minion");
        this.stateFile = new File(plugin.getDataFolder(), "boss-state.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
        this.lastScheduledSpawnDate = parseDate(state.getString("last-scheduled-spawn"));
        this.lastDefeatDate = parseDate(state.getString("last-defeat"));
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("boss.enabled", true);
        graceMinutes = Math.max(1, plugin.getConfig().getInt("boss.spawn-grace-minutes", 2));
        despawnMinutes = Math.max(1, plugin.getConfig().getInt("boss.despawn-after-minutes", 20));
        configuredMaxHealth = Math.max(100.0, plugin.getConfig().getDouble("boss.health", 5000.0));
        try { zoneId = ZoneId.of(plugin.getConfig().getString("boss.timezone", "Europe/Moscow")); }
        catch (Exception e) { zoneId = ZoneId.of("Europe/Moscow"); }
        try { spawnTime = LocalTime.parse(plugin.getConfig().getString("boss.spawn-time", "20:00")); }
        catch (Exception e) { spawnTime = LocalTime.of(20,0); }

        if (scheduleTask != null) scheduleTask.cancel();
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSchedule, 20L, 400L);
    }

    public void shutdown() {
        if (scheduleTask != null) scheduleTask.cancel();
        cleanup();
        saveState();
    }

    public boolean isActive() {
        return boss != null && boss.isValid();
    }

    public void setSpawn(Location location) {
        plugin.getConfig().set("boss.arena.world", location.getWorld().getName());
        plugin.getConfig().set("boss.arena.x", location.getX());
        plugin.getConfig().set("boss.arena.y", location.getY());
        plugin.getConfig().set("boss.arena.z", location.getZ());
        plugin.getConfig().set("boss.arena.yaw", location.getYaw());
        plugin.getConfig().set("boss.arena.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public void startManual() {
        if (!startBoss(false)) plugin.getLogger().warning("Could not start Pumpkin King. Configure arena first.");
    }

    public void stopManual() {
        if (!isActive()) return;
        broadcast("&cБой с Тыквенным Королём остановлен администратором.");
        cleanup();
    }

    public List<String> getStatusLines() {
        List<String> lines = new ArrayList<>();
        if (isActive()) {
            lines.add("&cБОЙ ИДЁТ СЕЙЧАС");
            lines.add("&7HP: &c" + Math.max(0, Math.round(bossHealth)) + "&7/&c" + Math.round(bossMaxHealth));
            if (phaseLocked) lines.add("&dУничтожьте прислужников Короля.");
            return lines;
        }

        ZonedDateTime next = nextSpawn();
        long seconds = Math.max(0, Duration.between(ZonedDateTime.now(zoneId), next).getSeconds());
        lines.add("&7Следующий бой: &e" + next.format(DateTimeFormatter.ofPattern("dd.MM HH:mm")) + " МСК");
        lines.add("&7До появления: &f" + formatDuration(seconds));
        if (lastDefeatDate != null && lastDefeatDate.equals(ZonedDateTime.now(zoneId).toLocalDate())) {
            lines.add("&aСегодня Король уже повержен.");
        }
        return lines;
    }

    private void tickSchedule() {
        if (!enabled || isActive()) return;
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime target = now.toLocalDate().atTime(spawnTime).atZone(zoneId);
        long secondsUntil = Duration.between(now, target).getSeconds();

        if (secondsUntil > 0) {
            for (int min : plugin.getConfig().getIntegerList("boss.warning-minutes")) {
                long edge = min * 60L;
                if (secondsUntil <= edge && secondsUntil > edge - 25) {
                    String token = now.toLocalDate() + ":" + min;
                    if (warningsSent.add(token)) {
                        broadcast(plugin.getConfig().getString("boss.messages.warning", "&6🎃 Тыквенный Король пробудится через &e%minutes% &6мин.!").replace("%minutes%", String.valueOf(min)));
                    }
                }
            }
            return;
        }

        long after = -secondsUntil;
        LocalDate today = now.toLocalDate();
        if (after <= graceMinutes * 60L && !today.equals(lastScheduledSpawnDate)) startBoss(true);
    }

    private boolean startBoss(boolean scheduled) {
        if (isActive()) return false;
        Location location = arena();
        if (location == null) return false;

        Entity raw = location.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
        if (!(raw instanceof LivingEntity living)) {
            raw.remove();
            return false;
        }

        boss = living;
        bossHealth = configuredMaxHealth;
        bossMaxHealth = configuredMaxHealth;
        damage.clear();
        minions.clear();
        phaseLocked = false;
        minionPhaseTriggered = false;
        phaseTwoTriggered = false;
        enrageTriggered = false;

        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte)1);
        boss.getPersistentDataContainer().set(items.getNoCurrencyDropKey(), PersistentDataType.BYTE, (byte)1);
        boss.setCustomName(ItemUtil.color(plugin.getConfig().getString("boss.name", "&6&lТыквенный Король")));
        boss.setCustomNameVisible(true);
        if (boss instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setPersistent(true);
        }

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_HOE));
            eq.setHelmetDropChance(0);
            eq.setItemInMainHandDropChance(0);
        }

        bossBar = Bukkit.createBossBar(ItemUtil.color("&6Тыквенный Король"), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        if (scheduled) {
            lastScheduledSpawnDate = ZonedDateTime.now(zoneId).toLocalDate();
            state.set("last-scheduled-spawn", lastScheduledSpawnDate.toString());
            saveState();
        }

        broadcast(plugin.getConfig().getString("boss.messages.spawn", "&6&l🎃 ТЫКВЕННЫЙ КОРОЛЬ ПРОБУДИЛСЯ!"));
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 1f, .8f);

        uiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUi, 20L, 20L);
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isActive()) {
                broadcast(plugin.getConfig().getString("boss.messages.escaped", "&cТыквенный Король скрылся."));
                cleanup();
            }
        }, despawnMinutes * 60L * 20L);
        return true;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (boss == null || !event.getEntity().getUniqueId().equals(boss.getUniqueId())) return;
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;

        Player player = resolvePlayer(byEntity.getDamager());
        if (player == null || phaseLocked) return;

        double dealt = Math.max(0, byEntity.getFinalDamage());
        if (dealt <= 0) return;
        damage.merge(player.getUniqueId(), dealt, Double::sum);
        bossHealth -= dealt;
        boss.playHurtAnimation(0f);

        if (bossHealth <= 0) {
            defeat();
            return;
        }

        double ratio = bossHealth / bossMaxHealth;
        if (!minionPhaseTriggered && ratio <= .70) {
            minionPhaseTriggered = true;
            phaseLocked = true;
            spawnMinions(Math.max(1, plugin.getConfig().getInt("boss.phases.minions.count", 10)));
            broadcast(plugin.getConfig().getString("boss.messages.minions", "&dКороль призвал прислужников!"));
        } else if (!phaseTwoTriggered && ratio <= .40) {
            phaseTwoTriggered = true;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true));
            broadcast(plugin.getConfig().getString("boss.messages.phase-two", "&6Король стал быстрее!"));
        } else if (!enrageTriggered && ratio <= .15) {
            enrageTriggered = true;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, true));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, true));
            broadcast(plugin.getConfig().getString("boss.messages.enrage", "&c&lКороль в ярости!"));
        }
    }

    @EventHandler
    public void onMinionDeath(EntityDeathEvent event) {
        if (!minions.remove(event.getEntity().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (phaseLocked && minions.isEmpty()) {
            phaseLocked = false;
            broadcast("&aВсе прислужники повержены — атакуйте Короля!");
        }
    }

    private void tickUi() {
        if (!isActive()) {
            cleanup();
            return;
        }
        minions.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
        if (phaseLocked && minions.isEmpty()) phaseLocked = false;

        if (bossBar != null) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            });
            bossBar.setProgress(Math.max(0, Math.min(1, bossHealth / bossMaxHealth)));
            bossBar.setTitle(ItemUtil.color("&6Тыквенный Король &8— &c" + Math.round(bossHealth) + " HP"));
        }
    }

    private void spawnMinions(int count) {
        if (boss == null) return;
        List<String> types = plugin.getConfig().getStringList("boss.phases.minions.types");
        if (types.isEmpty()) types = List.of("ZOMBIE","SKELETON");

        for (int i = 0; i < count; i++) {
            EntityType type;
            try { type = EntityType.valueOf(types.get(i % types.size()).toUpperCase(Locale.ROOT)); }
            catch (Exception e) { type = EntityType.ZOMBIE; }

            Location at = boss.getLocation().clone().add((random.nextDouble()-.5)*8, .5, (random.nextDouble()-.5)*8);
            Entity spawned = boss.getWorld().spawnEntity(at, type);
            if (!(spawned instanceof Monster monster)) {
                spawned.remove();
                continue;
            }

            monster.setCustomName(ItemUtil.color("&6Слуга Тыквенного Короля"));
            monster.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte)1);
            monster.getPersistentDataContainer().set(items.getNoCurrencyDropKey(), PersistentDataType.BYTE, (byte)1);
            monster.setRemoveWhenFarAway(false);
            monster.setPersistent(true);
            if (monster.getEquipment() != null) {
                monster.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                monster.getEquipment().setHelmetDropChance(0);
            }
            minions.add(monster.getUniqueId());
        }
    }

    private void defeat() {
        rewardPlayers();
        lastDefeatDate = ZonedDateTime.now(zoneId).toLocalDate();
        state.set("last-defeat", lastDefeatDate.toString());
        saveState();
        broadcast(plugin.getConfig().getString("boss.messages.defeat", "&a&l🎃 Тыквенный Король повержен!"));
        if (boss != null) boss.getWorld().playSound(boss.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, .9f);
        cleanup();
    }

    private void rewardPlayers() {
        double minDamage = bossMaxHealth * (plugin.getConfig().getDouble("boss.rewards.minimum-damage-percent", 2.0) / 100.0);
        List<Map.Entry<UUID,Double>> eligible = damage.entrySet().stream()
                .filter(e -> e.getValue() >= minDamage)
                .sorted(Map.Entry.<UUID,Double>comparingByValue().reversed())
                .toList();

        int pumpkins = Math.max(0, plugin.getConfig().getInt("boss.rewards.participation.event-pumpkins", 32));
        int shards = Math.max(0, plugin.getConfig().getInt("boss.rewards.participation.shards", 1));
        List<String> baseCommands = plugin.getConfig().getStringList("boss.rewards.participation.commands");

        for (Map.Entry<UUID,Double> entry : eligible) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(entry.getKey());
            String name = off.getName();
            if (name == null) continue;
            runCommands(baseCommands, name, entry.getValue());

            Player online = off.getPlayer();
            if (online != null) {
                if (pumpkins > 0) items.give(online, items.createEventPumpkin(pumpkins));
                if (shards > 0) items.give(online, items.createBossShard(shards));
                online.sendMessage(ItemUtil.color("&aНаграда: &6" + pumpkins + " 🎃 &d+ " + shards + " Осколок Короны"));
            }
        }

        for (int i = 0; i < Math.min(3, eligible.size()); i++) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(eligible.get(i).getKey());
            if (off.getName() != null) {
                runCommands(plugin.getConfig().getStringList("boss.rewards.top-damage." + (i+1) + ".commands"), off.getName(), eligible.get(i).getValue());
            }
        }
    }

    private void runCommands(List<String> commands, String player, double dealt) {
        for (String raw : commands) {
            String command = raw.replace("%player%", player).replace("%damage%", String.valueOf(Math.round(dealt)));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile p && p.getShooter() instanceof Player player) return player;
        return null;
    }

    private Location arena() {
        String worldName = plugin.getConfig().getString("boss.arena.world", "");
        if (worldName == null || worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                plugin.getConfig().getDouble("boss.arena.x"),
                plugin.getConfig().getDouble("boss.arena.y"),
                plugin.getConfig().getDouble("boss.arena.z"),
                (float)plugin.getConfig().getDouble("boss.arena.yaw"),
                (float)plugin.getConfig().getDouble("boss.arena.pitch"));
    }

    private ZonedDateTime nextSpawn() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime today = now.toLocalDate().atTime(spawnTime).atZone(zoneId);
        if (now.isBefore(today) && !now.toLocalDate().equals(lastScheduledSpawnDate)) return today;
        return now.toLocalDate().plusDays(1).atTime(spawnTime).atZone(zoneId);
    }

    private void cleanup() {
        if (timeoutTask != null) { timeoutTask.cancel(); timeoutTask = null; }
        if (uiTask != null) { uiTask.cancel(); uiTask = null; }
        for (UUID id : new HashSet<>(minions)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        minions.clear();
        if (boss != null) { boss.remove(); boss = null; }
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        damage.clear();
        phaseLocked = false;
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    private void broadcast(String text) {
        Bukkit.broadcastMessage(ItemUtil.color(text));
    }

    private LocalDate parseDate(String raw) {
        try { return raw == null ? null : LocalDate.parse(raw); }
        catch (Exception e) { return null; }
    }

    private void saveState() {
        try { state.save(stateFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save boss state: " + e.getMessage()); }
    }
}
