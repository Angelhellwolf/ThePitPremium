package cn.charlotte.pit.reward;

/** Bounties have their own budget; combat multipliers never multiply the principal. */
public final class BountyRules {
    public static final int NORMAL_CAP = 5000;
    public static final int HIGHLANDER_CAP = 10000;
    public static final int GROWTH_PER_KILL = 25;
    public static final int GROWTHS_PER_MINUTE = 4;
    public static final long GROWTH_PAIR_COOLDOWN = 5 * 60_000L;
    public static final long CLAIM_PAIR_COOLDOWN = 10 * 60_000L;
    public static final long COMBAT_WINDOW = 30_000L;
    public static final double MIN_COMBAT_DAMAGE = 2.0;

    private BountyRules() { }

    public static int cap(boolean highlander) {
        return highlander ? HIGHLANDER_CAP : NORMAL_CAP;
    }

    public static double payout(int bounty, boolean genesis, boolean solvent) {
        // Existing bounty-specific bonuses add; neither can multiply the other.
        return Math.min(HIGHLANDER_CAP, Math.max(0, bounty))
                * (1 + (genesis ? 0.5 : 0) + (solvent ? 0.5 : 0));
    }

    public static double highlanderBonus(int prestige, double calendarMultiplier) {
        return RewardMath.positive((10.0 + Math.max(0, prestige) * 0.5) * 0.5
                * RewardMath.positive(calendarMultiplier));
    }

    public static double highlanderVulnerability(double streak) {
        return Math.min(0.30, RewardMath.positive((streak - 50) * 0.003));
    }
}
