package io.papermc.folia.compat.wrappers.inventory;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WrappedInventory implements Inventory {
    private static final Logger LOGGER = Logger.getLogger(WrappedInventory.class.getName());

    private final Inventory realInventory;
    private final RegionTaskDelegator taskDelegator;
    private final InventoryHolderWrapper inventoryHolderWrapper;

    public WrappedInventory(Inventory realInventory, RegionTaskDelegator taskDelegator, InventoryHolderWrapper inventoryHolderWrapper) {
        if (realInventory == null) throw new IllegalArgumentException("realInventory cannot be null");
        if (taskDelegator == null) throw new IllegalArgumentException("taskDelegator cannot be null");
        if (inventoryHolderWrapper == null) throw new IllegalArgumentException("inventoryHolderWrapper (owner) cannot be null");

        this.realInventory = realInventory;
        this.taskDelegator = taskDelegator;
        this.inventoryHolderWrapper = inventoryHolderWrapper;
    }

    @Override
    public ItemStack getItem(int index) {
        // This is a read. For strict safety, especially if accessed from a foreign thread,
        // it should be a `callForInventoryHolder`. For now, direct access + clone.
        // This assumes that the `realInventory`'s internal state is being managed correctly
        // by Folia for any reads that might occur off-region-thread.
        ItemStack item = realInventory.getItem(index);
        return item != null ? item.clone() : null;
    }

    @Override
    public void setItem(int index, ItemStack item) {
        LOGGER.finer("[COMPAT LAYER] WrappedInventory.setItem for holder " + inventoryHolderWrapper + ". Delegating.");
        ItemStack clonedItem = (item != null) ? item.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> {
            LOGGER.finer("[COMPAT LAYER] Executing realInventory.setItem on delegated thread for holder " + inventoryHolderWrapper);
            realInventory.setItem(index, clonedItem);
        });
    }

    @Override
    public HashMap<Integer, ItemStack> addItem(ItemStack... items) throws IllegalArgumentException {
        if (items == null || items.length == 0) {
            return new HashMap<>();
        }
        LOGGER.finer("[COMPAT LAYER] WrappedInventory.addItem for holder " + inventoryHolderWrapper + ". Delegating with call.");
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) throw new IllegalArgumentException("Cannot add null ItemStack");
            clonedItems[i] = items[i].clone();
        }

        Future<HashMap<Integer, ItemStack>> future = taskDelegator.callForInventoryHolder(inventoryHolderWrapper, () -> {
            LOGGER.finer("[COMPAT LAYER] Executing realInventory.addItem on delegated thread for holder " + inventoryHolderWrapper);
            return realInventory.addItem(clonedItems);
        });

        try {
            HashMap<Integer, ItemStack> realLeftovers = future.get(); // This blocks, which is Bukkit API contract
            HashMap<Integer, ItemStack> clonedLeftovers = new HashMap<>();
            if (realLeftovers != null) {
                for (HashMap.Entry<Integer, ItemStack> entry : realLeftovers.entrySet()) {
                    clonedLeftovers.put(entry.getKey(), entry.getValue() != null ? entry.getValue().clone() : null);
                }
            }
            return clonedLeftovers;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "[COMPAT LAYER] Error executing addItem for holder " + inventoryHolderWrapper, e);
            // Return all items as leftovers if error occurs, cloning them
            HashMap<Integer, ItemStack> errorLeftovers = new HashMap<>();
            for (int i = 0; i < items.length; i++) {
                errorLeftovers.put(i, items[i].clone()); // Original items cloned
            }
            return errorLeftovers;
        }
    }

    @Override
    public InventoryHolder getHolder() {
        return this.inventoryHolderWrapper; // Return our wrapper
    }

    // Simple getters - direct delegation for now
    @Override public int getSize() { return realInventory.getSize(); }
    @Override public int getMaxStackSize() { return realInventory.getMaxStackSize(); }
    @Override public void setMaxStackSize(int size) { realInventory.setMaxStackSize(size); } // This might need delegation if it's not a simple field
    @Override public String getName() { return realInventory.getName(); } // Deprecated
    @Override public String getTitle() { return realInventory.getTitle(); } // Deprecated
    @Override public InventoryType getType() { return realInventory.getType(); }

    @Override
    public ItemStack[] getContents() {
        ItemStack[] realContents = realInventory.getContents();
        ItemStack[] clonedContents = new ItemStack[realContents.length];
        for (int i = 0; i < realContents.length; i++) {
            if (realContents[i] != null) {
                clonedContents[i] = realContents[i].clone();
            }
        }
        return clonedContents;
    }

    @Override
    public void setContents(ItemStack[] items) throws IllegalArgumentException {
        LOGGER.finer("[COMPAT LAYER] WrappedInventory.setContents for holder " + inventoryHolderWrapper + ". Delegating.");
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                clonedItems[i] = items[i].clone();
            }
        }
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.setContents(clonedItems));
    }

    @Override
    public ItemStack[] getStorageContents() {
        ItemStack[] realContents = realInventory.getStorageContents();
        ItemStack[] clonedContents = new ItemStack[realContents.length];
        for (int i = 0; i < realContents.length; i++) {
            if (realContents[i] != null) {
                clonedContents[i] = realContents[i].clone();
            }
        }
        return clonedContents;
    }

    @Override
    public void setStorageContents(ItemStack[] items) throws IllegalArgumentException {
        LOGGER.finer("[COMPAT LAYER] WrappedInventory.setStorageContents for holder " + inventoryHolderWrapper + ". Delegating.");
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                clonedItems[i] = items[i].clone();
            }
        }
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.setStorageContents(clonedItems));
    }


    // Other methods needing careful review or delegation
    @Override
    public HashMap<Integer, ItemStack> removeItem(ItemStack... items) throws IllegalArgumentException {
        // Similar to addItem, needs cloning and delegation with callForInventoryHolder
        LOGGER.warning("WrappedInventory.removeItem - using simplified delegation, may not return leftovers correctly.");
        ItemStack[] clonedItems = Arrays.stream(items).map(item -> item != null ? item.clone() : null).toArray(ItemStack[]::new);
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.removeItem(clonedItems));
        return new HashMap<>(); // Non-compliant
    }

    @Override
    public boolean contains(Material material) throws IllegalArgumentException { return realInventory.contains(material); } // Read, could be delegated
    @Override
    public boolean contains(ItemStack item) { return realInventory.contains(item != null ? item.clone() : null); } // Read, clone for safety
    @Override
    public boolean contains(Material material, int amount) throws IllegalArgumentException { return realInventory.contains(material, amount); }
    @Override
    public boolean contains(ItemStack item, int amount) { return realInventory.contains(item != null ? item.clone() : null, amount); }
    @Override
    public boolean containsAtLeast(ItemStack item, int amount) { return realInventory.containsAtLeast(item != null ? item.clone() : null, amount); }
    @Override
    public HashMap<Integer, ? extends ItemStack> all(Material material) throws IllegalArgumentException {
        // Read, clone results
        HashMap<Integer, ? extends ItemStack> realResult = realInventory.all(material);
        HashMap<Integer, ItemStack> clonedResult = new HashMap<>();
        realResult.forEach((key, value) -> clonedResult.put(key, value != null ? value.clone() : null));
        return clonedResult;
    }
    @Override
    public HashMap<Integer, ? extends ItemStack> all(ItemStack item) {
        HashMap<Integer, ? extends ItemStack> realResult = realInventory.all(item != null ? item.clone() : null);
        HashMap<Integer, ItemStack> clonedResult = new HashMap<>();
        realResult.forEach((key, value) -> clonedResult.put(key, value != null ? value.clone() : null));
        return clonedResult;
    }
    @Override
    public int first(Material material) throws IllegalArgumentException { return realInventory.first(material); }
    @Override
    public int first(ItemStack item) { return realInventory.first(item != null ? item.clone() : null); }
    @Override
    public int firstEmpty() { return realInventory.firstEmpty(); } // Read, likely safe if called on region thread via delegation
    @Override
    public boolean isEmpty() { return realInventory.isEmpty(); }

    @Override
    public void remove(Material material) throws IllegalArgumentException {
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.remove(material));
    }
    @Override
    public void remove(ItemStack item) {
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.remove(item != null ? item.clone() : null));
    }
    @Override
    public void clear(int index) {
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realInventory.clear(index));
    }
    @Override
    public void clear() {
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, realInventory::clear);
    }
    @Override
    public List<HumanEntity> getViewers() {
        // This list needs to be wrapped (List<WrappedHumanEntity>) and snapshotted.
        LOGGER.warning("WrappedInventory.getViewers - returning real list, needs wrapping and snapshotting.");
        return realInventory.getViewers();
    }
    @Override
    public ListIterator<ItemStack> iterator() {
        // Needs to iterate over clones, and be immutable or throw if modified.
        LOGGER.warning("WrappedInventory.iterator - returning iterator over real contents, needs cloning.");
        return getContentsSafe().listIterator(); // Iterate over cloned contents
    }
    @Override
    public ListIterator<ItemStack> iterator(int index) {
        LOGGER.warning("WrappedInventory.iterator(index) - returning iterator over real contents, needs cloning.");
        return getContentsSafe().listIterator(index); // Iterate over cloned contents
    }

    private List<ItemStack> getContentsSafe() {
        // Helper to get a modifiable list of clones for iterator
        ItemStack[] contents = getContents(); // This already returns clones
        return new ArrayList<>(Arrays.asList(contents));
    }

    @Override
    public Location getLocation() {
        // If the holder is location-based (e.g. a block), it should provide it.
        // Otherwise, this can be null (e.g. player inventory).
        return inventoryHolderWrapper.getLocationForDelegation();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof WrappedInventory) {
            return this.realInventory.equals(((WrappedInventory) obj).realInventory) &&
                   this.inventoryHolderWrapper.equals(((WrappedInventory) obj).inventoryHolderWrapper);
        }
        // Bukkit API allows comparing an inventory to its holder sometimes implicitly
        if (obj instanceof Inventory) {
             return this.realInventory.equals(obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return realInventory.hashCode() ^ inventoryHolderWrapper.hashCode();
    }
}
