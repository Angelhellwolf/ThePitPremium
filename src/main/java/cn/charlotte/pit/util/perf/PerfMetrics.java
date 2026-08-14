package cn.charlotte.pit.util.perf;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class PerfMetrics {

    private static final Map<String, TimerStat> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, CounterStat> COUNTERS = new ConcurrentHashMap<>();
    private static BukkitTask task;

    private PerfMetrics() {
    }

    public static void recordTime(String key, long nanos) {
        if (!NewConfiguration.INSTANCE.getPerfMetricsEnable()) return;
        if (nanos <= 0) return;
        TIMERS.computeIfAbsent(key, k -> new TimerStat()).add(nanos);
    }

    public static void addCount(String key, long delta) {
        if (!NewConfiguration.INSTANCE.getPerfMetricsEnable()) return;
        if (delta == 0) return;
        COUNTERS.computeIfAbsent(key, k -> new CounterStat()).add(delta);
    }

    public static synchronized void start(JavaPlugin plugin) {
        if (task != null) return;
        if (!NewConfiguration.INSTANCE.getPerfMetricsEnable()) return;

        long seconds = NewConfiguration.INSTANCE.getPerfMetricsIntervalSeconds();
        long periodTicks = Math.max(20L * 10L, seconds * 20L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, PerfMetrics::flushToConsole, periodTicks, periodTicks);
    }

    public static synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        TIMERS.clear();
        COUNTERS.clear();
    }

    private static void flushToConsole() {
        if (!NewConfiguration.INSTANCE.getPerfMetricsEnable()) return;

        List<TimerSnap> timerSnaps = new ArrayList<>();
        for (Map.Entry<String, TimerStat> e : TIMERS.entrySet()) {
            TimerSnap snap = e.getValue().snapshotAndReset(e.getKey());
            if (snap.count > 0 && snap.totalNanos > 0) {
                timerSnaps.add(snap);
            }
        }
        timerSnaps.sort(Comparator.comparingLong((TimerSnap s) -> s.totalNanos).reversed());

        List<CounterSnap> counterSnaps = new ArrayList<>();
        for (Map.Entry<String, CounterStat> e : COUNTERS.entrySet()) {
            CounterSnap snap = e.getValue().snapshotAndReset(e.getKey());
            if (snap.updates > 0 && snap.total != 0) {
                counterSnaps.add(snap);
            }
        }
        counterSnaps.sort(Comparator.comparingLong((CounterSnap s) -> Math.abs(s.total)).reversed());

        if (timerSnaps.isEmpty() && counterSnaps.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("[PerfMetrics] last ")
                .append(NewConfiguration.INSTANCE.getPerfMetricsIntervalSeconds())
                .append("s");

        ThePit.getInstance().getLogger().info(sb.toString());

        if (!timerSnaps.isEmpty()) {
            int limit = Math.min(10, timerSnaps.size());
            for (int i = 0; i < limit; i++) {
                TimerSnap s = timerSnaps.get(i);
                double avgMs = (s.totalNanos / (double) s.count) / 1_000_000.0;
                double totalMs = s.totalNanos / 1_000_000.0;
                double maxMs = s.maxNanos / 1_000_000.0;
                ThePit.getInstance().getLogger().info(
                        String.format("  timer %-28s n=%d avg=%.3fms max=%.3fms total=%.1fms",
                                s.key, s.count, avgMs, maxMs, totalMs)
                );
            }
        }

        if (!counterSnaps.isEmpty()) {
            int limit = Math.min(10, counterSnaps.size());
            for (int i = 0; i < limit; i++) {
                CounterSnap s = counterSnaps.get(i);
                double avg = s.total / (double) s.updates;
                ThePit.getInstance().getLogger().info(
                        String.format("  count %-28s updates=%d total=%d avg=%.2f max=%d",
                                s.key, s.updates, s.total, avg, s.max)
                );
            }
        }
    }

    private static final class TimerStat {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        void add(long nanos) {
            count.increment();
            totalNanos.add(nanos);
            long prev;
            do {
                prev = maxNanos.get();
                if (nanos <= prev) break;
            } while (!maxNanos.compareAndSet(prev, nanos));
        }

        TimerSnap snapshotAndReset(String key) {
            TimerSnap snap = new TimerSnap(
                    key,
                    count.sumThenReset(),
                    totalNanos.sumThenReset(),
                    maxNanos.getAndSet(0L)
            );
            return snap;
        }
    }

    private static final class CounterStat {
        private final LongAdder updates = new LongAdder();
        private final LongAdder total = new LongAdder();
        private final AtomicLong max = new AtomicLong();

        void add(long delta) {
            updates.increment();
            total.add(delta);
            long abs = Math.abs(delta);
            long prev;
            do {
                prev = max.get();
                if (abs <= prev) break;
            } while (!max.compareAndSet(prev, abs));
        }

        CounterSnap snapshotAndReset(String key) {
            return new CounterSnap(
                    key,
                    updates.sumThenReset(),
                    total.sumThenReset(),
                    max.getAndSet(0L)
            );
        }
    }

    private static final class TimerSnap {
        final String key;
        final long count;
        final long totalNanos;
        final long maxNanos;

        TimerSnap(String key, long count, long totalNanos, long maxNanos) {
            this.key = key;
            this.count = count;
            this.totalNanos = totalNanos;
            this.maxNanos = maxNanos;
        }
    }

    private static final class CounterSnap {
        final String key;
        final long updates;
        final long total;
        final long max;

        CounterSnap(String key, long updates, long total, long max) {
            this.key = key;
            this.updates = updates;
            this.total = total;
            this.max = max;
        }
    }
}

