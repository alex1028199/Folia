package io.papermc.folia.compat.wrappers;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import io.papermc.folia.compat.wrappers.entity.WrappedEntity;
import io.papermc.folia.compat.wrappers.entity.WrappedLivingEntity;
import io.papermc.folia.compat.wrappers.entity.WrappedPlayer;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class WrappedChunk implements Chunk {
    private static final Logger LOGGER = Logger.getLogger(WrappedChunk.class.getName());

    private final Chunk realChunk;
    private final WrappedWorld wrappedWorld;
    private final RegionTaskDelegator taskDelegator;

    public WrappedChunk(Chunk realChunk, WrappedWorld wrappedWorld, RegionTaskDelegator taskDelegator) {
        if (realChunk == null) throw new IllegalArgumentException("realChunk cannot be null");
        if (wrappedWorld == null) throw new IllegalArgumentException("wrappedWorld cannot be null");
        if (taskDelegator == null) throw new IllegalArgumentException("taskDelegator cannot be null");
        this.realChunk = realChunk;
        this.wrappedWorld = wrappedWorld;
        this.taskDelegator = taskDelegator;
    }

    private Location getRegionDelegationLocation() {
        // Center of the chunk at sea level (or y=64 as a fallback)
        // The Y value might not be super critical if Folia's region is purely X/Z based for the scheduler.
        int y = wrappedWorld.getEnvironment() == World.Environment.NETHER ? 32 : wrappedWorld.getSeaLevel();
        if (y < wrappedWorld.getMinHeight() && wrappedWorld.getMinHeight() <= 64) y = 64; // fallback for custom worlds
         else if (y < wrappedWorld.getMinHeight()) y = wrappedWorld.getMinHeight();


        return new Location(wrappedWorld, (getX() << 4) + 8, y, (getZ() << 4) + 8);
    }

    @Override
    public World getWorld() {
        return wrappedWorld;
    }

    @Override
    public int getX() {
        return realChunk.getX();
    }

    @Override
    public int getZ() {
        return realChunk.getZ();
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        // x, y, z are 0-15 relative chunk coords for this method
        // Construct absolute world coordinates
        int worldX = (getX() << 4) + x;
        int worldZ = (getZ() << 4) + z;
        // Y is absolute already in this context for Bukkit API
        Location blockLocation = new Location(this.wrappedWorld, worldX, y, worldZ);
        return this.wrappedWorld.getBlockAt(blockLocation);
    }

    @Override
    public Entity[] getEntities() {
        Entity[] realEntities = realChunk.getEntities();
        List<Entity> wrappedEntities = new ArrayList<>(realEntities.length);
        for (Entity entity : realEntities) {
            if (entity instanceof Player) {
                wrappedEntities.add(new WrappedPlayer((Player) entity, this.taskDelegator));
            } else if (entity instanceof LivingEntity) {
                wrappedEntities.add(new WrappedLivingEntity((LivingEntity) entity, this.taskDelegator));
            } else {
                wrappedEntities.add(new WrappedEntity(entity, this.taskDelegator));
            }
        }
        return wrappedEntities.toArray(new Entity[0]); // Bukkit API specifies Entity[]
    }

    @Override
    public boolean isLoaded() {
        // Read operation, can be direct for now
        return realChunk.isLoaded();
    }

    @Override
    public boolean load(boolean generate) {
        LOGGER.info("[COMPAT LAYER] WrappedChunk.load(" + generate + ") for chunk " + getX() + "," + getZ() + ". Delegating.");
        // This method returns boolean. A proper implementation would use callForLocation.
        // For now, optimistic return true, actual operation is fire-and-forget.
        taskDelegator.scheduleForLocation(getRegionDelegationLocation(), () -> realChunk.load(generate));
        return true; // Non-compliant: doesn't reflect actual load success
    }

    @Override
    public boolean load() {
        return load(true);
    }

    @Override
    public boolean unload(boolean save) {
        LOGGER.info("[COMPAT LAYER] WrappedChunk.unload(" + save + ") for chunk " + getX() + "," + getZ() + ". Delegating.");
        // This method returns boolean. Proper impl would use callForLocation.
        taskDelegator.scheduleForLocation(getRegionDelegationLocation(), () -> realChunk.unload(save));
        return true; // Non-compliant
    }

    @Override
    public boolean unload() {
        return unload(true);
    }

    // Direct delegations for other simpler methods for now
    @Override public BlockState[] getTileEntities() {
        // TODO: Wrap BlockState[]
        LOGGER.warning("WrappedChunk.getTileEntities - direct call, needs BlockState wrapping");
        return realChunk.getTileEntities();
    }
    @Override public boolean isSlimeChunk() { return realChunk.isSlimeChunk(); }
    @Override public boolean isForceLoaded() { return realChunk.isForceLoaded(); }
    @Override public void setForceLoaded(boolean forced) { taskDelegator.scheduleForLocation(getRegionDelegationLocation(), () -> realChunk.setForceLoaded(forced)); }
    @Override public boolean addPluginChunkTicket(Plugin plugin) {
        // Needs careful thought about plugin identity across classloaders if Folia remaps plugins.
        // For now, assuming direct call is okay for ticket management if chunk is already loaded.
        // However, the act of adding a ticket might need to be on a global thread.
        LOGGER.warning("WrappedChunk.addPluginChunkTicket - direct call, may need global delegation");
        return realChunk.addPluginChunkTicket(plugin);
    }
    @Override public boolean removePluginChunkTicket(Plugin plugin) {
        LOGGER.warning("WrappedChunk.removePluginChunkTicket - direct call, may need global delegation");
        return realChunk.removePluginChunkTicket(plugin);
    }
    @Override public Collection<Plugin> getPluginChunkTickets() { return realChunk.getPluginChunkTickets(); } // Snapshot
    @Override public long getInhabitedTime() { return realChunk.getInhabitedTime(); }
    @Override public void setInhabitedTime(long ticks) { taskDelegator.scheduleForLocation(getRegionDelegationLocation(), () -> realChunk.setInhabitedTime(ticks)); }
    @Override public boolean contains(BlockData block) { return realChunk.contains(block); }
    @Override public boolean contains(org.bukkit.block.Biome biome) {return false;}
    @Override public ChunkSnapshot getChunkSnapshot() { return realChunk.getChunkSnapshot(); } // Snapshots are immutable, generally safe
    @Override public ChunkSnapshot getChunkSnapshot(boolean includeMaxBlockY, boolean includeBiome, boolean includeBiomeTempRain) { return realChunk.getChunkSnapshot(includeMaxBlockY, includeBiome, includeBiomeTempRain); }
    @Override public org.bukkit.HeightMap getHeightMap(org.bukkit.HeightMapType heightMapType) {return null;}
    @Override public int getMinHeight() {return 0;}
    @Override public int getLogicalHeight() {return 0;}
    @Override public Predicate<Entity> getEntityCollisionPredicate() {return null;}
    @Override public CompletableFuture<Void> getEntitiesAsync(Predicate<Entity> predicate, List<Entity> list) {return null;}
    @Override public LoadLevel getLoadLevel() {return null;}

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof WrappedChunk) {
            return this.realChunk.equals(((WrappedChunk) obj).realChunk);
        }
        if (obj instanceof Chunk) {
            return this.realChunk.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return realChunk.hashCode();
    }
}
