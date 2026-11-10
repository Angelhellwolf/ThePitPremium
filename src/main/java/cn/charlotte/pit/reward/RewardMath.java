package cn.charlotte.pit.reward;

/** Pure arithmetic shared by rewards and regression tests. */
public final class RewardMath {
    private RewardMath() {}
    public static double positive(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
    public static double experienceTotal(double requested, double cap) {
        if (!Double.isFinite(requested)) {
            throw new IllegalArgumentException("Experience must be finite");
        }
        return Math.max(0, Math.min(requested, positive(cap)));
    }
    public static double contribution(double damage, double totalDamage) {
        return totalDamage > 0 && Double.isFinite(totalDamage)
                ? Math.min(1, positive(damage) / totalDamage) : 0;
    }
    public static boolean isNoob(boolean bot, int prestige, int level, int levelLimit) {
        return isNoob(bot, prestige, level, levelLimit, 0);
    }
    public static boolean isNoob(boolean bot, int prestige, int level, int levelLimit, int maxPrestige) {
        return !bot && prestige >= 0 && prestige <= maxPrestige && level >= 0 && level < levelLimit;
    }
    public static double creditedDamage(double health, double absorption, double normalDamage,
                                        double extraDamage, boolean syntheticLethal) {
        double amount = syntheticLethal ? positive(extraDamage) : positive(normalDamage) + positive(extraDamage);
        return Math.min(positive(health) + positive(absorption), positive(amount));
    }
}
