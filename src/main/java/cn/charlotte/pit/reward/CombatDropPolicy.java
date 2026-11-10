package cn.charlotte.pit.reward;

/** Combat target identity is independent of economy and persisted profile flags. */
public final class CombatDropPolicy {
    private CombatDropPolicy() { }

    public static double multiplier(boolean connectedHuman, boolean synthetic, double rewardMultiplier) {
        if (!Double.isFinite(rewardMultiplier)) return 0;
        return Math.max(0, rewardMultiplier);
    }
}
