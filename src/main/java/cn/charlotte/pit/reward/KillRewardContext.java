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
    private boolean v2Training;
    private boolean v2Extras = true;
    private double v2Units = 1;
    private RewardModifiers modifiers;
    private double candidateCoins = Double.NaN, candidateExperience = Double.NaN;

    public static void candidate(double coins, double xp) {
        KillRewardContext c = CURRENT.get();
        if (c != null) { c.candidateCoins = coins; c.candidateExperience = xp; }
    }
    public static double[] candidate(UUID recipient) {
        KillRewardContext c = CURRENT.get();
        return c != null && c.recipient.equals(recipient) && Double.isFinite(c.candidateCoins)
                ? new double[]{c.candidateCoins, c.candidateExperience} : null;
    }

    public static double candidateBonus(UUID recipient, RewardSource source) {
        KillRewardContext c = CURRENT.get();
        if (c != null && c.recipient.equals(recipient) && !c.v2Extras
                && (source == RewardSource.PERK || source == RewardSource.ENCHANT || source == RewardSource.STREAK || source == RewardSource.LOOT)) return 0;
        return bonusMultiplier(recipient, source);
    }

    public void setEconomy(double units, boolean training, boolean extras) {
        v2Units = units; v2Training = training; v2Extras = extras;
    }

    public static boolean training(UUID recipient) {
        KillRewardContext c = CURRENT.get();
        return c != null && c.recipient.equals(recipient) && (c.bot || c.v2Training);
    }

    public static boolean extras(UUID recipient) {
        KillRewardContext c = CURRENT.get();
        return c == null || !c.recipient.equals(recipient) || c.v2Extras;
    }

    public static double units(UUID recipient) {
        KillRewardContext c = CURRENT.get();
        return c == null || !c.recipient.equals(recipient) ? 1 : c.v2Units;
    }

    public static RewardModifiers beginModifiers(UUID recipient, double base, double fixedGold, double fixedXp) {
        KillRewardContext c = CURRENT.get();
        if (c == null || !c.recipient.equals(recipient)) return null;
        c.modifiers = new RewardModifiers(base, fixedGold, fixedXp);
        return c.modifiers;
    }

    public static RewardModifiers modifiers() {
        KillRewardContext c = CURRENT.get();
        return c == null ? null : c.modifiers;
    }

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
                ignored.setEconomy(captured.v2Units, captured.v2Training, captured.v2Extras);
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
        if (EconomyV2.active() && !context.v2Extras && (source == RewardSource.PERK || source == RewardSource.ENCHANT
                || source == RewardSource.STREAK || source == RewardSource.LOOT)) return 0;
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
