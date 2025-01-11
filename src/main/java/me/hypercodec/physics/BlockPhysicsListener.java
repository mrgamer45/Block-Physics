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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BlockPhysicsListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        UUID uuid = BlockPhysics.registerNewUpdateChain();

        Player player = event.getPlayer();

        Block block = event.getBlock();
        if (!player.isSneaking() || !BlockPhysics.config.getBoolean("shiftignorephysics") && player.hasPermission("blockphysics.shiftclick")) {
            BlockPhysics.updateBlock(block, true, uuid);
            return;
        }

        new CustomBlockData(block, BlockPhysics.plugin).set(BlockPhysics.ignorephysicskey, PersistentDataType.INTEGER, 1);
        BlockPhysics.updateBlock(block, false, uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        CustomBlockData cbd = new CustomBlockData(block, BlockPhysics.plugin);

        if(cbd.has(BlockPhysics.ignorephysicskey, PersistentDataType.INTEGER)) cbd.remove(BlockPhysics.ignorephysicskey);

        BlockPhysics.startUpdateChain(block, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        Entity ent = event.getEntity();
        if(ent instanceof FallingBlock) {
            PersistentDataContainer pdc = ent.getPersistentDataContainer();
            // TODO clean up if statement (probably make some static config values)
            if(pdc.has(BlockPhysics.eventidkey, PersistentDataType.STRING) && BlockPhysics.plugin.getConfig().getBoolean("fallingblocksupdate") && BlockPhysics.plugin.getConfig().getBoolean("chainupdates")) {
                UUID uuid = UUID.fromString(pdc.get(BlockPhysics.eventidkey, PersistentDataType.STRING));

                final int maxAffectedBlocks = BlockPhysics.config.getInt("maxaffectedblocks");
                if(maxAffectedBlocks != 0) {
                    // TODO make helper method for this scheduler
                    BlockPhysics.scheduler.runTaskLater(BlockPhysics.plugin, () -> BlockPhysics.iterations.remove(uuid), maxAffectedBlocks + 20);
                }
                BlockPhysics.updateBlock(event.getBlock(), false, uuid);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if(BlockPhysics.config.getBoolean("realisticexplosions")) {
            // TODO extract these two lines into a helper method
            UUID uuid = BlockPhysics.registerNewIterTracker();

            final boolean explosionUpdates = BlockPhysics.config.getBoolean("explosionupdates");

            for(Block block : event.blockList()) {
                if(!BlockPhysics.unstableblocks.contains(block.getType()) && !BlockPhysics.stableblocks.contains(block.getType()) && block.getType() != Material.TNT) {
                    Vector launchVec = event.getLocation().toVector().subtract(block.getLocation().toVector()).multiply(10).normalize();

                    BlockData data = block.getBlockData();

                    if(data instanceof Snowable snowable) {
                        snowable.setSnowy(false);
                    }

                    if(data.getMaterial() == Material.POWDER_SNOW) {
                        data = Material.SNOW_BLOCK.createBlockData();
                    }

                    block.setType(Material.AIR);

                    FallingBlock fb = event.getLocation().getWorld().spawnFallingBlock(block.getLocation(), data);
                    PersistentDataContainer pdc = fb.getPersistentDataContainer();

                    pdc.set(BlockPhysics.eventidkey, PersistentDataType.STRING, uuid.toString());
                    pdc.set(BlockPhysics.explodedkey, PersistentDataType.INTEGER, 1);
                    fb.setHurtEntities(true);
                    fb.setVelocity(launchVec);

                    // TODO maybe a helper method to increment this
                    BlockPhysics.iterations.put(uuid, BlockPhysics.iterations.get(uuid) + 1);

                    if(explosionUpdates)
                        BlockPhysics.updateBlock(block, false, uuid);
                }
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if(BlockPhysics.config.getBoolean("realisticexplosions")) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDropItem(@NotNull EntityDropItemEvent event) {
        Entity ent = event.getEntity();
        Location loc = ent.getLocation();
        if(ent.getType() == EntityType.FALLING_BLOCK && !BlockPhysics.config.getBoolean("unsolidblocksbreakfbs") && !(loc.getBlock().getState() instanceof TileState)) {
            try {
                event.setCancelled(true);
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
        if(BlockPhysics.config.getBoolean("projectilesupdate") && event.getHitBlock() != null)
            BlockPhysics.startUpdateChain(event.getHitBlock(), true);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(!player.hasPlayedBefore() && BlockPhysics.config.getBoolean("explosionparticlesdefault"))
            player.getPersistentDataContainer().set(BlockPhysics.explosionparticleskey, PersistentDataType.INTEGER, 1);
    }
}
