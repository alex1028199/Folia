package io.papermc.airforce.compat.wrappers;

import io.papermc.airforce.compat.scheduler.RegionTaskDelegator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class WrappedBlockTest {

    private Block mockRealBlock;
    private RegionTaskDelegator mockTaskDelegator;
    private WrappedWorld mockWrappedWorld;
    private WrappedBlock wrappedBlock;
    private Location blockLocation;

    @BeforeEach
    void setUp() {
        mockRealBlock = mock(Block.class);
        mockTaskDelegator = mock(RegionTaskDelegator.class);
        mockWrappedWorld = mock(WrappedWorld.class); // WrappedWorld itself

        // Mock the location of the block
        World bukkitWorld = mock(World.class); // Bukkit world, not WrappedWorld
        when(bukkitWorld.getName()).thenReturn("testworld");
        blockLocation = new Location(bukkitWorld, 10, 20, 30);
        when(mockRealBlock.getLocation()).thenReturn(blockLocation);
        when(mockRealBlock.getWorld()).thenReturn(bukkitWorld); // Real block returns real world

        // When WrappedWorld is asked for the task delegator (e.g. by WrappedBlock constructor if it needed it that way)
        // or when WrappedBlock needs to get its WrappedWorld to return it.
        when(mockWrappedWorld.getTaskDelegator()).thenReturn(mockTaskDelegator);
        when(mockWrappedWorld.getRealWorld()).thenReturn(bukkitWorld); // If WrappedBlock tries to get real world via WrappedWorld
        when(mockWrappedWorld.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
            // Simplistic mock: if it asks for its own location, return a new WrappedBlock around the same real block
            Location requestedLoc = invocation.getArgument(0);
            if (requestedLoc.equals(blockLocation)) {
                return new WrappedBlock(mockRealBlock, mockTaskDelegator, mockWrappedWorld);
            }
            // For other locations, it would typically involve more complex world state.
            // For this test, we only care about the block under test.
            Block newMockBlock = mock(Block.class);
            when(newMockBlock.getLocation()).thenReturn(requestedLoc);
            when(newMockBlock.getWorld()).thenReturn(bukkitWorld);
            return new WrappedBlock(newMockBlock, mockTaskDelegator, mockWrappedWorld);
        });


        wrappedBlock = new WrappedBlock(mockRealBlock, mockTaskDelegator, mockWrappedWorld);
    }

    @Test
    void getType_delegatesToRealBlock() {
        when(mockRealBlock.getType()).thenReturn(Material.DIRT);
        Material type = wrappedBlock.getType();
        assertEquals(Material.DIRT, type);
        verify(mockRealBlock).getType();
    }


    @Test
    void setType_delegatesToScheduler() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);

        wrappedBlock.setType(Material.STONE);

        // Verify scheduleForLocation was called on the task delegator
        verify(mockTaskDelegator).scheduleForLocation(locationCaptor.capture(), runnableCaptor.capture());

        // Check the location passed to the delegator
        assertEquals(blockLocation, locationCaptor.getValue(), "Location passed to delegator should be the block's location");

        // Execute the captured runnable
        Runnable capturedRunnable = runnableCaptor.getValue();
        assertNotNull(capturedRunnable, "Runnable should not be null");
        capturedRunnable.run();

        // Verify that the real block's setType method was called within the runnable
        verify(mockRealBlock).setType(Material.STONE, true); // Default applyPhysics is true
    }

    @Test
    void setType_withApplyPhysics_delegatesToScheduler() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);

        wrappedBlock.setType(Material.DIAMOND_BLOCK, false);

        verify(mockTaskDelegator).scheduleForLocation(locationCaptor.capture(), runnableCaptor.capture());
        assertEquals(blockLocation, locationCaptor.getValue());

        Runnable capturedRunnable = runnableCaptor.getValue();
        capturedRunnable.run();

        verify(mockRealBlock).setType(Material.DIAMOND_BLOCK, false);
    }

    @Test
    void getLocation_returnsCorrectLocation() {
        Location loc = wrappedBlock.getLocation();
        assertEquals(blockLocation, loc, "getLocation() should return the original block's location.");
        // Ensure it's a clone or the same immutable object if Location is immutable (it's not fully)
        // Bukkit's Location is mutable, so ideally it should be cloned by getLocation if it's to be safe.
        // The current WrappedBlock.getLocation() directly returns realBlock.getLocation().
        // For testing, this is fine if we know this. For safety, it should be cloned.
        // Let's assume for now the direct return is intended and test that.
        assertSame(blockLocation, loc, "getLocation() for this basic test returns same instance from real block.");
    }

    @Test
    void getWorld_returnsWrappedWorld() {
        World world = wrappedBlock.getWorld();
        assertNotNull(world, "World should not be null");
        assertTrue(world instanceof WrappedWorld, "getWorld() should return an instance of WrappedWorld");
        assertSame(mockWrappedWorld, world, "getWorld() should return the same WrappedWorld instance it was constructed with.");
    }
}
