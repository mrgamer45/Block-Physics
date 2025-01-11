package me.hypercodec.physics;

import com.jeff_media.customblockdata.CustomBlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Snowable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BlockPhysicsListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID uuid = UUID.randomUUID();
        BlockPhysics.iterations.put(uuid, 0);

        final int maxAffectedBlocks = BlockPhysics.config.getInt("maxaffectedblocks");
        if (maxAffectedBlocks != 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    BlockPhysics.iterations.remove(uuid);
                }
            }.runTaskLater(BlockPhysics.plugin, maxAffectedBlocks + 20);
        }

        Block block = event.getBlock();
        if (!event.getPlayer().isSneaking() || !BlockPhysics.config.getBoolean("shiftignorephysics") && event.getPlayer().hasPermission("blockphysics.shiftclick")) {
            BlockPhysics.updateBlock(block, true, uuid);
            return;
        }

        new CustomBlockData(block, BlockPhysics.plugin).set(BlockPhysics.ignorephysicskey, PersistentDataType.INTEGER, 1);
        BlockPhysics.updateBlock(block, false, uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if(new CustomBlockData(event.getBlock(), BlockPhysics.plugin).has(BlockPhysics.ignorephysicskey, PersistentDataType.INTEGER)) {new CustomBlockData(event.getBlock(), BlockPhysics.plugin).remove(BlockPhysics.ignorephysicskey);}

        UUID uuid = UUID.randomUUID();
        BlockPhysics.iterations.put(uuid, 0);

        final int maxAffectedBlocks = BlockPhysics.config.getInt("maxaffectedblocks");
        if (maxAffectedBlocks != 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    BlockPhysics.iterations.remove(uuid);
                }
            }.runTaskLater(BlockPhysics.plugin, maxAffectedBlocks + 20);
        }
        BlockPhysics.updateBlock(event.getBlock(), false, uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if(event.getEntity() instanceof FallingBlock) {
            if(event.getEntity().getPersistentDataContainer().has(BlockPhysics.eventidkey, PersistentDataType.STRING) && BlockPhysics.plugin.getConfig().getBoolean("fallingblocksupdate") && BlockPhysics.plugin.getConfig().getBoolean("chainupdates")) {
                UUID uuid = UUID.fromString(event.getEntity().getPersistentDataContainer().get(BlockPhysics.eventidkey, PersistentDataType.STRING));

                final int maxAffectedBlocks = BlockPhysics.config.getInt("maxaffectedblocks");
                if(maxAffectedBlocks != 0) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            BlockPhysics.iterations.remove(uuid);
                        }
                    }.runTaskLater(BlockPhysics.plugin, maxAffectedBlocks + 20);
                }
                BlockPhysics.updateBlock(event.getBlock(), false, uuid);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if(BlockPhysics.plugin.getConfig().getBoolean("realisticexplosions")) {
            UUID uuid = UUID.randomUUID();
            BlockPhysics.iterations.put(uuid, 0);

            for(Block block : event.blockList()) {
                if(!BlockPhysics.unstableblocks.contains(block.getType()) && !BlockPhysics.stableblocks.contains(block.getType()) && block.getType() != Material.TNT) {
                    Vector launchVec = event.getLocation().toVector().subtract(block.getLocation().toVector()).multiply(10).normalize();

                    BlockData data = block.getBlockData();

                    if(data instanceof Snowable) {
                        ((Snowable) data).setSnowy(false);
                    }

                    if(data.getMaterial() == Material.POWDER_SNOW) {
                        data = Material.SNOW_BLOCK.createBlockData();
                    }

                    block.setType(Material.AIR);

                    FallingBlock fb = event.getLocation().getWorld().spawnFallingBlock(block.getLocation(), data);
                    fb.getPersistentDataContainer().set(BlockPhysics.eventidkey, PersistentDataType.STRING, uuid.toString());
                    fb.getPersistentDataContainer().set(BlockPhysics.explodedkey, PersistentDataType.INTEGER, 1);
                    fb.setHurtEntities(true);
                    fb.setVelocity(launchVec);

                    BlockPhysics.iterations.put(uuid, BlockPhysics.iterations.get(uuid) + 1);

                    if(BlockPhysics.plugin.getConfig().getBoolean("explosionupdates")) {
                        BlockPhysics.updateBlock(block, false, uuid);}
                }
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {if(BlockPhysics.config.getBoolean("realisticexplosions")) event.setCancelled(true);}

    @EventHandler(ignoreCancelled = true)
    public void onEntityDropItem(@NotNull EntityDropItemEvent event) {
        if(event.getEntity().getType() == EntityType.FALLING_BLOCK && !BlockPhysics.plugin.getConfig().getBoolean("unsolidblocksbreakfbs") && !(event.getEntity().getLocation().getBlock().getState() instanceof TileState)) {
            try {
                event.setCancelled(true);
                Entity ent = event.getEntity();
                Location loc = ent.getLocation();
                loc.getWorld().dropItemNaturally(event.getEntity().getLocation(), new ItemStack(event.getEntity().getLocation().getBlock().getType()));
                // TODO round down instead of doing all this weird bs
                loc.getBlock().getLocation().getBlock().setBlockData(((FallingBlock) event.getEntity()).getBlockData());
                ent.remove();
            }
            catch(IllegalArgumentException ignored) {}
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if(BlockPhysics.config.getBoolean("projectilesupdate") && event.getHitBlock() != null) {
            UUID uuid = UUID.randomUUID();
            BlockPhysics.iterations.put(uuid, 0);

            final int maxAffectedBlocks = BlockPhysics.config.getInt("maxaffectedblocks");
            if (maxAffectedBlocks != 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        BlockPhysics.iterations.remove(uuid);
                    }
                }.runTaskLater(BlockPhysics.plugin, maxAffectedBlocks + 20);
            }
            BlockPhysics.updateBlock(event.getHitBlock(), true, uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(!player.hasPlayedBefore() && BlockPhysics.config.getBoolean("explosionparticlesdefault"))
            player.getPersistentDataContainer().set(BlockPhysics.explosionparticleskey, PersistentDataType.INTEGER, 1);
    }
}
