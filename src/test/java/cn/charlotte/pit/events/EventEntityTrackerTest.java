package cn.charlotte.pit.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class EventEntityTrackerTest {
    @Test
    public void endingAndStoppingKeepEntitiesWhoseWorldCannotBeLoaded() throws Exception {
        Constructor<EventEntityTracker> constructor = EventEntityTracker.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        EventEntityTracker tracker = constructor.newInstance();
        File directory = Files.createTempDirectory("pit-event-entities").toFile();
        set(tracker, "tempDir", directory);
        set(tracker, "snapshotFile", new File(directory, "entities.tmp"));
        set(tracker, "writeFile", new File(directory, "entities.tmp.writing"));
        World world = mock(World.class);
        when(world.getName()).thenReturn("unavailable");
        Entity entity = mock(Entity.class);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            tracker.track("test", entity);
            tracker.removeAll("test");
            tracker.onPluginDisable();
            Field field = EventEntityTracker.class.getDeclaredField("tracked");
            field.setAccessible(true);
            assertEquals(1, ((Map<?, ?>) field.get(tracker)).size());
            assertEquals(1, Files.readAllLines(new File(directory, "entities.tmp").toPath()).size());
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
