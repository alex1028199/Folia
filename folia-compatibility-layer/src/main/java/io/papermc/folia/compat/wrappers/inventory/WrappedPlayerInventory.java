package io.papermc.folia.compat.wrappers.inventory;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.logging.Logger;

public class WrappedPlayerInventory extends WrappedInventory implements PlayerInventory {
    private static final Logger LOGGER = Logger.getLogger(WrappedPlayerInventory.class.getName());

    private final PlayerInventory realPlayerInventory;
    // inventoryHolderWrapper is inherited from WrappedInventory and should be the WrappedPlayer instance

    public WrappedPlayerInventory(PlayerInventory realInventory, RegionTaskDelegator taskDelegator, InventoryHolderWrapper inventoryHolderWrapper) {
        super(realInventory, taskDelegator, inventoryHolderWrapper);
        this.realPlayerInventory = realInventory;
        if (!(inventoryHolderWrapper instanceof org.bukkit.entity.Player)) {
            throw new IllegalArgumentException("WrappedPlayerInventory must have a Player as its holder wrapper.");
        }
    }

    // Implement PlayerInventory methods by delegating to realPlayerInventory
    // For write operations, ensure they go through the delegation mechanism if they
    // aren't already covered by WrappedInventory's setItem/addItem.

    @Override
    public ItemStack[] getArmorContents() {
        // Read - clone results
        ItemStack[] realContents = realPlayerInventory.getArmorContents();
        ItemStack[] clonedContents = new ItemStack[realContents.length];
        for (int i = 0; i < realContents.length; i++) {
            if (realContents[i] != null) {
                clonedContents[i] = realContents[i].clone();
            }
        }
        return clonedContents;
    }

    @Override
    public ItemStack[] getExtraContents() {
        ItemStack[] realContents = realPlayerInventory.getExtraContents();
        ItemStack[] clonedContents = new ItemStack[realContents.length];
        for (int i = 0; i < realContents.length; i++) {
            if (realContents[i] != null) {
                clonedContents[i] = realContents[i].clone();
            }
        }
        return clonedContents;
    }


    @Override
    public ItemStack getHelmet() {
        ItemStack item = realPlayerInventory.getHelmet();
        return item != null ? item.clone() : null;
    }

    @Override
    public ItemStack getChestplate() {
        ItemStack item = realPlayerInventory.getChestplate();
        return item != null ? item.clone() : null;
    }

    @Override
    public ItemStack getLeggings() {
        ItemStack item = realPlayerInventory.getLeggings();
        return item != null ? item.clone() : null;
    }

    @Override
    public ItemStack getBoots() {
        ItemStack item = realPlayerInventory.getBoots();
        return item != null ? item.clone() : null;
    }

    @Override
    public void setArmorContents(ItemStack[] items) {
        LOGGER.finer("[COMPAT LAYER] WrappedPlayerInventory.setArmorContents. Delegating.");
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                clonedItems[i] = items[i].clone();
            }
        }
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setArmorContents(clonedItems));
    }

    @Override
    public void setExtraContents(ItemStack[] items) {
        LOGGER.finer("[COMPAT LAYER] WrappedPlayerInventory.setExtraContents. Delegating.");
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                clonedItems[i] = items[i].clone();
            }
        }
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setExtraContents(clonedItems));
    }

    @Override
    public void setHelmet(ItemStack helmet) {
        ItemStack cloned = helmet != null ? helmet.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setHelmet(cloned));
    }

    @Override
    public void setChestplate(ItemStack chestplate) {
        ItemStack cloned = chestplate != null ? chestplate.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setChestplate(cloned));
    }

    @Override
    public void setLeggings(ItemStack leggings) {
        ItemStack cloned = leggings != null ? leggings.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setLeggings(cloned));
    }

    @Override
    public void setBoots(ItemStack boots) {
        ItemStack cloned = boots != null ? boots.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setBoots(cloned));
    }

    @Override
    public ItemStack getItemInMainHand() {
        ItemStack item = realPlayerInventory.getItemInMainHand();
        return item != null ? item.clone() : null;
    }

    @Override
    public void setItemInMainHand(ItemStack item) {
        ItemStack cloned = item != null ? item.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItemInMainHand(cloned));
    }

    @Override
    public ItemStack getItemInOffHand() {
        ItemStack item = realPlayerInventory.getItemInOffHand();
        return item != null ? item.clone() : null;
    }

    @Override
    public void setItemInOffHand(ItemStack item) {
        ItemStack cloned = item != null ? item.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItemInOffHand(cloned));
    }

    @Override
    public ItemStack getItemInHand() { // Deprecated
        ItemStack item = realPlayerInventory.getItemInHand();
        return item != null ? item.clone() : null;
    }

    @Override
    public void setItemInHand(ItemStack stack) { // Deprecated
        ItemStack cloned = stack != null ? stack.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItemInHand(cloned));
    }

    @Override
    public int getHeldItemSlot() {
        return realPlayerInventory.getHeldItemSlot();
    }

    @Override
    public void setHeldItemSlot(int slot) {
        // This is a client-player state change, usually needs to be on their thread.
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setHeldItemSlot(slot));
    }


    @Override
    public HumanEntity getHolder() {
        // The holder of PlayerInventory is always a HumanEntity (Player)
        return (HumanEntity) inventoryHolderWrapper.getRealHolder();
    }

    // Other PlayerInventory specific methods can be added as needed.
    // Many might just delegate directly if they are simple reads or already handled by WrappedInventory.

    @Override public ItemStack getItem(EquipmentSlot slot) {
        ItemStack item = realPlayerInventory.getItem(slot);
        return item != null ? item.clone() : null;
    }
    @Override public void setItem(EquipmentSlot slot, ItemStack item) {
        ItemStack cloned = item != null ? item.clone() : null;
        taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItem(slot, cloned));
    }
    @Override public float getItemInHandDropChance() { return realPlayerInventory.getItemInHandDropChance(); } // Deprecated
    @Override public void setItemInHandDropChance(float chance) { /* Needs delegation */ } // Deprecated
    @Override public float getItemInMainHandDropChance() { return realPlayerInventory.getItemInMainHandDropChance(); }
    @Override public void setItemInMainHandDropChance(float chance) { taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItemInMainHandDropChance(chance));}
    @Override public float getItemInOffHandDropChance() { return realPlayerInventory.getItemInOffHandDropChance(); }
    @Override public void setItemInOffHandDropChance(float chance) { taskDelegator.scheduleForInventoryHolder(inventoryHolderWrapper, () -> realPlayerInventory.setItemInOffHandDropChance(chance));}

}
