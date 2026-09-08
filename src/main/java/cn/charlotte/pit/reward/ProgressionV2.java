package cn.charlotte.pit.reward;

/** Pure progression mathematics. Time is a calibration model, never an eligibility gate. */
public final class ProgressionV2 {
    public static final int MAX_PRESTIGE = 50;
    public static final double TARGET_HOURS = 2000;
    private static final int[] WEIGHTS = {1, 2, 3, 4, 6, 8, 10, 12, 14, 16, 20, 24};
    private static final double LATE_FIRST_HOURS = 28;
    private static final double LATE_HOUR_STEP = lateStep();

    private ProgressionV2() { }

    public static double base(int prestige) { return 10 + Math.max(0, prestige) * .5; }
    public static double sideScale(int prestige) { return 1 + Math.max(0, prestige) * .03; }
    public static double newcomer(int prestige) {
        return prestige <= 2 ? 1.5 : prestige <= 5 ? 1.3 : prestige <= 9 ? 1.15 : 1;
    }

    public static double goldRequired(int prestige) {
        int p = Math.max(0, Math.min(MAX_PRESTIGE, prestige));
        if (p <= 4) return 16000 + 4000 * p;
        if (p <= 9) return 40000 + 6000 * (p - 5);
        if (p <= 19) return 72000 + 8000 * (p - 10);
        if (p <= 29) return 160000 + 10000 * (p - 20);
        if (p <= 34) return 270000 + 15000 * (p - 30);
        return Math.round(referenceGoldPerHour(p) * (LATE_FIRST_HOURS + LATE_HOUR_STEP * (p - 35)));
    }

    public static double totalExperience(int prestige) { return 4 * goldRequired(prestige); }

    /** Integer cumulative thresholds keep every round's exact total, without rounding drift. */
    public static double experienceAtLevel(int prestige, int level) {
        int end = Math.max(0, Math.min(120, level));
        int weight = 0;
        for (int i = 0; i < end; i++) weight += WEIGHTS[i / 10];
        return Math.floor(totalExperience(prestige) * weight / 1200.0);
    }

    public static double experienceForLevel(int prestige, int level) {
        int current = Math.max(0, Math.min(119, level));
        return experienceAtLevel(prestige, current + 1) - experienceAtLevel(prestige, current);
    }

    public static double referenceGoldPerHour(int prestige) {
        int p = Math.max(0, prestige);
        double kills = p < 5 ? 2 + .4 * p : p < 10 ? 3.6 + .08 * (p - 5) : 4;
        double assists = p < 5 ? 1 + .4 * p : 3;
        double build = p < 5 ? 1 + .15 * p : p < 10 ? 1.6 + .04 * (p - 5) : 1.8;
        return 60 * base(p) * newcomer(p) * (kills * build + assists * .35) + 500 * sideScale(p);
    }

    public static double referenceHoursTo(int prestige) {
        double hours = 0;
        for (int p = 0; p < Math.min(MAX_PRESTIGE, prestige); p++)
            hours += (goldRequired(p) - (p == 0 ? 1000 : 0)) / referenceGoldPerHour(p);
        return hours;
    }

    private static double lateStep() {
        // The first 35 rounds retain the agreed early/mid-game curve. Fifteen
        // progressively longer rounds use the remaining 2,000-hour budget.
        double early = 0;
        for (int p = 0; p < 35; p++) early += (goldRequired(p) - (p == 0 ? 1000 : 0)) / referenceGoldPerHour(p);
        return ((TARGET_HOURS - early) / 15 - LATE_FIRST_HOURS) / 7;
    }
}
