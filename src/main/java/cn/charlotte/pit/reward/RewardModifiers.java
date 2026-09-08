package cn.charlotte.pit.reward;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Each effect sees one common base. Positive percentages add; negative multipliers remain effective. */
public final class RewardModifiers {
    private static final Set<String> PERCENT = new HashSet<>(Arrays.asList(
            "CoinBoost", "XPBoost", "CoinContractBoost", "XPContractBoost", "CoinPrestigeBoost", "celebrity",
            "gold_explorer_enchant", "wisdom", "pit_mba_enchant", "lycanthropy_enchant", "over_drive",
            "beast_mode_mega_streak", "hermit", "super_streaker", "worm_enchant", "CoopCat"));
    public final double base;
    private double fixedGold, fixedXp, percentGold, percentXp;
    private double lossGold = 1, lossXp = 1;

    public RewardModifiers(double base, double fixedGold, double fixedXp) {
        this.base = base; this.fixedGold = fixedGold; this.fixedXp = fixedXp;
    }

    public void apply(String key, AtomicDouble coins, AtomicDouble xp, Runnable action) {
        coins.set(base); xp.set(base);
        action.run();
        double dg = coins.get() - base, dx = xp.get() - base;
        if (!Double.isFinite(dg) || !Double.isFinite(dx)) throw new IllegalStateException("Non-finite modifier");
        if (dg < 0) lossGold *= Math.max(0, (base + dg) / base);
        else if ("GoldMiner".equals(key)) {
            if (dg >= base * .5) { percentGold += base * .5; fixedGold += dg - base * .5; }
            else fixedGold += dg;
        } else if (PERCENT.contains(key)) percentGold += dg;
        else fixedGold += dg;
        if (dx < 0) lossXp *= Math.max(0, (base + dx) / base);
        else if (PERCENT.contains(key)) percentXp += dx;
        else if ("to_the_moon".equals(key) && dx > 0) {
            percentXp += base * .2; fixedXp += Math.max(0, dx - base * .2);
        } else fixedXp += dx;
        coins.set(gold()); xp.set(experience());
    }

    /** Observe the old callback once; do not re-run gameplay effects to produce a comparison. */
    public void observe(String key, double beforeGold, double beforeXp, double afterGold, double afterXp) {
        double dg = afterGold - beforeGold, dx = afterXp - beforeXp;
        if (PERCENT.contains(key) || dg < 0) dg = beforeGold == 0 ? 0 : dg / beforeGold * base;
        if (PERCENT.contains(key) || dx < 0) dx = beforeXp == 0 ? 0 : dx / beforeXp * base;
        if ("sierra_enchant".equals(key)) dg /= 6;
        if ("to_the_moon".equals(key) && dx > 0) dx = Math.max(0, dx - beforeXp * .2) + base * .2;
        if ("GoldMiner".equals(key) && dg > 2) dg = base * .5 + Math.max(0, dg - beforeGold * .5);
        final double goldDelta = dg, xpDelta = dx;
        AtomicDouble coins = new AtomicDouble(), xp = new AtomicDouble();
        apply(key, coins, xp, () -> { coins.set(base + goldDelta); xp.set(base + xpDelta); });
    }

    public double fixedGold() { return fixedGold * lossGold; }
    public double fixedXp() { return fixedXp * lossXp; }
    public double gold() { return (base + fixedGold + percentGold) * lossGold; }
    public double experience() { return (base + fixedXp + percentXp) * lossXp; }
}
