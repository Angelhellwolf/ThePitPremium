package cn.charlotte.pit.util.reward;

/** Shared milestone arithmetic for whole kills and fractional assist streaks. */
public final class StreakRewardMath {
    private static final double ROUNDING_TOLERANCE = 1.0E-9;

    private StreakRewardMath() {
    }

    public static int crossedMilestones(double from, double to, int interval) {
        if (interval <= 0 || !Double.isFinite(from) || !Double.isFinite(to) || to <= from) {
            return 0;
        }
        double before = Math.floor((Math.max(0, from) + ROUNDING_TOLERANCE) / interval);
        double after = Math.floor((Math.max(0, to) + ROUNDING_TOLERANCE) / interval);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, after - before));
    }

    public static double superStreakerBonus(double streak) {
        return Math.min(0.5, crossedMilestones(0, streak, 10) * 0.05);
    }

    public static double hermitBonus(double streak) {
        return Math.min(0.75, crossedMilestones(50, streak, 10) * 0.05);
    }
}
