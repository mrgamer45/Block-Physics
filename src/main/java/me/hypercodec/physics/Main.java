package me.hypercodec.physics;

import com.jeff_media.customblockdata.CustomBlockData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Snowable;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class Main extends JavaPlugin {
    public static Main plugin;

    public static Set<Material> stableblocks = new HashSet<>();
    public static Set<Material> unstableblocks = new HashSet<>();

    public static Map<UUID, Integer> iterations = new HashMap<>();

    public static NamespacedKey ignorephysicskey;
    public static NamespacedKey eventidkey;
    public static NamespacedKey explodedkey;
    public static NamespacedKey explosionparticleskey;

    public static FileConfiguration config;
    public static Logger logger;

    double version = 1.8;

    @Override
    public void onDisable() {
        this.getLogger().info("Block Physics plugin unloaded");
    }

    @Override
    public void onEnable() {
        plugin = this;
        config = this.getConfig();
        logger = this.getLogger();

        this.getServer().getPluginManager().registerEvents(new BlockPhysicsListener(), this);

        final PluginCommand ep = this.getCommand("explosionparticles");
        ep.setExecutor(new ExplosionParticles());
        ep.setTabCompleter(new ExplosionParticles());

        this.saveDefaultConfig();

        ignorephysicskey = new NamespacedKey(plugin, "ignorephysics");
        eventidkey = new NamespacedKey(plugin, "eventid");
        explodedkey = new NamespacedKey(plugin, "exploded");
        explosionparticleskey = new NamespacedKey(plugin, "explosionparticles");

        boolean failedtoload = false;

        for(String val : config.getStringList("stableblocks")) {
            try {
                stableblocks.add(Material.valueOf(val));
            } catch (IllegalArgumentException e) {
                logger.warning("\"" + val + "\" is not a valid block material");
                failedtoload = true;
            }
        }

        for(String val : config.getStringList("unstableblocks")) {
            try {
                if(stableblocks.contains(Material.valueOf(val))) {
                    logger.warning("\"" + val + "\" is defined as both stable and unstable");
                    failedtoload = true;
                    continue;
                }

                unstableblocks.add(Material.valueOf(val));
            } catch (IllegalArgumentException e) {
                logger.warning("\"" + val + "\" is not a valid block material");
                failedtoload = true;
            }
        }

        if(failedtoload) {
            logger.severe("1 or more materials were invalid, disabling...");

            this.getServer().getPluginManager().disablePlugin(this);

            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // TODO probably extrapolate more
                final int autoUpdateDistance = config.getInt("autoupdatedistance");
                final int maxAffectedBlocks = config.getInt("maxaffectedblocks");
                for(Player player : Bukkit.getOnlinePlayers()) {
                    if(autoUpdateDistance != 0 && player.getGameMode() != GameMode.SPECTATOR) {
                        UUID uuid = UUID.randomUUID();
                        Main.iterations.put(uuid, 0);

                        // TODO clean up all this scheduler code
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Main.iterations.remove(uuid);
                            }
                        }.runTaskLater(Main.plugin, maxAffectedBlocks + 20);

                        Location loc = player.getLocation();
                        for(int x = loc.getBlockX() - autoUpdateDistance;x <= loc.getBlockX() + autoUpdateDistance;x++) {
                            for(int y = loc.getBlockY() - autoUpdateDistance;y <= loc.getBlockY() + autoUpdateDistance;y++) {
                                for(int z = loc.getBlockZ() - autoUpdateDistance;z <= player.getLocation().getBlockZ() + autoUpdateDistance;z++) {
                                    Main.updateBlock(player.getWorld().getBlockAt(x, y, z), true, uuid);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20, 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                // TODO optimize
                for(World world : Bukkit.getWorlds()) {
                    for(Entity entity1 : world.getEntities()) {
                        if(entity1 instanceof FallingBlock && entity1.getPersistentDataContainer().has(explodedkey, PersistentDataType.INTEGER)) {
                            for(Entity entity2 : entity1.getNearbyEntities(50, 50, 50)) {
                                if(entity2 instanceof Player player && entity2.getPersistentDataContainer().has(explosionparticleskey, PersistentDataType.INTEGER)) {
                                    player.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity1.getLocation(), 1, 0, 0, 0, 0);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 1, 1);

        this.getLogger().info("Block Physics v" + version + " loaded");
    }
    public static void updateBlock(Block block, boolean includeself, UUID uuid) {
        new BukkitRunnable() {
            @Override
            public void run() {
                final int maxAffectedBlocks = config.getInt("maxaffectedblocks");
                final boolean chainUpdates = config.getBoolean("chainupdates");
                for(int x = -1;x <= 1; x++) {
                    for(int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Location nlocation = block.getLocation().add(x, y, z);
                            World world = nlocation.getWorld();
                            Block nblock = world.getBlockAt(nlocation);
                            if (!(!includeself && nblock == block) && !unstableblocks.contains(nblock.getType()) && unstableblocks.contains(world.getBlockAt(nblock.getX(), nblock.getY() - 1, nblock.getZ()).getType()) && !stableblocks.contains(nblock.getType()) && !new CustomBlockData(nblock, plugin).has(new NamespacedKey(plugin, "ignorephysics"), PersistentDataType.INTEGER)) {
                                iterations.put(uuid, iterations.get(uuid) + 1);
                                if (maxAffectedBlocks != 0 && iterations.get(uuid) > maxAffectedBlocks) return;

                                BlockData data = nblock.getBlockData();

                                if (data instanceof Snowable snowable) snowable.setSnowy(false);

                                if (data.getMaterial() == Material.POWDER_SNOW) data = Material.SNOW_BLOCK.createBlockData();

                                nblock.setType(Material.AIR);

                                FallingBlock fblock = world.spawnFallingBlock(nblock.getLocation().add(0.5, 0.5, 0.5), data);
                                fblock.getPersistentDataContainer().set(new NamespacedKey(plugin, "eventid"), PersistentDataType.STRING, uuid.toString());

                                if (chainUpdates) updateBlock(nblock, false, uuid);
                            }
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1);
    }
}
