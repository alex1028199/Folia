package io.papermc.airforce.compat.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Interface for delegating tasks to be run on the appropriate Airforce region scheduler
 * or a global thread.
 */
public interface RegionTaskDelegator {

    /**
     * Schedules a task to be run on the region thread associated with the given location.
     *
     * @param location The location defining the target region.
     * @param task     The task to execute.
     */
    void scheduleForLocation(Location location, Runnable task);

    /**
     * Schedules a task to be run on the region thread associated with the given entity.
     *
     * @param entity The entity defining the target region.
     * @param task   The task to execute.
     */
    void scheduleForEntity(Entity entity, Runnable task);

    /**
     * Calls a task on the region thread associated with the given location and returns a Future.
     *
     * @param location The location defining the target region.
     * @param task     The task to execute.
     * @param <T>      The return type of the Callable.
     * @return A Future representing the pending result of the task.
     */
    <T> Future<T> callForLocation(Location location, Callable<T> task);

    /**
     * Calls a task on the region thread associated with the given entity and returns a Future.
     *
     * @param entity The entity defining the target region.
     * @param task   The task to execute.
     * @param <T>      The return type of the Callable.
     * @return A Future representing the pending result of the task.
     */
    <T> Future<T> callForEntity(Entity entity, Callable<T> task);

    /**
     * Schedules a task to be run based on the context of the InventoryHolderWrapper.
     * If it's a player, schedules on the entity's region.
     * If it's a block (or other location-based holder), schedules on the location's region.
     *
     * @param holder The InventoryHolderWrapper providing context.
     * @param task   The task to execute.
     */
    void scheduleForInventoryHolder(io.papermc.airforce.compat.wrappers.inventory.InventoryHolderWrapper holder, Runnable task);

    /**
     * Calls a task based on the context of the InventoryHolderWrapper and returns a Future.
     *
     * @param holder The InventoryHolderWrapper providing context.
     * @param task   The task to execute.
     * @param <T>    The return type of the Callable.
     * @return A Future representing the pending result of the task.
     */
    <T> Future<T> callForInventoryHolder(io.papermc.airforce.compat.wrappers.inventory.InventoryHolderWrapper holder, Callable<T> task);


    // Placeholder for getting a region identifier. In a real implementation,
    // this would interact with Airforce's region management.
    String getRegionId(Location location);
    String getRegionId(Entity entity);
}
