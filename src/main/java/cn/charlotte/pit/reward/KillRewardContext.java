package cn.charlotte.pit.reward;

import java.util.UUID;

/** Carries a kill's reduction to immediate extra coin/XP grants, never to unrelated players. */
public final class KillRewardContext implements AutoCloseable {
    private static final ThreadLocal<KillRewardContext> CURRENT = new ThreadLocal<>();
    private final KillRewardContext previous;
    private final UUID recipient;
    private final double multiplier;
    private final boolean bot;
    private boolean closed;
    private double previousStreak = Double.NaN;

    public void setPreviousStreak(double streak) { previousStreak = streak; }

    public static boolean crossedStreak(UUID recipient, double current, int interval) {
        KillRewardContext context = CURRENT.get();
        if (context == null || !context.recipient.equals(recipient) || !Double.isFinite(context.previousStreak))
            return current > 0 && interval > 0 && current % interval == 0;
        return cn.charlotte.pit.util.reward.StreakRewardMath.crossedMilestones(context.previousStreak, current, interval) > 0;
    }

    private KillRewardContext(UUID recipient, double multiplier, boolean bot) {
        this.previous = CURRENT.get();
        this.recipient = recipient;
        this.multiplier = Double.isFinite(multiplier) ? Math.max(0, Math.min(1, multiplier)) : 1;
        this.bot = bot;
        CURRENT.set(this);
    }

    public static KillRewardContext open(UUID recipient, double multiplier) {
        return new KillRewardContext(recipient, multiplier, false);
    }

    public static KillRewardContext openBot(UUID recipient) { return new KillRewardContext(recipient, 0, true); }

    /** Carry the originating kill policy through a scheduled reward instead of losing it next tick. */
    public static Runnable wrap(Runnable reward) {
        KillRewardContext captured = CURRENT.get();
        if (captured == null) return reward;
        return () -> {
            try (KillRewardContext ignored = new KillRewardContext(captured.recipient, captured.multiplier, captured.bot)) {
                ignored.previousStreak = captured.previousStreak;
                reward.run();
            }
        };
    }

    public static boolean isBot(UUID recipient) {
        KillRewardContext context = CURRENT.get();
        return context != null && context.bot && context.recipient.equals(recipient);
    }

    public static boolean isBotReward() {
        KillRewardContext context = CURRENT.get();
        return context != null && context.bot;
    }

    public static double bonusMultiplier(UUID recipient, RewardSource source) {
        KillRewardContext context = CURRENT.get();
        if (context == null || !context.recipient.equals(recipient)) return 1;
        // KILL/ASSIST already include this policy in the aggregate receipt.
        // Mail, stored payouts, contracts, and unrelated event rewards keep their own provenance.
        return source == RewardSource.PERK || source == RewardSource.ENCHANT
                || source == RewardSource.STREAK || source == RewardSource.LOOT ? context.multiplier : 1;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
