package io.papermc.airforce.compat.wrappers.entity;

import io.papermc.airforce.compat.scheduler.RegionTaskDelegator;
import io.papermc.airforce.compat.wrappers.WrappedWorld;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class WrappedEntity implements Entity {
    private static final Logger LOGGER = Logger.getLogger(WrappedEntity.class.getName());

    protected final Entity realEntity;
    protected final RegionTaskDelegator taskDelegator;
    // No direct WrappedWorld field to avoid potential issues if an entity is moved between worlds
    // and the wrapper isn't updated. getWorld() will handle wrapping.

    public WrappedEntity(Entity realEntity, RegionTaskDelegator taskDelegator) {
        if (realEntity == null) throw new IllegalArgumentException("realEntity cannot be null");
        if (taskDelegator == null) throw new IllegalArgumentException("taskDelegator cannot be null");
        this.realEntity = realEntity;
        this.taskDelegator = taskDelegator;
    }

    protected WrappedWorld getWrappedWorld() {
        // Assuming RegionTaskDelegator might eventually provide a way to get the correct WrappedWorld
        // or that a central registry exists. For now, this is a simplified approach.
        // This is a placeholder, as direct world wrapping like this is tricky without a central
        // WrappedWorld cache or passing it through all instantiations.
        // A better approach would be if the object that gives you the entity (e.g. WrappedWorld.getEntities())
        // injects the correct WrappedWorld instance.
        // For this step, we'll assume that the World object obtained from the realEntity
        // will be wrapped by the caller if needed, or by a central mechanism.
        // The alternative is to pass WrappedWorld into constructor, but that makes cross-world teleports complex for the wrapper.
        return new WrappedWorld(realEntity.getWorld(), taskDelegator);
    }


    @Override
    public Location getLocation() {
        // Read operation. For now, direct.
        // Could be taskDelegator.callForEntity(realEntity, () -> realEntity.getLocation().clone())
        // if we need to ensure snapshotting from the entity's thread.
        return realEntity.getLocation();
    }

    @Override
    public Location getLocation(Location loc) {
        // Read operation.
        return realEntity.getLocation(loc);
    }

    @Override
    public World getWorld() {
        // This ensures that the world is also wrapped.
        return getWrappedWorld();
    }

    @Override
    public boolean teleport(Location location) {
        LOGGER.info("[COMPAT LAYER] WrappedEntity.teleport(Location) for " + getUniqueId() + " to " + location + ". Delegating to scheduler.");
        // Teleport returns boolean, so ideally, this should be a Callable and block.
        // For now, we'll make it async and return true, which is not strictly Bukkit-compliant.
        // A proper implementation would use callForEntity and wait on the Future.
        taskDelegator.scheduleForEntity(realEntity, () -> {
            LOGGER.info("[COMPAT LAYER] Executing realEntity.teleport(Location) on delegated thread for " + getUniqueId());
            realEntity.teleport(location);
        });
        return true; // Non-compliant: doesn't reflect actual teleport success/event cancellation
    }

    @Override
    public boolean teleport(Entity destination) {
        LOGGER.info("[COMPAT LAYER] WrappedEntity.teleport(Entity) for " + getUniqueId() + " to " + destination.getUniqueId() + ". Delegating to scheduler.");
        Entity realDestination = (destination instanceof WrappedEntity) ? ((WrappedEntity) destination).realEntity : destination;
        taskDelegator.scheduleForEntity(realEntity, () -> {
            LOGGER.info("[COMPAT LAYER] Executing realEntity.teleport(Entity) on delegated thread for " + getUniqueId());
            realEntity.teleport(realDestination);
        });
        return true; // Non-compliant
    }

    @Override
    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        LOGGER.info("[COMPAT LAYER] WrappedEntity.teleport(Location, Cause) for " + getUniqueId() + ". Delegating.");
        taskDelegator.scheduleForEntity(realEntity, () -> realEntity.teleport(location, cause));
        return true; // Non-compliant
    }

    @Override
    public boolean teleport(Entity destination, PlayerTeleportEvent.TeleportCause cause) {
        LOGGER.info("[COMPAT LAYER] WrappedEntity.teleport(Entity, Cause) for " + getUniqueId() + ". Delegating.");
        Entity realDestination = (destination instanceof WrappedEntity) ? ((WrappedEntity) destination).realEntity : destination;
        taskDelegator.scheduleForEntity(realEntity, () -> realEntity.teleport(realDestination, cause));
        return true; // Non-compliant
    }

    @Override
    public EntityType getType() {
        return realEntity.getType();
    }

    @Override
    public UUID getUniqueId() {
        return realEntity.getUniqueId();
    }

    @Override
    public boolean isValid() {
        // This read is critical and might need to be callable from entity's thread if accessed cross-thread
        return realEntity.isValid();
    }

    // Direct delegations for most other read-only or less critical methods for now
    @Override public Vector getVelocity() { return realEntity.getVelocity(); }
    @Override public void setVelocity(Vector velocity) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setVelocity(velocity)); } // Write op
    @Override public double getHeight() { return realEntity.getHeight(); }
    @Override public double getWidth() { return realEntity.getWidth(); }
    @Override public BoundingBox getBoundingBox() { return realEntity.getBoundingBox(); }
    @Override public boolean isOnGround() { return realEntity.isOnGround(); }
    @Override public boolean isInWater() { return realEntity.isInWater(); }
    @Override public List<Entity> getNearbyEntities(double x, double y, double z) {
        // Needs to return List<WrappedEntity> and potentially delegate the query
        LOGGER.warning("WrappedEntity.getNearbyEntities - direct call, needs proper wrapping and delegation");
        return realEntity.getNearbyEntities(x,y,z);
    }
    @Override public int getEntityId() { return realEntity.getEntityId(); }
    @Override public int getFireTicks() { return realEntity.getFireTicks(); }
    @Override public int getMaxFireTicks() { return realEntity.getMaxFireTicks(); }
    @Override public void setFireTicks(int ticks) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setFireTicks(ticks));}
    @Override public void remove() { taskDelegator.scheduleForEntity(realEntity, realEntity::remove); }
    @Override public boolean isDead() { return realEntity.isDead(); }
    @Override public Server getServer() { return realEntity.getServer(); } // Server object itself isn't wrapped yet
    @Override public Entity getPassenger() { /* Needs wrapping */ return realEntity.getPassenger(); }
    @Override public boolean setPassenger(Entity passenger) { /* Needs delegation + unwrap */ return realEntity.setPassenger(passenger); }
    @Override public List<Entity> getPassengers() { /* Needs wrapping */ return realEntity.getPassengers(); }
    @Override public boolean addPassenger(Entity passenger) { /* Needs delegation + unwrap */ return realEntity.addPassenger(passenger); }
    @Override public boolean removePassenger(Entity passenger) { /* Needs delegation + unwrap */ return realEntity.removePassenger(passenger); }
    @Override public boolean isEmpty() { return realEntity.isEmpty(); }
    @Override public boolean eject() { /* Needs delegation */ return realEntity.eject(); }
    @Override public float getFallDistance() { return realEntity.getFallDistance(); }
    @Override public void setFallDistance(float distance) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setFallDistance(distance));}
    @Override public void setLastDamageCause(EntityDamageEvent event) { /* Needs delegation */ realEntity.setLastDamageCause(event); }
    @Override public EntityDamageEvent getLastDamageCause() { return realEntity.getLastDamageCause(); }
    @Override public void setTicksLived(int value) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setTicksLived(value));}
    @Override public int getTicksLived() { return realEntity.getTicksLived(); }
    @Override public void playEffect(EntityEffect type) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.playEffect(type));}
    @Override public void setCustomName(String name) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setCustomName(name));}
    @Override public String getCustomName() { return realEntity.getCustomName(); }
    @Override public void setCustomNameVisible(boolean flag) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setCustomNameVisible(flag));}
    @Override public boolean isCustomNameVisible() { return realEntity.isCustomNameVisible(); }
    @Override public void setGlowing(boolean flag) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setGlowing(flag));}
    @Override public boolean isGlowing() { return realEntity.isGlowing(); }
    @Override public void setInvulnerable(boolean flag) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setInvulnerable(flag));}
    @Override public boolean isInvulnerable() { return realEntity.isInvulnerable(); }
    @Override public boolean isSilent() { return realEntity.isSilent(); }
    @Override public void setSilent(boolean flag) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setSilent(flag));}
    @Override public boolean hasGravity() { return realEntity.hasGravity(); }
    @Override public void setGravity(boolean gravity) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setGravity(gravity));}
    @Override public int getPortalCooldown() { return realEntity.getPortalCooldown(); }
    @Override public void setPortalCooldown(int cooldown) { taskDelegator.scheduleForEntity(realEntity, () -> realEntity.setPortalCooldown(cooldown));}
    @Override public Set<String> getScoreboardTags() { return realEntity.getScoreboardTags(); } // Read, potentially snapshot
    @Override public boolean addScoreboardTag(String tag) { /* Needs delegation */ return realEntity.addScoreboardTag(tag); }
    @Override public boolean removeScoreboardTag(String tag) { /* Needs delegation */ return realEntity.removeScoreboardTag(tag); }
    @Override public PistonMoveReaction getPistonMoveReaction() { return realEntity.getPistonMoveReaction(); }
    @Override public BlockFace getFacing() { return realEntity.getFacing(); }
    @Override public Pose getPose() { return realEntity.getPose(); }
    @Override public Spigot spigot() { return realEntity.spigot(); } // Needs deep wrapping for its methods

    // Metadata methods - potentially global, or need careful handling
    @Override public void setMetadata(String metadataKey, MetadataValue newMetadataValue) { realEntity.setMetadata(metadataKey, newMetadataValue); }
    @Override public List<MetadataValue> getMetadata(String metadataKey) { return realEntity.getMetadata(metadataKey); }
    @Override public boolean hasMetadata(String metadataKey) { return realEntity.hasMetadata(metadataKey); }
    @Override public void removeMetadata(String metadataKey, Plugin owningPlugin) { realEntity.removeMetadata(metadataKey, owningPlugin); }

    // Permissions - generally operate on a global permissions plugin
    @Override public boolean isPermissionSet(String name) { return realEntity.isPermissionSet(name); }
    @Override public boolean isPermissionSet(Permission perm) { return realEntity.isPermissionSet(perm); }
    @Override public boolean hasPermission(String name) { return realEntity.hasPermission(name); }
    @Override public boolean hasPermission(Permission perm) { return realEntity.hasPermission(perm); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return realEntity.addAttachment(plugin, name, value); }
    @Override public PermissionAttachment addAttachment(Plugin plugin) { return realEntity.addAttachment(plugin); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return realEntity.addAttachment(plugin, name, value, ticks); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return realEntity.addAttachment(plugin, ticks); }
    @Override public void removeAttachment(PermissionAttachment attachment) { realEntity.removeAttachment(attachment); }
    @Override public void recalculatePermissions() { realEntity.recalculatePermissions(); }
    @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return realEntity.getEffectivePermissions(); }
    @Override public boolean isOp() { return realEntity.isOp(); }
    @Override public void setOp(boolean value) { realEntity.setOp(value); } // Global op state
    @Override public String getName() { return realEntity.getName();} // For players, etc.
    @Override public PersistentDataContainer getPersistentDataContainer() { return realEntity.getPersistentDataContainer(); } // Needs careful handling for thread safety

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof WrappedEntity) {
            return this.realEntity.equals(((WrappedEntity) obj).realEntity);
        }
        // Allow comparison with unwrapped entity if it happens to be passed
        if (obj instanceof Entity) {
            return this.realEntity.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return realEntity.hashCode();
    }
}
