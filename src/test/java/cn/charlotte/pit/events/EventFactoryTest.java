package cn.charlotte.pit.events;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EventFactoryTest {
    @Test
    public void earlyNormalEventEndClearsTheLastCountdownImmediately() throws Exception {
        EventFactory factory = new EventFactory();
        INormalEvent event = mock(INormalEvent.class, withSettings().extraInterfaces(IEvent.class));
        set(factory, "activeNormalEvent", event);
        BukkitTask task = mock(BukkitTask.class);
        set(factory, "normalTask", task);
        cn.charlotte.pit.ThePit plugin = mock(cn.charlotte.pit.ThePit.class);
        cn.charlotte.pit.util.bossbar.BossBarHandler handler = mock(cn.charlotte.pit.util.bossbar.BossBarHandler.class);
        cn.charlotte.pit.util.bossbar.BossBar bar = mock(cn.charlotte.pit.util.bossbar.BossBar.class);
        when(plugin.getBossBar()).thenReturn(handler);
        when(handler.getBossBar()).thenReturn(bar);
        try (MockedStatic<cn.charlotte.pit.ThePit> pit = mockStatic(cn.charlotte.pit.ThePit.class)) {
            pit.when(cn.charlotte.pit.ThePit::getInstance).thenReturn(plugin);
            factory.inactiveEvent(event);
            verify(task).cancel();
            verify(bar).setTitle("");
            verify(bar).setProgress(0);
            verify(bar).update();
            assertNull(factory.getActiveNormalEvent());
        }
    }

    @Test
    public void earlyNormalEndDoesNotEraseAnEpicPreparationBar() throws Exception {
        EventFactory factory = new EventFactory();
        INormalEvent event = mock(INormalEvent.class, withSettings().extraInterfaces(IEvent.class));
        set(factory, "activeNormalEvent", event);
        set(factory, "nextEpicEvent", mock(IEpicEvent.class));
        cn.charlotte.pit.ThePit plugin = mock(cn.charlotte.pit.ThePit.class);
        try (MockedStatic<cn.charlotte.pit.ThePit> pit = mockStatic(cn.charlotte.pit.ThePit.class)) {
            pit.when(cn.charlotte.pit.ThePit::getInstance).thenReturn(plugin);
            factory.inactiveEvent(event);
            verify(plugin, never()).getBossBar();
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    @Test
    public void failedActivationCleansPartialEventState() {
        INormalEvent event = mock(INormalEvent.class, withSettings().extraInterfaces(IEvent.class));
        IEvent lifecycle = (IEvent) event;
        doThrow(new IllegalStateException("partial structure")).when(lifecycle).onActive();
        EventFactory factory = new EventFactory();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            factory.activeEvent(event);
            assertNull(factory.getActiveNormalEvent());
            verify(lifecycle).onInactive();
        }
    }

    @Test
    public void replacingAndStoppingPreparationCancelsItWithoutAwardingCompletion() {
        IEpicEvent first = mock(IEpicEvent.class, withSettings().extraInterfaces(IEvent.class, IPrepareEvent.class));
        IEpicEvent second = mock(IEpicEvent.class, withSettings().extraInterfaces(IEvent.class, IPrepareEvent.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        EventFactory factory = new EventFactory();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            factory.readyEpicEvent(first);
            factory.readyEpicEvent(second);
            verify((IPrepareEvent) first).onPreInactive();
            factory.shutdown();
            factory.shutdown();
            assertNull(factory.getNextEpicEvent());
            verify((IPrepareEvent) second, times(1)).onPreInactive();
            verify((IEvent) first, never()).onInactive();
            verify((IEvent) second, never()).onInactive();
        }
    }
}
