package cn.charlotte.pit.reward;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** A real rolling window; assists can inspect it but cannot consume a kill allowance. */
public final class RepeatKillTracker {
    private final long windowMillis;
    private final Map<UUID, Map<UUID, ArrayDeque<Long>>> kills = new HashMap<>();
    public RepeatKillTracker(long windowMillis) { this.windowMillis = windowMillis; }

    public Result recordKill(UUID killer, UUID victim, long now) {
        ArrayDeque<Long> times = kills.computeIfAbsent(killer, ignored -> new HashMap<>())
                .computeIfAbsent(victim, ignored -> new ArrayDeque<>());
        expire(times, now);
        times.addLast(now);
        return result(times.size());
    }
    public Result forAssist(UUID player, UUID victim, long now) {
        Map<UUID, ArrayDeque<Long>> victims = kills.get(player);
        ArrayDeque<Long> times = victims == null ? null : victims.get(victim);
        if (times == null) return result(1);
        expire(times, now);
        // Use the next actual kill's policy without incrementing its count.
        return result(times.size() + 1);
    }
    public void cleanup(long now) {
        Iterator<Map<UUID, ArrayDeque<Long>>> players = kills.values().iterator();
        while (players.hasNext()) {
            Map<UUID, ArrayDeque<Long>> victims = players.next();
            victims.values().removeIf(times -> { expire(times, now); return times.isEmpty(); });
            if (victims.isEmpty()) players.remove();
        }
    }
    public void forget(UUID player) {
        kills.remove(player);
        kills.values().forEach(victims -> victims.remove(player));
        kills.values().removeIf(Map::isEmpty);
    }
    private void expire(ArrayDeque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= windowMillis) times.removeFirst();
    }
    private static Result result(int count) {
        double[] factors = {1, .75, .5, .25};
        return new Result(count <= 4 ? factors[Math.max(1, count) - 1] : .05, count <= 4);
    }
    public static final class Result {
        private final double multiplier;
        private final boolean progressionEligible;
        Result(double multiplier, boolean progressionEligible) {
            this.multiplier = multiplier; this.progressionEligible = progressionEligible;
        }
        public double getMultiplier() { return multiplier; }
        public boolean isProgressionEligible() { return progressionEligible; }
    }
}

