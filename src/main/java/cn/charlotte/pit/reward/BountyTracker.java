package cn.charlotte.pit.reward;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread combat evidence and rolling limits. Reconnecting does not reset pair limits. */
public final class BountyTracker {
    private final Map<Pair, Damage> damage = new HashMap<>();
    private final Map<Pair, Long> growthPairs = new HashMap<>();
    private final Map<Pair, Long> claimPairs = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> growthWindows = new HashMap<>();

    public void recordDamage(UUID attacker, UUID victim, double amount, long now) {
        if (attacker == null || victim == null || attacker.equals(victim) || RewardMath.positive(amount) == 0) return;
        Pair pair = new Pair(attacker, victim);
        Damage previous = damage.get(pair);
        double total = previous != null && fresh(previous.time, now, BountyRules.COMBAT_WINDOW) ? previous.amount : 0;
        damage.put(pair, new Damage(Math.min(100, total + amount), now));
    }

    public boolean hasRealFight(UUID first, UUID second, long now) {
        if (first == null || second == null || first.equals(second)) return false;
        if (!contributed(new Pair(first, second), now)) return false;
        // A hunter may finish a target fighting another human; the victim need not hit this hunter.
        double victimOutput = 0;
        for (Map.Entry<Pair, Damage> entry : damage.entrySet()) {
            if (entry.getKey().first.equals(second) && fresh(entry.getValue().time, now, BountyRules.COMBAT_WINDOW)) {
                victimOutput += entry.getValue().amount;
            }
        }
        return victimOutput >= BountyRules.MIN_COMBAT_DAMAGE;
    }

    private boolean contributed(Pair pair, long now) {
        Damage value = damage.get(pair);
        return value != null && value.amount >= BountyRules.MIN_COMBAT_DAMAGE
                && fresh(value.time, now, BountyRules.COMBAT_WINDOW);
    }

    public int grow(UUID killer, UUID victim, int current, int cap, long now) {
        if (killer == null || victim == null || killer.equals(victim) || current >= cap) return 0;
        Pair pair = unordered(killer, victim);
        if (limited(growthPairs, pair, now, BountyRules.GROWTH_PAIR_COOLDOWN)) return 0;
        ArrayDeque<Long> times = growthWindows.computeIfAbsent(killer, ignored -> new ArrayDeque<>());
        times.removeIf(time -> !fresh(time, now, 60_000));
        if (times.size() >= BountyRules.GROWTHS_PER_MINUTE) return 0;
        int amount = Math.min(BountyRules.GROWTH_PER_KILL, cap - Math.max(0, current));
        if (amount <= 0) return 0;
        growthPairs.put(pair, now);
        times.addLast(now);
        return amount;
    }

    public boolean claim(UUID killer, UUID victim, long now) {
        if (killer == null || victim == null || killer.equals(victim)) return false;
        Pair pair = unordered(killer, victim);
        if (limited(claimPairs, pair, now, BountyRules.CLAIM_PAIR_COOLDOWN)) return false;
        claimPairs.put(pair, now);
        return true;
    }

    public void finishLife(UUID player) {
        damage.keySet().removeIf(pair -> pair.first.equals(player) || pair.second.equals(player));
    }

    public void cleanup(long now) {
        damage.values().removeIf(value -> !fresh(value.time, now, BountyRules.COMBAT_WINDOW));
        growthPairs.values().removeIf(time -> !fresh(time, now, BountyRules.GROWTH_PAIR_COOLDOWN));
        claimPairs.values().removeIf(time -> !fresh(time, now, BountyRules.CLAIM_PAIR_COOLDOWN));
        growthWindows.values().removeIf(times -> {
            times.removeIf(time -> !fresh(time, now, 60_000));
            return times.isEmpty();
        });
    }

    private static boolean fresh(long time, long now, long duration) {
        return now >= time && now - time < duration;
    }

    private static boolean limited(Map<Pair, Long> map, Pair pair, long now, long duration) {
        Long previous = map.get(pair);
        return previous != null && fresh(previous, now, duration);
    }

    private static Pair unordered(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? new Pair(first, second) : new Pair(second, first);
    }

    private static final class Damage {
        private final double amount;
        private final long time;
        private Damage(double amount, long time) { this.amount = amount; this.time = time; }
    }

    private static final class Pair {
        private final UUID first, second;
        private Pair(UUID first, UUID second) { this.first = first; this.second = second; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Pair)) return false;
            Pair pair = (Pair) other;
            return first.equals(pair.first) && second.equals(pair.second);
        }
        @Override public int hashCode() { return Objects.hash(first, second); }
    }
}
