package cn.charlotte.pit.reward;

/** Bot rewards retain half the human calendar base, with a shared per-life v2 variation. */
public final class BotRewards {
    private BotRewards() { }
    public static double multiplier(double humanMultiplier) { return RewardMath.positive(humanMultiplier) * 0.5; }
    public static double amount(int prestige, double multiplier) {
        return (10.0 + Math.max(0, prestige) * 0.5) * RewardMath.positive(multiplier);
    }

    /** All recipients and both currencies share one draw; splitting assists cannot reroll it. */
    public static double variableFactor(cn.charlotte.pit.data.PlayerProfile victim) {
        if (!victim.isBot() || !EconomyV2.active() && !EconomyV2.observing()) return 1;
        cn.charlotte.pit.data.sub.EconomyData state = CombatEconomy.state(victim);
        if (!Double.isFinite(state.botRewardFactor) || state.botRewardFactor <= 0)
            state.botRewardFactor = factorFromDraw(java.util.concurrent.ThreadLocalRandom.current().nextDouble());
        return state.botRewardFactor;
    }

    public static double factorFromDraw(double unitDraw) {
        if (!Double.isFinite(unitDraw) || unitDraw < 0 || unitDraw > 1)
            throw new IllegalArgumentException("Invalid bot reward draw");
        return 1 + (2 * unitDraw - 1) * EconomyV2.botRewardVariation();
    }

    public static boolean isTrainingItem(org.bukkit.inventory.ItemStack item) {
        Integer flag = cn.charlotte.pit.util.item.ItemUtil.getItemIntData(item, "botReward");
        return flag != null && flag == 1;
    }
}
