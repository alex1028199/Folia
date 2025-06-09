package io.papermc.airforce.compat.wrappers;

import io.papermc.airforce.compat.scheduler.RegionTaskDelegator;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Consumer;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WrappedWorld implements World {
    private static final Logger LOGGER = Logger.getLogger(WrappedWorld.class.getName());

    private final World realWorld;
    private final RegionTaskDelegator taskDelegator;

    public WrappedWorld(World realWorld, RegionTaskDelegator taskDelegator) {
        if (realWorld == null) throw new IllegalArgumentException("realWorld cannot be null");
        if (taskDelegator == null) throw new IllegalArgumentException("taskDelegator cannot be null");
        this.realWorld = realWorld;
        this.taskDelegator = taskDelegator;
        LOGGER.info("[COMPAT LAYER] WrappedWorld created for world: " + realWorld.getName());
    }

    public RegionTaskDelegator getTaskDelegator() {
        return taskDelegator;
    }

    public World getRealWorld() {
        return realWorld;
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        // In a full implementation, we might check if this call is on the region thread.
        // If not, getting the real block and wrapping it might be fine for read-only if Block is immutable enough,
        // but Folia might have its own wrappers internally.
        // For now, assume Block object itself is relatively safe to obtain, but its methods are guarded.
        return new WrappedBlock(realWorld.getBlockAt(x, y, z), taskDelegator, this);
    }

    @Override
    public Block getBlockAt(Location location) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        if (!this.equals(location.getWorld())) { // Basic check
            throw new IllegalArgumentException("Location is for a different world");
        }
        return new WrappedBlock(realWorld.getBlockAt(location), taskDelegator, this);
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        // This is a read. For now, direct. Could be delegated if proves problematic.
        // return taskDelegator.callForLocation(new Location(this, x, 0, z), () -> realWorld.getHighestBlockYAt(x, z));
        return realWorld.getHighestBlockYAt(x, z);
    }

    @Override
    public int getHighestBlockYAt(Location location) {
        return realWorld.getHighestBlockYAt(location);
    }

    @Override
    public Chunk getChunkAt(int x, int z) {
        // Assuming that getting the chunk object itself is relatively safe,
        // but operations on it will be wrapped/delegated by WrappedChunk.
        return new WrappedChunk(realWorld.getChunkAt(x, z), this, taskDelegator);
    }

    @Override
    public Chunk getChunkAt(Location location) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        if (!this.equals(location.getWorld())) {
             throw new IllegalArgumentException("Location is for a different world");
        }
        return new WrappedChunk(realWorld.getChunkAt(location), this, taskDelegator);
    }

    @Override
    public Chunk getChunkAt(Block block) {
        if (block == null) throw new IllegalArgumentException("Block cannot be null");
        if (!this.equals(block.getWorld())) {
            throw new IllegalArgumentException("Block is for a different world");
        }
        return new WrappedChunk(realWorld.getChunkAt(block), this, taskDelegator);
    }

    // For methods returning collections, we should return immutable copies/snapshots
    // This is a simplified example; a full implementation needs to be more careful
    // about the implications of snapshotting (staleness) vs. live (but potentially unsafe) data.

    @Override
    public List<Entity> getEntities() {
        List<Entity> realEntities = this.realWorld.getEntities();
        List<Entity> wrappedEntities = new ArrayList<>(realEntities.size());
        for (Entity entity : realEntities) {
            if (entity instanceof Player) {
                wrappedEntities.add(new io.papermc.airforce.compat.wrappers.entity.WrappedPlayer((Player) entity, this.taskDelegator));
            } else if (entity instanceof LivingEntity) {
                wrappedEntities.add(new io.papermc.airforce.compat.wrappers.entity.WrappedLivingEntity((LivingEntity) entity, this.taskDelegator));
            } else {
                wrappedEntities.add(new io.papermc.airforce.compat.wrappers.entity.WrappedEntity(entity, this.taskDelegator));
            }
        }
        return Collections.unmodifiableList(wrappedEntities);
    }

    @Override
    public List<LivingEntity> getLivingEntities() {
        List<LivingEntity> realEntities = this.realWorld.getLivingEntities();
        List<LivingEntity> wrappedEntities = new ArrayList<>(realEntities.size());
        for (LivingEntity entity : realEntities) {
            if (entity instanceof Player) {
                wrappedEntities.add(new io.papermc.airforce.compat.wrappers.entity.WrappedPlayer((Player) entity, this.taskDelegator));
            } else {
                wrappedEntities.add(new io.papermc.airforce.compat.wrappers.entity.WrappedLivingEntity(entity, this.taskDelegator));
            }
        }
        return Collections.unmodifiableList(wrappedEntities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> cls) {
        Collection<T> realEntities = this.realWorld.getEntitiesByClass(cls);
        List<T> wrappedEntities = new ArrayList<>(realEntities.size());
        for (T entity : realEntities) {
            if (Player.class.isAssignableFrom(cls) && entity instanceof Player) {
                wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedPlayer((Player) entity, this.taskDelegator));
            } else if (LivingEntity.class.isAssignableFrom(cls) && entity instanceof LivingEntity) {
                wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedLivingEntity((LivingEntity) entity, this.taskDelegator));
            } else if (Entity.class.isAssignableFrom(cls)) { // Check if cls is Entity or superclass
                 wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedEntity(entity, this.taskDelegator));
            } else {
                // If it's a more specific non-LivingEntity, non-Player type, we might not have a wrapper or add real entity
                LOGGER.warning("[COMPAT LAYER] getEntitiesByClass for specific type " + cls.getName() + " may return non-wrapped or partially wrapped entities.");
                wrappedEntities.add(entity); // Or a more generic WrappedEntity if appropriate
            }
        }
        return Collections.unmodifiableList(wrappedEntities);
    }

    @Override
    public <T extends Entity> Collection<T> getEntitiesByClasses(Class<?>... classes) {
        Collection<T> realEntities = this.realWorld.getEntitiesByClasses(classes);
        List<T> wrappedEntities = new ArrayList<>(realEntities.size());
        // This logic is more complex due to multiple classes.
        // A simple approach for now, but might not wrap perfectly according to each class type.
        for (T entity : realEntities) {
            if (entity instanceof Player) {
                wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedPlayer((Player) entity, this.taskDelegator));
            } else if (entity instanceof LivingEntity) {
                wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedLivingEntity((LivingEntity) entity, this.taskDelegator));
            } else {
                wrappedEntities.add((T) new io.papermc.airforce.compat.wrappers.entity.WrappedEntity(entity, this.taskDelegator));
            }
        }
        return Collections.unmodifiableList(wrappedEntities);
    }

    @Override
    public List<Player> getPlayers() {
        List<Player> realPlayers = this.realWorld.getPlayers();
        List<Player> wrappedPlayers = new ArrayList<>(realPlayers.size());
        for (Player player : realPlayers) {
            wrappedPlayers.add(new io.papermc.airforce.compat.wrappers.entity.WrappedPlayer(player, this.taskDelegator));
        }
        return Collections.unmodifiableList(wrappedPlayers);
    }

    // UID and Name are generally safe
    @Override
    public String getName() {
        return realWorld.getName();
    }

    @Override
    public UUID getUID() {
        return realWorld.getUID();
    }

    @Override
    public Location getSpawnLocation() {
        // Location is immutable enough for now
        return realWorld.getSpawnLocation();
    }

    @Override
    public boolean setSpawnLocation(Location location) {
        // This is a write operation, potentially global or needs special handling by Folia
        LOGGER.info("[COMPAT LAYER] WrappedWorld.setSpawnLocation called. Delegating to global or real object for now.");
        // taskDelegator.scheduleForLocation(location, () -> realWorld.setSpawnLocation(location));
        // Or, if it's a global property for the world:
        // taskDelegator.scheduleOnGlobalThread(() -> realWorld.setSpawnLocation(location));
        return realWorld.setSpawnLocation(location); // Direct for now
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float angle) {
        return realWorld.setSpawnLocation(x,y,z,angle);
    }
    @Override
    public boolean setSpawnLocation(int x, int y, int z) {
        return realWorld.setSpawnLocation(x,y,z);
    }


    // Many methods would need similar treatment:
    // - Reads: Direct, or snapshot via delegator if data is highly mutable or sensitive.
    // - Writes: Delegate via taskDelegator to appropriate region or global thread.
    // - Return types: Wrap Bukkit objects (Entity, Chunk, etc.) in their respective Wrapped counterparts.

    // Example of a method that might need delegation for safety:
    @Override
    public void setTime(long time) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.setTime called. This is a global world property, potentially needs careful handling or delegation to a global tick source.");
        // This should likely be run on a main/global thread, not a specific region.
        // taskDelegator.scheduleOnGlobalThread(() -> realWorld.setTime(time));
        realWorld.setTime(time); // Direct for now
    }

    @Override
    public long getTime() {
        return realWorld.getTime();
    }

    // Auto-generated delegates ( স্থূল ) - these would need individual review for thread safety.
    // For this exercise, most will be direct calls or throw UnsupportedOperationException.

    @Override
    public WorldType getWorldType() { return realWorld.getWorldType(); }
    @Override
    public boolean canGenerateStructures() { return realWorld.canGenerateStructures(); }
    @Override
    public boolean isHardcore() { return realWorld.isHardcore(); }
    @Override
    public void setHardcore(boolean hardcore) { realWorld.setHardcore(hardcore); } // Global state controller?
    @Override
    public long getFullTime() { return realWorld.getFullTime(); }
    @Override
    public void setFullTime(long time) { realWorld.setFullTime(time); } // Global state controller / global tick source
    @Override
    public boolean createExplosion(double x, double y, double z, float power) {
        // Complex, involves multiple blocks and entities. Needs careful delegation.
        Location loc = new Location(this,x,y,z);
        // This is a "write" that affects an area. Folia likely has special handling.
        // For now, conceptual:
        // taskDelegator.scheduleForLocation(loc, () -> realWorld.createExplosion(x,y,z,power));
        LOGGER.warning("WrappedWorld.createExplosion(coords, power) - direct call, needs proper delegation");
        return realWorld.createExplosion(x,y,z,power);
    }
    // ... and so on for many other World methods.

    // For methods not yet properly wrapped or decided upon:
    private <T> T unsupported(String methodName) {
        throw new UnsupportedOperationException("Method " + methodName + " is not yet supported by WrappedWorld in the compatibility layer.");
    }
    @Override public ChunkGenerator getGenerator() { return unsupported("getGenerator"); }

    // Delegate remaining methods directly, or throw UnsupportedOperationException
    // This is just a small subset for brevity

    @Override public File getWorldFolder() { return realWorld.getWorldFolder(); }
    @Override public Environment getEnvironment() { return realWorld.getEnvironment(); }
    @Override public long getSeed() { return realWorld.getSeed(); }
    @Override public boolean getPVP() { return realWorld.getPVP(); }
    @Override public void setPVP(boolean pvp) { realWorld.setPVP(pvp); } // Global state
    @Override public List<BlockPopulator> getPopulators() { return Collections.unmodifiableList(realWorld.getPopulators()); } // Snapshot
    @Override public <T extends Entity> T spawn(Location location, Class<T> clazz) throws IllegalArgumentException { return unsupported("spawn(Location,Class)");} // Needs entity wrapping + delegation
    @Override public <T extends Entity> T spawn(Location location, Class<T> clazz, Consumer<T> function) throws IllegalArgumentException { return unsupported("spawn(Location,Class,Consumer)"); }
    @Override public FallingBlock spawnFallingBlock(Location location, BlockData data) throws IllegalArgumentException { return unsupported("spawnFallingBlock"); } // Needs entity wrapping + delegation

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof WrappedWorld) {
            return this.realWorld.equals(((WrappedWorld) obj).realWorld);
        }
        if (obj instanceof World) { // For comparison with unwrapped world instances if they leak
            return this.realWorld.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return realWorld.hashCode();
    }

    // --- All other World methods would need to be implemented ---
    // Many would be direct delegations: realWorld.methodName()
    // Others would need careful thought about threading, snapshotting, and wrapping return types.
    // For brevity, they are omitted here but would be part of a full implementation.
    @Override public int getMinHeight() { return realWorld.getMinHeight(); }
    @Override public int getMaxHeight() { return realWorld.getMaxHeight(); }
    @Override public int getHighestBlockYAt(int x, int z, HeightMap heightMap) { return realWorld.getHighestBlockYAt(x,z,heightMap); }
    @Override public int getHighestBlockYAt(Location location, HeightMap heightMap) { return realWorld.getHighestBlockYAt(location, heightMap); }
    @Override public Block getHighestBlockAt(int x, int z) { return new WrappedBlock(realWorld.getHighestBlockAt(x,z), taskDelegator, this); }
    @Override public Block getHighestBlockAt(Location location) { return new WrappedBlock(realWorld.getHighestBlockAt(location), taskDelegator, this); }
    @Override public Block getHighestBlockAt(int x, int z, HeightMap heightMap) { return new WrappedBlock(realWorld.getHighestBlockAt(x,z,heightMap), taskDelegator, this); }
    @Override public Block getHighestBlockAt(Location location, HeightMap heightMap) { return new WrappedBlock(realWorld.getHighestBlockAt(location,heightMap), taskDelegator, this); }
    @Override public Biome getBiome(int x, int y, int z) { return realWorld.getBiome(x,y,z); }
    @Override public void setBiome(int x, int y, int z, Biome bio) { taskDelegator.scheduleForLocation(new Location(this,x,y,z), ()-> realWorld.setBiome(x,y,z,bio));}
    @Override public BiomeProvider getBiomeProvider() { return realWorld.getBiomeProvider(); } // May need wrapping
    @Override public boolean isChunkLoaded(Chunk chunk) { return realWorld.isChunkLoaded(chunk); } // Chunk should be wrapped
    @Override public Chunk[] getLoadedChunks() {
        return Arrays.stream(realWorld.getLoadedChunks())
            .map(chunk -> new WrappedChunk(chunk, this, taskDelegator))
            .toArray(Chunk[]::new);
    }
    @Override public void loadChunk(Chunk chunk) {
        // If chunk is already a WrappedChunk, we might get its realChunk or handle it.
        // For now, assume it might be a raw chunk from elsewhere (less likely with good wrapping)
        Chunk actualChunk = (chunk instanceof WrappedChunk) ? ((WrappedChunk) chunk).realChunk : chunk;
        LOGGER.info("[COMPAT LAYER] WrappedWorld.loadChunk(Chunk) for " + actualChunk.getX() + "," + actualChunk.getZ() + ". Delegating if not already loaded.");
        // Folia might have its own way to "request" a chunk load that is safer.
        // This direct call might be okay if the chunk is already somewhat managed by Folia.
        // The WrappedChunk.load() is more about plugin-initiated loads.
        if (!actualChunk.isLoaded()) {
            Location loc = new Location(this, actualChunk.getX() << 4, 64, actualChunk.getZ() << 4);
            taskDelegator.scheduleForLocation(loc, () -> actualChunk.load(true));
        }
    }
    @Override public boolean isChunkLoaded(int x, int z) { return realWorld.isChunkLoaded(x,z); }
    @Override public boolean isChunkGenerated(int x, int z) { return realWorld.isChunkGenerated(x,z); }
    @Override public boolean isChunkInUse(int x, int z) { return realWorld.isChunkInUse(x,z); }
    @Override public void loadChunk(int x, int z) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.loadChunk(" + x + "," + z + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.loadChunk(x, z, true));
    }
    @Override public boolean loadChunk(int x, int z, boolean generate) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.loadChunk(" + x + "," + z + ", " + generate + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        // This returns boolean, should use callForLocation for strict compliance
        taskDelegator.scheduleForLocation(loc, () -> realWorld.loadChunk(x, z, generate));
        return true; // Non-compliant
    }
    @Override public boolean unloadChunk(Chunk chunk) {
        Chunk actualChunk = (chunk instanceof WrappedChunk) ? ((WrappedChunk) chunk).realChunk : chunk;
        LOGGER.info("[COMPAT LAYER] WrappedWorld.unloadChunk(Chunk) for " + actualChunk.getX() + "," + actualChunk.getZ() + ". Delegating.");
        Location loc = new Location(this, actualChunk.getX() << 4, 64, actualChunk.getZ() << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.unloadChunk(actualChunk));
        return true; // Non-compliant
    }
    @Override public boolean unloadChunk(int x, int z) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.unloadChunk(" + x + "," + z + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.unloadChunk(x,z, true));
        return true; // Non-compliant
    }
    @Override public boolean unloadChunk(int x, int z, boolean save) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.unloadChunk(" + x + "," + z + ", " + save + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.unloadChunk(x,z, save));
        return true; // Non-compliant
    }
    @Override public boolean unloadChunkRequest(int x, int z) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.unloadChunkRequest(" + x + "," + z + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.unloadChunkRequest(x,z));
        return true; // Non-compliant
    }
    @Override public boolean regenerateChunk(int x, int z) {
        LOGGER.info("[COMPAT LAYER] WrappedWorld.regenerateChunk(" + x + "," + z + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.regenerateChunk(x,z));
        return true; // Non-compliant
    }
    @Override public boolean refreshChunk(int x, int z) {
        // This typically sends client updates, might need special handling or be region-local
        LOGGER.info("[COMPAT LAYER] WrappedWorld.refreshChunk(" + x + "," + z + ") called. Delegating.");
        Location loc = new Location(this, x << 4, 64, z << 4);
        taskDelegator.scheduleForLocation(loc, () -> realWorld.refreshChunk(x,z));
        return true; // Non-compliant
    }
    @Override public boolean isClearWeather() { return realWorld.isClearWeather(); }
    @Override public void setClearWeatherDuration(int duration) { realWorld.setClearWeatherDuration(duration); } // Global state / deleg.
    @Override public boolean hasStorm() { return realWorld.hasStorm(); }
    @Override public void setStorm(boolean hasStorm) { realWorld.setStorm(hasStorm); } // Global / deleg.
    @Override public int getWeatherDuration() { return realWorld.getWeatherDuration(); }
    @Override public void setWeatherDuration(int duration) { realWorld.setWeatherDuration(duration); } // Global / deleg.
    @Override public boolean isThundering() { return realWorld.isThundering(); }
    @Override public void setThundering(boolean thundering) { realWorld.setThundering(thundering); } // Global / deleg.
    @Override public int getThunderDuration() { return realWorld.getThunderDuration(); }
    @Override public void setThunderDuration(int duration) { realWorld.setThunderDuration(duration); } // Global / deleg.
    @Override public boolean createExplosion(Location loc, float power) { return realWorld.createExplosion(loc,power); } // Deleg.
    @Override public boolean createExplosion(Location loc, float power, boolean setFire) { return realWorld.createExplosion(loc,power,setFire); } // Deleg.
    @Override public boolean createExplosion(Location loc, float power, boolean setFire, boolean breakBlocks) { return realWorld.createExplosion(loc,power,setFire,breakBlocks); } // Deleg.
    @Override public boolean createExplosion(double x, double y, double z, float power, boolean setFire) { return realWorld.createExplosion(x,y,z,power,setFire); } // Deleg.
    @Override public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks) { return realWorld.createExplosion(x,y,z,power,setFire,breakBlocks); } // Deleg.
    @Override public Item dropItem(Location location, ItemStack item) { return unsupported("dropItem"); } // Deleg. + Wrap
    @Override public Item dropItemNaturally(Location location, ItemStack item) { return unsupported("dropItemNaturally"); } // Deleg. + Wrap
    @Override public Arrow spawnArrow(Location location, Vector direction, float speed, float spread) { return unsupported("spawnArrow"); } // Deleg. + Wrap
    @Override public <T extends AbstractArrow> T spawnArrow(Location location, Vector direction, float speed, float spread, Class<T> clazz) { return unsupported("spawnArrow"); } // Deleg. + Wrap
    @Override public boolean generateTree(Location location, TreeType type) { return realWorld.generateTree(location,type); } // Deleg.
    @Override public boolean generateTree(Location loc, TreeType type, BlockChangeDelegate delegate) { return realWorld.generateTree(loc,type,delegate); } // Deleg.
    @Override public Entity spawnEntity(Location loc, EntityType type) { return unsupported("spawnEntity"); } // Deleg. + Wrap
    @Override public LightningStrike strikeLightning(Location loc) { return unsupported("strikeLightning"); } // Deleg. + Wrap
    @Override public LightningStrike strikeLightningEffect(Location loc) { return unsupported("strikeLightningEffect"); } // Deleg. + Wrap
    @Override public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) { return unsupported("getNearbyEntities"); } // Snapshot + Wrap + Deleg. for query
    @Override public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z, Predicate<Entity> filter) { return unsupported("getNearbyEntities"); }
    @Override public Collection<Entity> getNearbyEntities(BoundingBox boundingBox) { return unsupported("getNearbyEntities"); }
    @Override public Collection<Entity> getNearbyEntities(BoundingBox boundingBox, Predicate<Entity> filter) { return unsupported("getNearbyEntities"); }
    @Override public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance) { return unsupported("rayTraceEntities"); }
    @Override public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, double raySize){ return unsupported("rayTraceEntities"); }
    @Override public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, Predicate<Entity> filter){ return unsupported("rayTraceEntities"); }
    @Override public RayTraceResult rayTraceEntities(Location start, Vector direction, double maxDistance, double raySize, Predicate<Entity> filter){ return unsupported("rayTraceEntities"); }
    @Override public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance){ return unsupported("rayTraceBlocks"); }
    @Override public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode){ return unsupported("rayTraceBlocks"); }
    @Override public RayTraceResult rayTraceBlocks(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks){ return unsupported("rayTraceBlocks"); }
    @Override public RayTraceResult rayTrace(Location start, Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, Predicate<Entity> filter){ return unsupported("rayTrace"); }
    @Override public String getGameRuleValue(String rule) { return realWorld.getGameRuleValue(rule); } // Global
    @Override public boolean setGameRuleValue(String rule, String value) { return realWorld.setGameRuleValue(rule,value); } // Global / Deleg.
    @Override public String[] getGameRules() { return realWorld.getGameRules(); } // Global
    @Override public boolean isGameRule(String rule) { return realWorld.isGameRule(rule); } // Global
    @Override public void setMetadata(String metadataKey, MetadataValue newMetadataValue) { realWorld.setMetadata(metadataKey, newMetadataValue); } // Global / Deleg for world meta
    @Override public List<MetadataValue> getMetadata(String metadataKey) { return realWorld.getMetadata(metadataKey); } // Global
    @Override public boolean hasMetadata(String metadataKey) { return realWorld.hasMetadata(metadataKey); } // Global
    @Override public void removeMetadata(String metadataKey, Plugin owningPlugin) { realWorld.removeMetadata(metadataKey, owningPlugin); } // Global
    @Override public int getViewDistance() { return realWorld.getViewDistance(); }
    @Override public int getSimulationDistance() { return realWorld.getSimulationDistance(); }
    @Override public Spigot spigot() { return realWorld.spigot(); } // Oh boy, Spigot().playEffect and such. Needs deep wrapping.
    @Override public Biome getBiome(int x, int z) { return realWorld.getBiome(x,z); }
    @Override public void setBiome(int x, int z, Biome bio) { taskDelegator.scheduleForLocation(new Location(this,x,0,z), ()->realWorld.setBiome(x,z,bio));}
    @Override public BlockData getBlockData(int x, int y, int z) { return realWorld.getBlockData(x,y,z); }
    @Override public void setBlockData(int x, int y, int z, BlockData blockData) { taskDelegator.scheduleForLocation(new Location(this,x,y,z), ()->realWorld.setBlockData(x,y,z,blockData));}
    @Override public Material getType(int x, int y, int z) { return realWorld.getType(x,y,z); }
    @Override public void setType(int x, int y, int z, Material material) { taskDelegator.scheduleForLocation(new Location(this,x,y,z), ()->realWorld.setType(x,y,z,material));}
    @Override public boolean generateTree(Location l, Random r, TreeType tt, Consumer<BlockState> c) { return realWorld.generateTree(l,r,tt,c); } // Deleg.
    @Override public boolean generateTree(Location l, Random r, TreeType tt, Predicate<BlockState> p, Consumer<BlockState> c) { return realWorld.generateTree(l,r,tt,p,c); } // Deleg.
    @Override public Sound getSound() { return realWorld.getSound(); } // Global
    @Override public Particle getParticle() { return realWorld.getParticle(); } // Global
    @Override public Difficulty getDifficulty() { return realWorld.getDifficulty(); } // Global
    @Override public void setDifficulty(Difficulty difficulty) { realWorld.setDifficulty(difficulty); } // Global / Deleg.
    @Override public int getSendViewDistance() { return realWorld.getSendViewDistance(); }
    @Override public WorldBorder getWorldBorder() { return realWorld.getWorldBorder(); } // Global / Needs wrapping
    @Override public void playSound(Location location, Sound sound, float volume, float pitch) { /* Needs careful handling for audibility across regions */ }
    @Override public void playSound(Location location, String sound, float volume, float pitch) { /* ... */ }
    @Override public void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch) { /* ... */ }
    @Override public void playSound(Location location, String sound, SoundCategory category, float volume, float pitch) { /* ... */ }
    @Override public void playEffect(Location location, Effect effect, int data) { /* ... */ }
    @Override public void playEffect(Location location, Effect effect, int data, int radius) { /* ... */ }
    @Override public <T> void playEffect(Location location, Effect effect, T data) { /* ... */ }
    @Override public <T> void playEffect(Location location, Effect effect, T data, int radius) { /* ... */ }
    @Override public <T> T spawnParticle(Particle particle, Location location, int count, T data) { return unsupported("spawnParticle"); }
    // ... Many more methods to implement ...
    @Override public void spawnParticle(Particle particle, Location location, int count) {}
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count) {}
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {}
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {}
    @Override public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) {}
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {}
    @Override public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {}
    @Override public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {}
    @Override public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {}
    @Override public void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {}
    @Override public CompletableFuture<Chunk> getChunkAtAsync(int i, int i1) {return unsupported("getChunkAtAsync");}
    @Override public CompletableFuture<Chunk> getChunkAtAsync(int i, int i1, boolean b) {return unsupported("getChunkAtAsync");}
    @Override public CompletableFuture<Chunk> getChunkAtAsync(Location location) {return unsupported("getChunkAtAsync");}
    @Override public CompletableFuture<Chunk> getChunkAtAsync(Location location, boolean b) {return unsupported("getChunkAtAsync");}
    @Override public boolean addPluginChunkTicket(int i, int i1, Plugin plugin) {return realWorld.addPluginChunkTicket(i,i1,plugin);} // Deleg.
    @Override public boolean removePluginChunkTicket(int i, int i1, Plugin plugin) {return realWorld.removePluginChunkTicket(i,i1,plugin);} // Deleg.
    @Override public void setPluginChunkTickets(int i, int i1, Set<Plugin> set) {realWorld.setPluginChunkTickets(i,i1,set);} // Deleg.
    @Override public Collection<Plugin> getPluginChunkTickets(int i, int i1) {return realWorld.getPluginChunkTickets(i,i1);} // Deleg. for read consistency
    @Override public boolean isForceLoaded(int i, int i1) {return realWorld.isForceLoaded(i,i1);}
    @Override public void setForceLoaded(int i, int i1, boolean b) {realWorld.setForceLoaded(i,i1,b);} // Deleg.
    @Override public Collection<Location> getForceLoadedChunks() {return realWorld.getForceLoadedChunks();}
    @Override public Location locateNearestStructure(Location location, StructureType structureType, int i, boolean b) {return unsupported("locateNearestStructure");}
    @Override public Location locateNearestStructure(Location location, org.bukkit.StructureType structureType, int i, boolean b) {return unsupported("locateNearestStructure");}
    @Override public int getLogicalHeight() {return realWorld.getLogicalHeight();}
    @Override public boolean isDeepslateTarget(int i, int i1) {return realWorld.isDeepslateTarget(i,i1);}
    @Override public boolean popResourceBubble(Location location) {return realWorld.popResourceBubble(location);}// Deleg.

}
