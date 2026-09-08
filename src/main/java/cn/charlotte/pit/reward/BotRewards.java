package cn.charlotte.pit.reward;

/** The owner requires bots to use exactly half of the current human calendar multiplier. */
public final class BotRewards {
    private BotRewards() { }
    public static double multiplier(double humanMultiplier) { return RewardMath.positive(humanMultiplier) * 0.5; }
    public static double amount(int prestige, double multiplier) {
        return (10.0 + Math.max(0, prestige) * 0.5) * RewardMath.positive(multiplier);
    }

    public static boolean isTrainingItem(org.bukkit.inventory.ItemStack item) {
        Integer flag = cn.charlotte.pit.util.item.ItemUtil.getItemIntData(item, "botReward");
        return flag != null && flag == 1;
    }
}
