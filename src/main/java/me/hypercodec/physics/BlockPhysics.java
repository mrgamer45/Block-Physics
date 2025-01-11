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
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Logger;

public class BlockPhysics extends JavaPlugin {
    public static BlockPhysics plugin;

    public static Set<Material> stableblocks = new HashSet<>();
    public static Set<Material> unstableblocks = new HashSet<>();

    public static Map<UUID, Integer> iterations = new HashMap<>();

    public static NamespacedKey ignorephysicskey;
    public static NamespacedKey eventidkey;
    public static NamespacedKey explodedkey;
    public static NamespacedKey explosionparticleskey;

    public static FileConfiguration config;
    public static Logger logger;
    public static BukkitScheduler scheduler;

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
        scheduler = this.getServer().getScheduler();

        this.getServer().getPluginManager().registerEvents(new BlockPhysicsListener(), this);

        final PluginCommand ep = this.getCommand("explosionparticles");
        ep.setExecutor(new ExplosionParticles());
        ep.setTabCompleter(new ExplosionParticles());

        this.saveDefaultConfig();

        ignorephysicskey = new NamespacedKey(plugin, "ignorephysics");
        eventidkey = new NamespacedKey(plugin, "eventid");
        explodedkey = new NamespacedKey(plugin, "exploded");
        explosionparticleskey = new NamespacedKey(plugin, "explosionparticles");

        for(String val : config.getStringList("stableblocks")) {
            try {
                stableblocks.add(Material.valueOf(val));
            } catch (IllegalArgumentException e) {
                logger.severe("\"" + val + "\" is not a valid block material");
            }
        }

        for(String val : config.getStringList("unstableblocks")) {
            try {
                if(stableblocks.contains(Material.valueOf(val))) {
                    logger.warning("\"" + val + "\" is defined as both stable and unstable");
                    continue;
                }

                unstableblocks.add(Material.valueOf(val));
            } catch (IllegalArgumentException e) {
                logger.severe("\"" + val + "\" is not a valid block material");
            }
        }

        scheduler.runTaskTimer(this, () -> {
            // TODO probably extrapolate more
            final int autoUpdateDistance = config.getInt("autoupdatedistance");
            final int maxAffectedBlocks = config.getInt("maxaffectedblocks");
            for(Player player : Bukkit.getOnlinePlayers()) {
                if(autoUpdateDistance != 0 && player.getGameMode() != GameMode.SPECTATOR) {
                    UUID uuid = UUID.randomUUID();
                    BlockPhysics.iterations.put(uuid, 0);

                    scheduler.runTaskLater(BlockPhysics.plugin, () -> BlockPhysics.iterations.remove(uuid), maxAffectedBlocks + 20);

                    Location loc = player.getLocation();
                    for(int x = loc.getBlockX() - autoUpdateDistance;x <= loc.getBlockX() + autoUpdateDistance;x++) {
                        for(int y = loc.getBlockY() - autoUpdateDistance;y <= loc.getBlockY() + autoUpdateDistance;y++) {
                            for(int z = loc.getBlockZ() - autoUpdateDistance;z <= player.getLocation().getBlockZ() + autoUpdateDistance;z++) {
                                BlockPhysics.updateBlock(player.getWorld().getBlockAt(x, y, z), true, uuid);
                            }
                        }
                    }
                }
            }
        }, 20, 20);

        scheduler.runTaskTimer(this, () -> {
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
        }, 1, 1);
        this.getLogger().info("Block Physics v" + version + " loaded");
    }
    public static void updateBlock(Block block, boolean includeself, UUID uuid) {
        scheduler.runTaskLater(plugin, () -> {
            final int maxAffectedBlocks = config.getInt("maxaffectedblocks");
            final boolean chainUpdates = config.getBoolean("chainupdates");
            for(int x = -1;x <= 1; x++) {
                for(int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Location nlocation = block.getLocation().add(x, y, z);
                        World world = nlocation.getWorld();
                        Block nblock = world.getBlockAt(nlocation);

                        // TODO probably clean up more.
                        if(!includeself && nblock == block) continue;
                        if(unstableblocks.contains(nblock.getType())) continue;
                        if(!unstableblocks.contains(world.getBlockAt(nblock.getX(), nblock.getY() - 1, nblock.getZ()).getType())) continue;
                        if(stableblocks.contains(nblock.getType())) continue;
                        if(new CustomBlockData(nblock, plugin).has(ignorephysicskey, PersistentDataType.INTEGER)) continue;

                        iterations.put(uuid, iterations.get(uuid) + 1);
                        if (maxAffectedBlocks != 0 && iterations.get(uuid) > maxAffectedBlocks) return;

                        BlockData data = nblock.getBlockData();

                        if (data instanceof Snowable snowable) snowable.setSnowy(false);

                        if (data.getMaterial() == Material.POWDER_SNOW) data = Material.SNOW_BLOCK.createBlockData();

                        nblock.setType(Material.AIR);

                        FallingBlock fblock = world.spawnFallingBlock(nblock.getLocation().add(0.5, 0.5, 0.5), data);
                        fblock.getPersistentDataContainer().set(eventidkey, PersistentDataType.STRING, uuid.toString());

                        if (chainUpdates) updateBlock(nblock, false, uuid);
                    }
                }
            }
        }, 1);
    }

    public static @NotNull UUID registerNewIterTracker() {
        UUID uuid = UUID.randomUUID();
        iterations.put(uuid, 0);
        return uuid;
    }

    public static @NotNull UUID registerNewUpdateChain() {
        UUID uuid = registerNewIterTracker();

        final int maxAffectedBlocks = config.getInt("maxaffectedblocks");
        if (maxAffectedBlocks != 0) {
            scheduler.runTaskLater(plugin, () -> iterations.remove(uuid), maxAffectedBlocks + 20);
        }
        return uuid;
    }

    public static @NotNull UUID startUpdateChain(Block block, boolean includeself) {
        UUID uuid = registerNewUpdateChain();
        updateBlock(block, includeself, uuid);
        return uuid;
    }
}
