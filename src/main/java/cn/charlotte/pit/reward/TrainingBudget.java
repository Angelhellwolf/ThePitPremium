package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.sub.EconomyData;

/** Token buckets make higher throughput monotonic and do not punish a normal training pace. */
public final class TrainingBudget {
    private TrainingBudget() { }

    public static double consume(EconomyData data, double units, long now) {
        return allocate(data, units, now).rewardUnits;
    }

    /** Consume shared quotas once, retaining private currency separately from drop/contribution rules. */
    public static Allocation allocate(EconomyData data, double units, long now) {
        units = RewardMath.positive(units);
        if (units == 0) return new Allocation(0, 0);
        if (data.trainingUpdatedAt == 0) data.trainingUpdatedAt = now;
        double elapsed = Math.max(0, Math.min(180000, now - data.trainingUpdatedAt)) / 60000.0;
        data.trainingFull = Math.min(EconomyV2.fullCapacity, Math.max(0, data.trainingFull) + elapsed * EconomyV2.fullRate);
        data.trainingBuffer = Math.min(EconomyV2.bufferCapacity, Math.max(0, data.trainingBuffer) + elapsed * EconomyV2.bufferRate);
        // A clock rollback must not create a second refill interval.
        data.trainingUpdatedAt = Math.max(data.trainingUpdatedAt, now);
        double full = Math.min(data.trainingFull, units);
        data.trainingFull -= full;
        double buffered = Math.min(data.trainingBuffer, units - full);
        data.trainingBuffer -= buffered;
        return new Allocation(
                full + buffered * EconomyV2.bufferMultiplier + (units - full - buffered) * EconomyV2.overflowMultiplier,
                full + (units - full) * EconomyV2.privateReducedMultiplier);
    }

    public static final class Allocation {
        public final double rewardUnits;
        public final double privateCurrencyUnits;
        private Allocation(double rewardUnits, double privateCurrencyUnits) {
            this.rewardUnits = rewardUnits;
            this.privateCurrencyUnits = privateCurrencyUnits;
        }
    }

    public static double normalized(double health, double damage, int armorPoints) {
        if (!Double.isFinite(health) || !Double.isFinite(damage) || health <= 0 || damage <= 0) return 0;
        double armor = Math.max(0, Math.min(20, armorPoints)) * .04;
        return Math.min(1, Math.min(health, damage) / 20 * .44 / (1 - armor));
    }
}
