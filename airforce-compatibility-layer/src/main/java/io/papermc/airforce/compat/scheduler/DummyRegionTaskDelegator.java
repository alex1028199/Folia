package io.papermc.airforce.compat.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class DummyRegionTaskDelegator implements RegionTaskDelegator {

    private static final Logger LOGGER = Logger.getLogger(DummyRegionTaskDelegator.class.getName());

    @Override
    public void scheduleForLocation(Location location, Runnable task) {
        String regionId = getRegionId(location);
        LOGGER.info("[COMPAT LAYER] Task WOULD BE SCHEDULED for location " + location + " (Region: " + regionId + ")");
        // For dummy implementation, run immediately
        try {
            task.run();
            LOGGER.info("[COMPAT LAYER] Dummy task for location " + location + " executed.");
        } catch (Exception e) {
            LOGGER.severe("[COMPAT LAYER] Error executing dummy task for location " + location + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void scheduleForEntity(Entity entity, Runnable task) {
        String regionId = getRegionId(entity);
        LOGGER.info("[COMPAT LAYER] Task WOULD BE SCHEDULED for entity " + entity.getUniqueId() + " (Region: " + regionId + ")");
        // For dummy implementation, run immediately
        try {
            task.run();
            LOGGER.info("[COMPAT LAYER] Dummy task for entity " + entity.getUniqueId() + " executed.");
        } catch (Exception e) {
            LOGGER.severe("[COMPAT LAYER] Error executing dummy task for entity " + entity.getUniqueId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public <T> Future<T> callForLocation(Location location, Callable<T> task) {
        String regionId = getRegionId(location);
        LOGGER.info("[COMPAT LAYER] Callable task WOULD BE CALLED for location " + location + " (Region: " + regionId + ")");
        // For dummy implementation, run immediately and return a completed Future
        try {
            T result = task.call();
            LOGGER.info("[COMPAT LAYER] Dummy callable for location " + location + " executed.");
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            LOGGER.severe("[COMPAT LAYER] Error executing dummy callable for location " + location + ": " + e.getMessage());
            e.printStackTrace();
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public <T> Future<T> callForEntity(Entity entity, Callable<T> task) {
        String regionId = getRegionId(entity);
        LOGGER.info("[COMPAT LAYER] Callable task WOULD BE CALLED for entity " + entity.getUniqueId() + " (Region: " + regionId + ")");
        // For dummy implementation, run immediately and return a completed Future
        try {
            T result = task.call();
            LOGGER.info("[COMPAT LAYER] Dummy callable for entity " + entity.getUniqueId() + " executed.");
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            LOGGER.severe("[COMPAT LAYER] Error executing dummy callable for entity " + entity.getUniqueId() + ": " + e.getMessage());
            e.printStackTrace();
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public String getRegionId(Location location) {
        if (location == null || location.getWorld() == null) {
            return "GLOBAL/UNKNOWN_WORLD";
        }
        // Super simple placeholder: derive from chunk coordinates
        return location.getWorld().getName() + "_cx" + (location.getBlockX() >> 4) + "_cz" + (location.getBlockZ() >> 4);
    }

    @Override
    public String getRegionId(Entity entity) {
        if (entity == null) {
            return "GLOBAL/NULL_ENTITY";
        }
        return getRegionId(entity.getLocation());
    }

    @Override
    public void scheduleForInventoryHolder(io.papermc.airforce.compat.wrappers.inventory.InventoryHolderWrapper holder, Runnable task) {
        if (holder.isPlayerHolder()) {
            io.papermc.airforce.compat.wrappers.entity.WrappedPlayer wrappedPlayer = holder.getAsPlayerHolder();
            scheduleForEntity((Entity) wrappedPlayer.getRealHolder(), task);
        } else {
            Location loc = holder.getLocationForDelegation();
            if (loc != null) {
                scheduleForLocation(loc, task);
            } else {
                LOGGER.warning("[COMPAT LAYER] InventoryHolder has no location for delegation, running task immediately/globally for: " + holder);
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.log(java.util.logging.Level.SEVERE, "Error executing immediate task for InventoryHolder: " + holder, e);
                }
            }
        }
    }

    @Override
    public <T> Future<T> callForInventoryHolder(io.papermc.airforce.compat.wrappers.inventory.InventoryHolderWrapper holder, Callable<T> task) {
        if (holder.isPlayerHolder()) {
            io.papermc.airforce.compat.wrappers.entity.WrappedPlayer wrappedPlayer = holder.getAsPlayerHolder();
            return callForEntity((Entity) wrappedPlayer.getRealHolder(), task);
        } else {
            Location loc = holder.getLocationForDelegation();
            if (loc != null) {
                return callForLocation(loc, task);
            } else {
                LOGGER.warning("[COMPAT LAYER] InventoryHolder has no location for delegation, calling task immediately/globally for: " + holder);
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception e) {
                    LOGGER.log(java.util.logging.Level.SEVERE, "Error executing immediate callable for InventoryHolder: " + holder, e);
                    CompletableFuture<T> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
        }
    }
}
