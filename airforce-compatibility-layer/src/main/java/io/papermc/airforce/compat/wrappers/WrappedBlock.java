package io.papermc.airforce.compat.wrappers;

import io.papermc.airforce.compat.scheduler.RegionTaskDelegator;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

// Basic placeholder for WrappedBlock, to be expanded
public class WrappedBlock implements Block {
    private static final Logger LOGGER = Logger.getLogger(WrappedBlock.class.getName());

    private final Block realBlock;
    private final RegionTaskDelegator taskDelegator;
    private final WrappedWorld wrappedWorld; // To return wrapped world

    public WrappedBlock(Block realBlock, RegionTaskDelegator taskDelegator, WrappedWorld wrappedWorld) {
        if (realBlock == null) throw new IllegalArgumentException("realBlock cannot be null");
        if (taskDelegator == null) throw new IllegalArgumentException("taskDelegator cannot be null");
        if (wrappedWorld == null) throw new IllegalArgumentException("wrappedWorld cannot be null");
        this.realBlock = realBlock;
        this.taskDelegator = taskDelegator;
        this.wrappedWorld = wrappedWorld;
    }

    @Override
    public byte getData() {
        // Potentially delegate via taskDelegator.callForLocation if not on region thread
        return realBlock.getData();
    }

    @Override
    public Block getRelative(BlockFace face) {
        // Should return WrappedBlock
        return new WrappedBlock(realBlock.getRelative(face), taskDelegator, wrappedWorld);
    }

    @Override
    public Block getRelative(BlockFace face, int distance) {
        // Should return WrappedBlock
        return new WrappedBlock(realBlock.getRelative(face, distance), taskDelegator, wrappedWorld);
    }

    @Override
    public Block getRelative(int modX, int modY, int modZ) {
        // Should return WrappedBlock
        return new WrappedBlock(realBlock.getRelative(modX, modY, modZ), taskDelegator, wrappedWorld);
    }

    @Override
    public Material getType() {
        // Potentially delegate via taskDelegator.callForLocation if not on region thread
        return realBlock.getType();
    }

    @Override
    public void setType(Material type) {
        setType(type, true);
    }

    @Override
    public void setType(Material type, boolean applyPhysics) {
        final Location loc = realBlock.getLocation(); // Capture location for the lambda
        LOGGER.info("[COMPAT LAYER] WrappedBlock.setType called for " + loc + ". Delegating to scheduler.");
        taskDelegator.scheduleForLocation(loc, () -> {
            LOGGER.info("[COMPAT LAYER] Executing realBlock.setType on delegated thread for " + loc);
            realBlock.setType(type, applyPhysics);
        });
    }

    @Override
    public Location getLocation() {
        // Location is immutable enough for now, but could be wrapped if needed
        return realBlock.getLocation();
    }

    @Override
    public Location getLocation(Location loc) {
        return realBlock.getLocation(loc);
    }

    @Override
    public World getWorld() {
        return this.wrappedWorld; // Return the already wrapped world
    }

    // Other Block methods delegated directly or throwing UnsupportedOperationException for now

    @Override
    public int getX() {
        return realBlock.getX();
    }

    @Override
    public int getY() {
        return realBlock.getY();
    }

    @Override
    public int getZ() {
        return realBlock.getZ();
    }

    @Override
    public Chunk getChunk() {
        // TODO: Should return WrappedChunk
        return realBlock.getChunk();
    }

    @Override
    public void setBlockData(BlockData data) {
        setBlockData(data, true);
    }

    @Override
    public void setBlockData(BlockData data, boolean applyPhysics) {
        final Location loc = realBlock.getLocation();
        taskDelegator.scheduleForLocation(loc, () -> realBlock.setBlockData(data, applyPhysics));
    }

    @Override
    public BlockData getBlockData() {
        return realBlock.getBlockData();
    }

    @Override
    public BlockState getState() {
        // TODO: Should return WrappedBlockState, and state itself needs careful handling
        // For now, direct call, but this is a major area for thread safety.
        // May need taskDelegator.callForLocation if called from another thread.
        return realBlock.getState();
    }

    @Override
    public Biome getBiome() {
        return realBlock.getBiome();
    }

    @Override
    public void setBiome(Biome bio) {
        final Location loc = realBlock.getLocation();
        taskDelegator.scheduleForLocation(loc, () -> realBlock.setBiome(bio));
    }

    @Override
    public boolean isBlockPowered() {
        return realBlock.isBlockPowered();
    }

    @Override
    public boolean isBlockIndirectlyPowered() {
        return realBlock.isBlockIndirectlyPowered();
    }

    @Override
    public boolean isBlockFacePowered(BlockFace face) {
        return realBlock.isBlockFacePowered(face);
    }

    @Override
    public boolean isBlockFaceIndirectlyPowered(BlockFace face) {
        return realBlock.isBlockFaceIndirectlyPowered(face);
    }

    @Override
    public int getBlockPower(BlockFace face) {
        return realBlock.getBlockPower(face);
    }

    @Override
    public int getBlockPower() {
        return realBlock.getBlockPower();
    }

    @Override
    public boolean isEmpty() {
        return realBlock.isEmpty();
    }

    @Override
    public boolean isLiquid() {
        return realBlock.isLiquid();
    }

    @Override
    public double getTemperature() {
        return realBlock.getTemperature();
    }

    @Override
    public double getHumidity() {
        return realBlock.getHumidity();
    }

    @Override
    public PistonMoveReaction getPistonMoveReaction() {
        return realBlock.getPistonMoveReaction();
    }

    @Override
    public boolean breakNaturally() {
        // This is a write operation, needs delegation
        final Location loc = realBlock.getLocation();
        // This should be a Callable if it returns a boolean, but API is void.
        // For now, assume it's okay to run async if it's void and returns boolean for success.
        // A proper impl would need to use callForLocation and block.
        // For this step, we are focusing on setType, so this is simplified.
        taskDelegator.scheduleForLocation(loc, () -> realBlock.breakNaturally());
        return true; // Cannot determine actual success without blocking & callable
    }

    @Override
    public boolean breakNaturally(ItemStack tool) {
        final Location loc = realBlock.getLocation();
        taskDelegator.scheduleForLocation(loc, () -> realBlock.breakNaturally(tool));
        return true;
    }

    @Override
    public Collection<ItemStack> getDrops() {
        // Read, potentially snapshot
        return realBlock.getDrops();
    }

    @Override
    public Collection<ItemStack> getDrops(ItemStack tool) {
        return realBlock.getDrops(tool);
    }

    @Override
    public Collection<ItemStack> getDrops(ItemStack tool, Entity entity) {
        return realBlock.getDrops(tool, entity);
    }

    @Override
    public boolean isPassable() {
        return realBlock.isPassable();
    }

    @Override
    public RayTraceResult rayTrace(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode) {
        return realBlock.rayTrace(start, direction, maxDistance, fluidCollisionMode);
    }

    @Override
    public BoundingBox getBoundingBox() {
        return realBlock.getBoundingBox();
    }

    @Override
    public VoxelShape getCollisionShape() {
        return realBlock.getCollisionShape();
    }

    @Override
    public boolean canPlace(BlockData data) {
        return realBlock.canPlace(data);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return realBlock.getMetadata(metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return realBlock.hasMetadata(metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        realBlock.removeMetadata(metadataKey, owningPlugin);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        realBlock.setMetadata(metadataKey, newMetadataValue);
    }

    @Override
    public String getTranslationKey() {
       return realBlock.getTranslationKey();
    }

    @Override
    public float getDestroySpeed(ItemStack itemStack, boolean considerTools) {
        return realBlock.getDestroySpeed(itemStack, considerTools);
    }
}
