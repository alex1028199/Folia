package io.papermc.airforce.compat.wrappers.entity;

import io.papermc.airforce.compat.scheduler.RegionTaskDelegator;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class WrappedLivingEntity extends WrappedEntity implements LivingEntity {
    private static final Logger LOGGER = Logger.getLogger(WrappedLivingEntity.class.getName());

    protected final LivingEntity realLivingEntity;

    public WrappedLivingEntity(LivingEntity realEntity, RegionTaskDelegator taskDelegator) {
        super(realEntity, taskDelegator);
        this.realLivingEntity = realEntity;
    }

    @Override
    public double getHealth() {
        // Read op, could be callable for strict consistency from other threads
        return realLivingEntity.getHealth();
    }

    @Override
    public void setHealth(double health) {
        LOGGER.info("[COMPAT LAYER] WrappedLivingEntity.setHealth for " + getUniqueId() + ". Delegating.");
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setHealth(health));
    }

    @Override
    public double getAbsorptionAmount() {
        return realLivingEntity.getAbsorptionAmount();
    }

    @Override
    public void setAbsorptionAmount(double amount) {
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setAbsorptionAmount(amount));
    }

    @Override
    public double getMaxHealth() {
        // Usually from attributes, potentially safe to read directly
        return realLivingEntity.getMaxHealth();
    }

    @Override
    public void setMaxHealth(double health) {
        // Modifies attributes, should be delegated
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setMaxHealth(health));
    }

    @Override
    public void resetMaxHealth() {
        taskDelegator.scheduleForEntity(realLivingEntity, realLivingEntity::resetMaxHealth);
    }

    @Override
    public EntityEquipment getEquipment() {
        // EntityEquipment itself needs deep wrapping for its methods (getItem, setItem etc.)
        // For now, returning real one with a warning.
        LOGGER.warning("WrappedLivingEntity.getEquipment - returning real equipment, needs deep wrapping.");
        return realLivingEntity.getEquipment();
    }

    @Override
    public boolean addPotionEffect(PotionEffect effect) {
        // This returns boolean, so should be a blocking call if strict compatibility is needed.
        // Simplified for now.
        LOGGER.info("[COMPAT LAYER] WrappedLivingEntity.addPotionEffect for " + getUniqueId() + ". Delegating.");
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.addPotionEffect(effect));
        return true; // Non-compliant: doesn't reflect actual success
    }

    @Override
    public boolean addPotionEffect(PotionEffect effect, boolean force) {
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.addPotionEffect(effect, force));
        return true; // Non-compliant
    }

    @Override
    public boolean addPotionEffects(Collection<PotionEffect> effects) {
        taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.addPotionEffects(effects));
        return true; // Non-compliant
    }

    // Other LivingEntity methods - direct delegation or TODOs for now
    @Override public boolean hasPotionEffect(PotionEffectType type) { return realLivingEntity.hasPotionEffect(type); }
    @Override public PotionEffect getPotionEffect(PotionEffectType type) { return realLivingEntity.getPotionEffect(type); }
    @Override public void removePotionEffect(PotionEffectType type) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.removePotionEffect(type)); }
    @Override public Collection<PotionEffect> getActivePotionEffects() { return realLivingEntity.getActivePotionEffects(); } // Snapshot needed
    @Override public boolean getRemoveWhenFarAway() { return realLivingEntity.getRemoveWhenFarAway(); }
    @Override public void setRemoveWhenFarAway(boolean remove) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setRemoveWhenFarAway(remove)); }
    @Override public boolean canPickupItems() { return realLivingEntity.canPickupItems(); }
    @Override public void setCanPickupItems(boolean pickup) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setCanPickupItems(pickup)); }
    @Override public boolean isLeashed() { return realLivingEntity.isLeashed(); }
    @Override public Entity getLeashHolder() throws IllegalStateException { /* Needs wrapping */ return realLivingEntity.getLeashHolder(); }
    @Override public boolean setLeashHolder(Entity holder) { /* Needs delegation & unwrap */ return realLivingEntity.setLeashHolder(holder); }
    @Override public boolean isGliding() { return realLivingEntity.isGliding(); }
    @Override public void setGliding(boolean gliding) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setGliding(gliding)); }
    @Override public boolean isSwimming() { return realLivingEntity.isSwimming(); }
    @Override public void setSwimming(boolean swimming) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setSwimming(swimming)); }
    @Override public boolean isRiptiding() { return realLivingEntity.isRiptiding(); }
    @Override public boolean isSleeping() { return realLivingEntity.isSleeping(); }
    @Override public void setAI(boolean ai) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setAI(ai)); }
    @Override public boolean hasAI() { return realLivingEntity.hasAI(); }
    @Override public void attack(Entity target) { /* Needs delegation & unwrap */ realLivingEntity.attack(target); }
    @Override public void swingMainHand() { taskDelegator.scheduleForEntity(realLivingEntity, realLivingEntity::swingMainHand); }
    @Override public void swingOffHand() { taskDelegator.scheduleForEntity(realLivingEntity, realLivingEntity::swingOffHand); }
    @Override public void damage(double amount) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.damage(amount)); }
    @Override public void damage(double amount, Entity source) { /* Needs delegation & unwrap */ realLivingEntity.damage(amount, source); }
    @Override public void setLastDamage(double damage) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setLastDamage(damage)); }
    @Override public double getLastDamage() { return realLivingEntity.getLastDamage(); }
    @Override public int getNoDamageTicks() { return realLivingEntity.getNoDamageTicks(); }
    @Override public void setNoDamageTicks(int ticks) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setNoDamageTicks(ticks)); }
    @Override public Player getKiller() { /* Needs wrapping */ return realLivingEntity.getKiller(); }
    @Override public <T extends Projectile> T launchProjectile(Class<? extends T> projectile) { /* Needs delegation & wrapping */ return realLivingEntity.launchProjectile(projectile); }
    @Override public <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity) { /* Needs delegation & wrapping */ return realLivingEntity.launchProjectile(projectile, velocity); }
    @Override public int getRemainingAir() { return realLivingEntity.getRemainingAir(); }
    @Override public void setRemainingAir(int ticks) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setRemainingAir(ticks)); }
    @Override public int getMaximumAir() { return realLivingEntity.getMaximumAir(); }
    @Override public void setMaximumAir(int ticks) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setMaximumAir(ticks)); }
    @Override public int getArrowCooldown() { return realLivingEntity.getArrowCooldown(); }
    @Override public void setArrowCooldown(int ticks) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setArrowCooldown(ticks)); }
    @Override public int getArrowsInBody() { return realLivingEntity.getArrowsInBody(); }
    @Override public void setArrowsInBody(int count) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setArrowsInBody(count)); }
    @Override public int getMaximumNoDamageTicks() { return realLivingEntity.getMaximumNoDamageTicks(); }
    @Override public void setMaximumNoDamageTicks(int ticks) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setMaximumNoDamageTicks(ticks)); }
    @Override public AttributeInstance getAttribute(Attribute attribute) { return realLivingEntity.getAttribute(attribute); } // Attributes might need wrapping/snapshotting
    @Override public void setCollidable(boolean collidable) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setCollidable(collidable)); }
    @Override public boolean isCollidable() { return realLivingEntity.isCollidable(); }
    @Override public Set<UUID> getCollidableExemptions() { return realLivingEntity.getCollidableExemptions(); } // Snapshot
    @Override public <T> T getTargetEntity(int maxDistance, Class<T> targetType, boolean ignoreLOS, Predicate<Entity> predicate) { return null;}
    @Override public Entity getTargetEntity(int maxDistance, boolean ignoreLOS) { return null; } // Needs wrapping
    @Override public List<Block> getLastTwoTargetBlocks(Set<Material> transparent, int maxDistance) { return realLivingEntity.getLastTwoTargetBlocks(transparent, maxDistance); } // Snapshot, wrap blocks
    @Override public Block getTargetBlock(Set<Material> transparent, int maxDistance) { /* Wrap block */ return realLivingEntity.getTargetBlock(transparent, maxDistance); }
    @Override public Block getTargetBlockExact(int maxDistance, FluidCollisionMode fluidCollisionMode) { /* Wrap block */ return realLivingEntity.getTargetBlockExact(maxDistance, fluidCollisionMode); }
    @Override public RayTraceResult rayTraceBlocks(double maxDistance, FluidCollisionMode fluidCollisionMode) { return realLivingEntity.rayTraceBlocks(maxDistance, fluidCollisionMode); }
    @Override public boolean hasLineOfSight(Entity other) { /* Needs unwrap */ return realLivingEntity.hasLineOfSight(other); }
    @Override public boolean getMemory(org.bukkit.MemoryKey<Object> memoryKey) {return false;}
    @Override public <T> T getMemory(org.bukkit.MemoryKey<T> memoryKey) { return realLivingEntity.getMemory(memoryKey); }
    @Override public <T> void setMemory(org.bukkit.MemoryKey<T> memoryKey, T memoryValue) { taskDelegator.scheduleForEntity(realLivingEntity, () -> realLivingEntity.setMemory(memoryKey, memoryValue)); }
    @Override public org.bukkit.Sound getDeathSound() {return null;}
    @Override public org.bukkit.Sound getHurtSound() {return null;}
    @Override public org.bukkit.Sound getFallDamageSound(int i) {return null;}
    @Override public org.bukkit.Sound getFallDamageSoundSmall() {return null;}
    @Override public org.bukkit.Sound getFallDamageSoundBig() {return null;}
    @Override public org.bukkit.Sound getDrinkingSound(ItemStack itemStack) {return null;}
    @Override public org.bukkit.Sound getEatingSound(ItemStack itemStack) {return null;}
    @Override public boolean canBreatheUnderwater() {return false;}
    @Override public EntityType getKillerType() {return null;}
    @Override public void lookAt(Entity entity, float f, float f1) {}
    @Override public void lookAt(Location location, float f, float f1) {}
    @Override public void lookAt(double v, double v1, double v2, float f, float f1) {}
    @Override public int getArrowCount() {return 0;}
    @Override public void setArrowCount(int i) {}
    @Override public void setHandRaised(boolean b) {}
    @Override public boolean isHandRaised() {return false;}
    @Override public boolean isJumping() {return false;}
    @Override public void setJumping(boolean b) {}
    @Override public void playPickupItemAnimation(Item item, int i) {}
    @Override public float getHurtDirection() {return 0;}
    @Override public void setHurtDirection(float v) {}
    @Override public ItemStack getItemInUse() {return null;}
    @Override public int getItemInUseTicks() {return 0;}
    @Override public void setItemInUseTicks(int i) {}
    @Override public void clearActiveItem() {}
    @Override public void stopUsingItem() {}
    @Override public boolean isUsingItem() {return false;}
    @Override public void lookAt(Entity entity) {}
    @Override public void lookAt(Location location) {}
    @Override public void lookAt(double v, double v1, double v2) {}
    @Override public boolean isBlocking() {return false;}
    @Override public void setBlocking(boolean b) {}
    @Override public BlockData getBlockPlacementMaterial() {return null;}
    @Override public Location getEyeLocation() { return realLivingEntity.getEyeLocation(); }
}
