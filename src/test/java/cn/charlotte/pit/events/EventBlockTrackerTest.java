package cn.charlotte.pit.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EventBlockTrackerTest {
    @Test
    public void signedCoordinatesRoundTripWithoutChangingAnotherAxis() throws Exception {
        Method pack = method("pack", int.class, int.class, int.class);
        Method unpack = method("unpack", long.class);
        for (int x : new int[]{-30000000, -16, -1, 0, 1, 16, 30000000}) {
            for (int z : new int[]{-30000000, -16, -1, 0, 1, 16, 30000000}) {
                assertArrayEquals(new int[]{x, 255, z},
                        (int[]) unpack.invoke(null, pack.invoke(null, x, 255, z)));
            }
        }
    }

    @Test
    public void stoppingBeforeFirstRestoreTickStillRestoresAndKeepsUnavailableWorlds() throws Exception {
        File directory = Files.createTempDirectory("pit-event-blocks").toFile();
        EventBlockTracker tracker = tracker(directory);
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block block = mock(Block.class);
        AtomicReference<Material> material = new AtomicReference<>(Material.STONE);
        when(block.getType()).thenAnswer(invocation -> material.get());
        when(block.getData()).thenReturn((byte) 0);
        when(block.getX()).thenReturn(12);
        when(block.getY()).thenReturn(70);
        when(block.getZ()).thenReturn(-19);
        Location location = new Location(world, 12, 70, -19);
        when(world.getBlockAt(location)).thenReturn(block);
        when(world.getBlockAt(12, 70, -19)).thenReturn(block);
        when(block.getLocation()).thenReturn(location);
        doAnswer(invocation -> { material.set(invocation.getArgument(0)); return null; })
                .when(block).setType(any(Material.class), eq(false));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);
            tracker.captureBlock("test", location);
            material.set(Material.AIR);
            tracker.restore("test");
            assertTrue("queued rollback must remain journaled", journal(directory).stream().anyMatch(line -> line.startsWith("B|test|12|70|-19|")));
            tracker.onPluginDisable();
            assertEquals(Material.STONE, material.get());
            verify(world, never()).getBlockAt(-13, 70, -19);

            tracker.captureBlock("unloaded", location);
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(null);
            tracker.restoreImmediately("unloaded");
            tracker.onPluginDisable();
            assertTrue(journal(directory).stream().anyMatch(line -> line.startsWith("B|unloaded|")));
        }
    }

    @Test
    public void legacyNegativeZJournalIsMigratedToItsOriginalX() throws Exception {
        File directory = Files.createTempDirectory("pit-event-legacy").toFile();
        Files.write(new File(directory, "event_blocks.tmp").toPath(), Arrays.asList(
                "R|legacy|arena|12|70|-19|12|70|-19", "B|legacy|-13|70|-19|STONE|0"), StandardCharsets.UTF_8);
        EventBlockTracker tracker = tracker(directory);
        method("loadSnapshot").invoke(tracker);
        @SuppressWarnings("unchecked")
        List<String> serialized = (List<String>) method("serialize").invoke(tracker);
        assertTrue(serialized.contains("V|2"));
        assertTrue(serialized.contains("B|legacy|12|70|-19|STONE|0"));
    }

    @Test
    public void blockEntityContentsSurviveJournalReload() throws Exception {
        File directory = Files.createTempDirectory("pit-event-tile").toFile();
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        TileEntity original = mock(TileEntity.class);
        TileEntity restored = mock(TileEntity.class);
        doAnswer(invocation -> {
            ((NBTTagCompound) invocation.getArgument(0)).setString("CustomName", "Arena chest");
            return null;
        }).when(original).b(any(NBTTagCompound.class));
        Block block = mock(Block.class);
        Location location = new Location(world, 5, 64, 5);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(location);
        when(block.getX()).thenReturn(5);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(5);
        when(block.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(location)).thenReturn(block);
        when(world.getBlockAt(5, 64, 5)).thenReturn(block);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(world);
            // Only replace the live-server lookup; exercise real NBT encoding, the
            // disk journal, reloading and decoding into a different block entity.
            EventBlockTracker tracker = spy(tracker(directory));
            doReturn(original).when(tracker).tileAt(block);
            tracker.captureBlock("chest", location);
            EventBlockTracker restarted = spy(tracker(directory));
            doReturn(restored).when(restarted).tileAt(block);
            method("loadSnapshot").invoke(restarted);
            restarted.restoreImmediately("chest");
            verify(restored).a(argThat((NBTTagCompound tag) -> "Arena chest".equals(tag.getString("CustomName"))
                    && tag.getInt("x") == 5 && tag.getInt("y") == 64 && tag.getInt("z") == 5));
            verify(restored).update();
        }
    }

    private static List<String> journal(File directory) throws Exception {
        return Files.readAllLines(new File(directory, "event_blocks.tmp").toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void unavailableWorldKeepsOverlappingSnapshotsInCaptureOrder() throws Exception {
        File directory = Files.createTempDirectory("pit-event-layers").toFile();
        Files.write(new File(directory, "event_blocks.tmp").toPath(), Arrays.asList(
                "V|2", "R|tower|arena|1|64|1|1|64|1", "B|tower|1|64|1|STONE|0",
                "R|wall|arena|1|64|1|1|64|1", "B|wall|1|64|1|WOOL|1"), StandardCharsets.UTF_8);
        EventBlockTracker tracker = tracker(directory);
        method("loadSnapshot").invoke(tracker);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            tracker.onPluginDisable();
            List<String> persisted = journal(directory);
            assertTrue(persisted.indexOf("B|tower|1|64|1|STONE|0")
                    < persisted.indexOf("B|wall|1|64|1|WOOL|1"));
        }
    }

    private static EventBlockTracker tracker(File directory) throws Exception {
        Constructor<EventBlockTracker> constructor = EventBlockTracker.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        EventBlockTracker tracker = constructor.newInstance();
        set(tracker, "tempDir", directory);
        set(tracker, "snapshotFile", new File(directory, "event_blocks.tmp"));
        set(tracker, "writeFile", new File(directory, "event_blocks.tmp.writing"));
        return tracker;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = EventBlockTracker.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
}
