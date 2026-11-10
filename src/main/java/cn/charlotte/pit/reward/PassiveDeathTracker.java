package cn.charlotte.pit.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Detects consecutive combat deaths without dealing accepted damage in that life. */
public final class PassiveDeathTracker {
    private final long retentionMillis;
    private final Map<UUID, State> players = new HashMap<>();

    public PassiveDeathTracker(long retentionMillis) { this.retentionMillis = retentionMillis; }

    public void recordOutput(UUID player, double acceptedDamage, long now) {
        if (player == null || !Double.isFinite(acceptedDamage) || acceptedDamage <= 0) return;
        State state = state(player, now);
        state.dealtDamage = true;
        state.passiveDeaths = 0;
        state.updatedAt = now;
    }

    public Result finishLife(UUID player, boolean combatDeath, long now) {
        State state = state(player, now);
        if (combatDeath) {
            state.passiveDeaths = state.dealtDamage ? 0 : Math.min(5, state.passiveDeaths + 1);
        }
        state.dealtDamage = false;
        state.updatedAt = now;
        return combatDeath ? result(state.passiveDeaths) : result(0);
    }

    public Result current(UUID player, long now) { return result(state(player, now).passiveDeaths); }
    public void forget(UUID player) { players.remove(player); }
    public void cleanup(long now) { players.values().removeIf(state -> now - state.updatedAt >= retentionMillis); }

    private State state(UUID player, long now) {
        State state = players.get(player);
        if (state == null || now - state.updatedAt >= retentionMillis || now < state.updatedAt) {
            state = new State(); state.updatedAt = now; players.put(player, state);
        }
        return state;
    }

    private static Result result(int deaths) {
        return new Result(deaths, deaths >= 5 ? 0.2 : deaths >= 3 ? 0.5 : 1.0);
    }

    private static final class State { boolean dealtDamage; int passiveDeaths; long updatedAt; }
    public static final class Result {
        private final int passiveDeaths;
        private final double multiplier;
        Result(int passiveDeaths, double multiplier) { this.passiveDeaths = passiveDeaths; this.multiplier = multiplier; }
        public int getPassiveDeaths() { return passiveDeaths; }
        public double getMultiplier() { return multiplier; }
    }
}
