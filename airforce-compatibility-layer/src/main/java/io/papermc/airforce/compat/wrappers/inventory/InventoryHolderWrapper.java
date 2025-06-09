package io.papermc.airforce.compat.wrappers.inventory;

import io.papermc.airforce.compat.wrappers.entity.WrappedPlayer;
import org.bukkit.Location;
import org.bukkit.inventory.InventoryHolder;

/**
 * Represents a wrapper for an InventoryHolder, providing context for task delegation.
 */
public interface InventoryHolderWrapper extends InventoryHolder { // Extend Bukkit's InventoryHolder for convenience

    /**
     * Gets the underlying real Bukkit InventoryHolder.
     *
     * @return The real InventoryHolder.
     */
    InventoryHolder getRealHolder();

    /**
     * Checks if the holder is a player.
     *
     * @return True if the holder is a player, false otherwise.
     */
    boolean isPlayerHolder();

    /**
     * Gets this holder as a WrappedPlayer, if it is one.
     *
     * @return This instance cast to WrappedPlayer, or null if not a player holder.
     * @throws ClassCastException if called when isPlayerHolder() is false.
     */
    WrappedPlayer getAsPlayerHolder();

    /**
     * Gets the location of this inventory holder, if it's location-based (e.g., a block).
     *
     * @return The Location, or null if not applicable (e.g., for a player's main inventory if not tied to a specific block).
     */
    Location getLocationForDelegation(); // Might be null for some holders like virtual inventories
}
