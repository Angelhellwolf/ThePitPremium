package cn.charlotte.pit.enchantment.runnable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WellAnimationLocationTest {
    @Test public void fakeGlassUsesTheLayerUnderTheActualTableInsteadOfTheDisplayAnchor() {
        World world = mock(World.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            Block block = mock(Block.class);
            when(block.getLocation()).thenReturn(new Location(world, x, y, z));
            when(block.getType()).thenReturn(x == 14 && y == 107 && z == 0 ? Material.ENCHANTMENT_TABLE : Material.AIR);
            return block;
        });
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location at = call.getArgument(0); return world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        });
        Location table = AnimationRunnable.resolveTable(new Location(world, 14, 108, 0));
        assertEquals(107, table.getBlockY());
        List<Location> ring = AnimationRunnable.ringBelow(table);
        assertEquals(8, ring.size()); assertEquals(8, new HashSet<>(ring).size());
        for (Location at : ring) assertEquals(106, at.getBlockY());
    }
    @Test public void privateTableAnimationUsesTheLibraryTableLayer() {
        List<Location> ring = AnimationRunnable.ringBelow(new Location(mock(World.class), 18, 72, 13));
        for (Location at : ring) {
            assertEquals(71, at.getBlockY());
            assertTrue(at.getBlockZ() >= 12 && at.getBlockZ() <= 14);
        }
    }
}
