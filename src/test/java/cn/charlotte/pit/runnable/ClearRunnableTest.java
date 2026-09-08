package cn.charlotte.pit.runnable;

import cn.charlotte.pit.data.sub.PlacedBlockData;
import cn.charlotte.pit.events.EventBlockTracker;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClearRunnableTest {
    @Test
    public void expiredPlacementCannotDeleteAnEventOverlayEvenWhenMaterialMatches() throws Exception {
        ClearRunnable clear = mock(ClearRunnable.class, CALLS_REAL_METHODS);
        EventBlockTracker events = mock(EventBlockTracker.class);
        World world = mock(World.class);
        Location location = new Location(world, 10, 64, 10);
        Block block = mock(Block.class);
        when(world.getBlockAt(location)).thenReturn(block);
        when(world.getBlockAt(10, 64, 10)).thenReturn(block);
        when(block.getType()).thenReturn(Material.OBSIDIAN);
        PlacedBlockData placed = new PlacedBlockData(location, new Cooldown(0), Material.OBSIDIAN, (byte) 0);
        when(events.isTracked(location)).thenReturn(true);
        try (MockedStatic<EventBlockTracker> tracked = mockStatic(EventBlockTracker.class)) {
            tracked.when(EventBlockTracker::get).thenReturn(events);
            assertFalse(clear.isTrackedPlacement(placed));
            Method cleanup = ClearRunnable.class.getDeclaredMethod("clearPlacedBlock", PlacedBlockData.class);
            cleanup.setAccessible(true);
            assertFalse((Boolean) cleanup.invoke(clear, placed));
            verify(block, never()).setType(any(Material.class), anyBoolean());
        }
    }

    @Test
    public void matchingIncludesWoolColorButAcceptsFlowingAndStationaryWater() {
        ClearRunnable clear = mock(ClearRunnable.class, CALLS_REAL_METHODS);
        EventBlockTracker events = mock(EventBlockTracker.class);
        World world = mock(World.class);
        Location location = new Location(world, 10, 64, 10);
        Block block = mock(Block.class);
        when(world.getBlockAt(location)).thenReturn(block);
        when(world.getBlockAt(10, 64, 10)).thenReturn(block);
        when(block.getType()).thenReturn(Material.WOOL);
        when(block.getData()).thenReturn((byte) 14);
        try (MockedStatic<EventBlockTracker> tracked = mockStatic(EventBlockTracker.class)) {
            tracked.when(EventBlockTracker::get).thenReturn(events);
            assertFalse(clear.isTrackedPlacement(new PlacedBlockData(location, new Cooldown(0), Material.WOOL, (byte) 0)));
            assertTrue(clear.isTrackedPlacement(new PlacedBlockData(location, new Cooldown(0), Material.WOOL, (byte) 14)));
            when(block.getType()).thenReturn(Material.STATIONARY_WATER);
            when(block.getData()).thenReturn((byte) 3);
            assertTrue(clear.isTrackedPlacement(new PlacedBlockData(location, new Cooldown(0), Material.WATER, (byte) 0)));
        }
    }
}
