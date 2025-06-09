package io.papermc.folia.compat.wrappers.entity;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import io.papermc.folia.compat.wrappers.WrappedWorld;
import io.papermc.folia.compat.wrappers.inventory.InventoryHolderWrapper;
import io.papermc.folia.compat.wrappers.inventory.WrappedInventory;
import io.papermc.folia.compat.wrappers.inventory.WrappedPlayerInventory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WrappedPlayerTest {

    private Player mockRealPlayer;
    private RegionTaskDelegator mockTaskDelegator;
    private WrappedPlayer wrappedPlayer;
    private WrappedWorld mockWrappedWorld; // For when player.getWorld() is called

    @BeforeEach
    void setUp() {
        mockRealPlayer = mock(Player.class);
        mockTaskDelegator = mock(RegionTaskDelegator.class);
        mockWrappedWorld = mock(WrappedWorld.class); // This is our wrapper

        // Stub basic methods for the real player
        when(mockRealPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mockRealPlayer.getName()).thenReturn("TestPlayer");

        // Mock player's world and location
        World bukkitWorld = mock(World.class); // Bukkit world
        when(bukkitWorld.getName()).thenReturn("testworld");
        Location playerLocation = new Location(bukkitWorld, 5, 6, 7);
        when(mockRealPlayer.getWorld()).thenReturn(bukkitWorld);
        when(mockRealPlayer.getLocation()).thenReturn(playerLocation);

        // If WrappedPlayer tries to create a WrappedWorld for its real world
        when(mockWrappedWorld.getTaskDelegator()).thenReturn(mockTaskDelegator); // for WrappedEntity's getWorld()

        wrappedPlayer = new WrappedPlayer(mockRealPlayer, mockTaskDelegator);
    }

    @Test
    void teleport_delegatesToScheduler() {
        Location targetLocation = new Location(mockRealPlayer.getWorld(), 10, 11, 12);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Player> entityCaptor = ArgumentCaptor.forClass(Player.class);

        wrappedPlayer.teleport(targetLocation);

        // Verify scheduleForEntity was called
        verify(mockTaskDelegator).scheduleForEntity(entityCaptor.capture(), runnableCaptor.capture());
        assertSame(mockRealPlayer, entityCaptor.getValue(), "Task should be scheduled for the real player entity");

        // Execute the captured runnable
        Runnable capturedRunnable = runnableCaptor.getValue();
        assertNotNull(capturedRunnable);
        capturedRunnable.run();

        // Verify that the real player's teleport method was called
        verify(mockRealPlayer).teleport(targetLocation);
    }

    @Test
    void getName_delegatesToRealPlayer() {
        assertEquals("TestPlayer", wrappedPlayer.getName());
        verify(mockRealPlayer).getName();
    }

    @Test
    void getUniqueId_delegatesToRealPlayer() {
        assertEquals(mockRealPlayer.getUniqueId(), wrappedPlayer.getUniqueId());
        verify(mockRealPlayer).getUniqueId();
    }

    @Test
    void getWorld_returnsWrappedWorld() {
        // This relies on WrappedEntity.getWorld() which creates a new WrappedWorld.
        // To test this properly, we'd need to ensure that the WrappedWorld constructor
        // inside WrappedEntity.getWorld() gets the same mockTaskDelegator.
        // Or, if WrappedPlayer took WrappedWorld in its constructor, that would be easier.
        // Given current WrappedEntity.getWorld() implementation:
        // `return new WrappedWorld(realEntity.getWorld(), taskDelegator);`
        // This will create a new WrappedWorld every time.

        World world = wrappedPlayer.getWorld();
        assertNotNull(world);
        assertTrue(world instanceof WrappedWorld, "Player.getWorld() should return a WrappedWorld");
        // We can't assertSame with mockWrappedWorld here because WrappedEntity creates a new one.
        // We can verify its properties if needed, or that it has the correct taskDelegator.
        assertEquals(mockRealPlayer.getWorld().getName(), world.getName());
    }

    @Test
    void getInventory_returnsWrappedPlayerInventoryAndImplementsInventoryHolderWrapper() {
        PlayerInventory mockRealPlayerInventory = mock(PlayerInventory.class);
        when(mockRealPlayer.getInventory()).thenReturn(mockRealPlayerInventory);

        Inventory inventory = wrappedPlayer.getInventory();
        assertNotNull(inventory);
        assertTrue(inventory instanceof WrappedPlayerInventory, "getInventory() should return WrappedPlayerInventory");
        assertTrue(inventory instanceof InventoryHolderWrapper, "WrappedPlayerInventory should be an InventoryHolderWrapper (implicitly via WrappedPlayer holder)");

        InventoryHolderWrapper holderWrapper = (InventoryHolderWrapper) wrappedPlayer; // Player itself is the holder wrapper
        assertSame(mockRealPlayer, holderWrapper.getRealHolder(), "Real holder should be the real player");
        assertTrue(holderWrapper.isPlayerHolder());
        assertSame(wrappedPlayer, holderWrapper.getAsPlayerHolder());
        assertNull(holderWrapper.getLocationForDelegation(), "Player holder should have null location for delegation (uses entity context)");

        WrappedPlayerInventory wrappedInv = (WrappedPlayerInventory) inventory;
        assertSame(holderWrapper, wrappedInv.getHolder(), "WrappedInventory's holder should be the WrappedPlayer");
    }

     @Test
    void getEnderChest_returnsWrappedInventory() {
        Inventory mockRealEnderChest = mock(Inventory.class);
        when(mockRealPlayer.getEnderChest()).thenReturn(mockRealEnderChest);

        Inventory enderChest = wrappedPlayer.getEnderChest();
        assertNotNull(enderChest);
        assertTrue(enderChest instanceof WrappedInventory, "getEnderChest() should return WrappedInventory");
        assertFalse(enderChest instanceof WrappedPlayerInventory, "EnderChest is generic WrappedInventory, not PlayerInventory type");

        InventoryHolderWrapper holderWrapper = (InventoryHolderWrapper) wrappedPlayer;
        WrappedInventory wrappedEnderChest = (WrappedInventory) enderChest;
        assertSame(holderWrapper, wrappedEnderChest.getHolder(), "WrappedInventory's holder for EnderChest should be the WrappedPlayer");
    }

    @Test
    void getRealEntity_returnsRealPlayer() {
        assertSame(mockRealPlayer, wrappedPlayer.getRealEntity());
    }
}
