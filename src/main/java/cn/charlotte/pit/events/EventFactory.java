package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.time.TimeUtil;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.logging.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.bukkit.Sound.BURP;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/9 12:59
 */
public class EventFactory {
    private final List<INormalEvent> normalEvents;
    private final List<IEpicEvent> epicEvents;



    private volatile INormalEvent activeNormalEvent;
    private volatile IEpicEvent activeEpicEvent;
    private Cooldown normalEnd;

    private long lastNormalEvent;

    private volatile IEpicEvent nextEpicEvent;
    private Cooldown nextEpicEventTimer;
    private BukkitTask normalTask;
    private BukkitTask preparationTask;
    private BukkitTask timerTask;
    private boolean stopping;

    public EventFactory() {
        this.normalEvents = new ArrayList<>();
        this.epicEvents = new ArrayList<>();
        this.normalEnd = new Cooldown(0);
    }

    public void pushEvent(IEpicEvent event) {
        pushEvent(event, false);
    }

    public void pushEvent(IEpicEvent event, boolean force) {
        if (publicOnline() >= getRequiredOnlineSafe((IEvent) event)) {
            readyEpicEvent(event);
        }
    }

    public void pushEvent(INormalEvent event) {
        pushEvent(event, false);
    }

    public void pushEvent(INormalEvent event, boolean force) {
        if (publicOnline() >= getRequiredOnlineSafe((IEvent) event)) {
            activeEvent(event);
        }
    }

    @SneakyThrows
    public void init(List<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            if (IEpicEvent.class.isAssignableFrom(clazz)) {
                try {
                    this.epicEvents.add((IEpicEvent) clazz.newInstance());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            if (INormalEvent.class.isAssignableFrom(clazz)) {
                try {
                    this.normalEvents.add((INormalEvent) clazz.newInstance());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        timerTask = Bukkit.getScheduler()
                .runTaskTimer(ThePit.getInstance(), new EventTimer(), 20, 20);

    }

    public String getActiveEpicEventName() {
        if (activeEpicEvent == null) {
            return null;
        }
        return ((IEvent) activeEpicEvent).getEventInternalName();
    }

    public String getActiveNormalEventName() {
        if (activeNormalEvent == null) {
            return null;
        }
        return ((IEvent) activeNormalEvent).getEventInternalName();
    }

    public void activeEvent(INormalEvent event) {
        if (stopping || event == null || activeNormalEvent == event) return;
        if (activeNormalEvent != null) inactiveEvent(activeNormalEvent);
        EventBlockTracker.get().finishRestorations();
        this.lastNormalEvent = System.currentTimeMillis();
        this.activeNormalEvent = event;
        IEvent iEvent = (IEvent) event;
        this.normalEnd = new Cooldown(5, TimeUnit.MINUTES);
        try {
            iEvent.onActive();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            this.activeNormalEvent = null;
            cleanup(iEvent);
            clearIdleBossBar();
            return;
        }
        if (activeNormalEvent != event) return;

        normalTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!event.equals(activeNormalEvent)) {
                    cancel();
                    return;
                }

                if (normalEnd.hasExpired()) {
                    cancel();
                    ThePit.getInstance().getEventFactory().inactiveEvent(event);
                    return;
                }
                if (activeEpicEvent != null || nextEpicEvent != null) return;

                if (tick >= 15) {
                    tick = 0;
                }
                String start;
                if (tick % 2 == 0) {
                    start = "&a&l小型事件! ";
                } else {
                    start = "&2&l小型事件! ";
                }

                final String title = CC.translate(start + "&6&l" + iEvent.getEventName() + " &7将在 &e" + TimeUtil.millisToTimer(normalEnd.getRemaining()) + " &7后结束!");

                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setTitle(title);
                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setProgress(normalEnd.getRemaining() / (5 * 1000 * 60d));

                tick++;
            }
        }.runTaskTimer(ThePit.getInstance(), 10, 10);
    }

    public void inactiveEvent(INormalEvent event) {
        if (event == null || activeNormalEvent != event) return;

        this.activeNormalEvent = null;
        if (normalTask != null) normalTask.cancel();
        normalTask = null;
        IEvent iEvent = (IEvent) event;
        cleanup(iEvent);
        clearIdleBossBar();
    }

    public void readyEpicEvent(IEpicEvent event) {
        if (stopping || event == null || nextEpicEvent == event || activeEpicEvent == event) return;
        cancelPreparation();
        this.nextEpicEvent = event;
        this.nextEpicEventTimer = new Cooldown(5, TimeUnit.MINUTES);
        IEvent iEvent = (IEvent) event;

        if (event instanceof IPrepareEvent) {
            try {
                ((IPrepareEvent) event).onPreActive();
            } catch (Throwable failure) {
                cancelPreparation();
                Bukkit.getLogger().log(Level.WARNING, "Failed to prepare event", failure);
                return;
            }
        }

        preparationTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (stopping || nextEpicEvent != event) {
                    cancel();
                    return;
                }
                if (nextEpicEventTimer.hasExpired()) {
                    ThePit.getInstance()
                            .getBossBar()
                            .getBossBar()
                            .setTitle("");
                    cancel();
                    activeEvent(event);
                    return;
                }

                if (tick >= 15) {
                    tick = 0;
                }
                String start;
                if (tick % 2 == 0) {
                    start = "&5&l大型事件! ";
                } else {
                    start = "&d&l大型事件! ";
                }

                final String title = CC.translate(start + "&6&l" + iEvent.getEventName() + " &7将在 &e" + TimeUtil.millisToTimer(nextEpicEventTimer.getRemaining()) + " &7后开始!");

                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setTitle(title);
                ThePit.getInstance()
                        .getBossBar()
                        .getBossBar()
                        .setProgress(nextEpicEventTimer.getRemaining() / (5 * 1000 * 60d));

                tick++;
            }
        }.runTaskTimer(ThePit.getInstance(), 10, 10);
    }

    public void activeEvent(IEpicEvent event) {
        if (stopping || event == null || activeEpicEvent == event) return;
        if (activeEpicEvent != null) inactiveEvent(activeEpicEvent);
        if (activeNormalEvent != null) inactiveEvent(activeNormalEvent);
        EventBlockTracker.get().finishRestorations();
        if (nextEpicEvent != event) cancelPreparation();
        if (preparationTask != null) preparationTask.cancel();
        preparationTask = null;
        this.nextEpicEvent = null;
        this.nextEpicEventTimer = null;
        this.activeEpicEvent = event;
        IEvent iEvent = (IEvent) event;
        this.normalEnd = new Cooldown(5, TimeUnit.MINUTES);
        cn.charlotte.pit.reward.MajorEventRewards.start(iEvent.getEventInternalName());
        try {
            iEvent.onActive();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            this.activeEpicEvent = null;
            cleanup(iEvent);
            clearIdleBossBar();
        }
    }

    private int getRequiredOnlineSafe(IEvent event) {
        try {
            Integer required = event.requireOnline();
            return cn.charlotte.pit.reward.MajorEventRewards.minimum(event.getEventInternalName(), required == null ? 0 : required);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return 0;
        }
    }

    public static int publicOnline() {
        return cn.charlotte.pit.reward.EconomyV2.active()
                ? cn.charlotte.pit.junxu.PublicPitParticipants.online().size() : Bukkit.getOnlinePlayers().size();
    }

    public void inactiveEvent(IEpicEvent event) {
        if (event == null || activeEpicEvent != event) return;

        this.activeEpicEvent = null;
        IEvent iEvent = (IEvent) event;

        Bukkit.getOnlinePlayers().forEach(player -> player.playSound(player.getLocation(), BURP, 1, 0.72F));
        cleanup(iEvent);
        clearIdleBossBar();
    }

    private void cleanup(IEvent event) {
        try {
            event.onInactive();
        } catch (Throwable failure) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to clean event " + event.getEventInternalName(), failure);
        } finally {
            cn.charlotte.pit.reward.MajorEventRewards.end(event.getEventInternalName());
        }
    }

    private void cancelPreparation() {
        if (preparationTask != null) preparationTask.cancel();
        preparationTask = null;
        IEpicEvent pending = nextEpicEvent;
        nextEpicEvent = null;
        nextEpicEventTimer = null;
        if (pending instanceof IPrepareEvent) {
            try {
                ((IPrepareEvent) pending).onPreInactive();
            } catch (Throwable failure) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to cancel prepared event", failure);
            }
        }
        clearIdleBossBar();
    }

    /** An event can finish early (e.g. the egg's click limit); its last countdown must not remain frozen. */
    private void clearIdleBossBar() {
        if (activeNormalEvent != null || activeEpicEvent != null || nextEpicEvent != null) return;
        ThePit plugin = ThePit.getInstance();
        if (plugin == null || plugin.getBossBar() == null) return;
        cn.charlotte.pit.util.bossbar.BossBar bar = plugin.getBossBar().getBossBar();
        if (bar == null) return;
        bar.setTitle("");
        bar.setProgress(0);
        bar.update();
    }

    public void shutdown() {
        if (stopping) return;
        stopping = true;
        if (timerTask != null) timerTask.cancel();
        timerTask = null;
        cancelPreparation();
        if (activeNormalEvent != null) inactiveEvent(activeNormalEvent);
        if (activeEpicEvent != null) inactiveEvent(activeEpicEvent);
    }

    public List<INormalEvent> getNormalEvents() {
        return normalEvents;
    }

    public List<IEpicEvent> getEpicEvents() {
        return epicEvents;
    }

    public INormalEvent getActiveNormalEvent() {
        return activeNormalEvent;
    }

    public IEpicEvent getActiveEpicEvent() {
        return activeEpicEvent;
    }

    public Cooldown getNormalEnd() {
        return normalEnd;
    }

    public void setNormalEnd(Cooldown cooldown) {
        this.normalEnd = cooldown;
    }

    public long getLastNormalEvent() {
        return lastNormalEvent;
    }

    public IEpicEvent getNextEpicEvent() {
        return nextEpicEvent;
    }

    public Cooldown getNextEpicEventTimer() {
        return nextEpicEventTimer;
    }
}
