package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;

import java.util.UUID;

/** Private training and the two approved owners use the human base; other bots retain half. */
public final class BotRewards {
    /** Only these two accounts receive the human-style Bot reward policy. */
    private static final UUID SC_SHENGCHENG = UUID.fromString("e4bc7a15-8174-35ea-b23f-4c50080db6e9");
    private static final UUID SEVENTY_SEVEN_HZ = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");
    public static final double BOT_ENCHANT_MULTIPLIER = 0.5;

    private BotRewards() { }
    public static double multiplier(double humanMultiplier) { return RewardMath.positive(humanMultiplier) * 0.5; }

    public static double multiplier(double humanMultiplier, boolean normalReward) {
        return normalReward ? RewardMath.positive(humanMultiplier) : multiplier(humanMultiplier);
    }

    public static boolean usesNormalReward(PlayerProfile recipient) {
        if (recipient == null) return false;
        UUID id = recipient.getPlayerUuid();
        if (normalRewardOwner(id)) return true;
        if (!recipient.isBot()) return false;
        ThePit plugin = ThePit.getInstance();
        if (plugin == null || plugin.getJunxuManager() == null || plugin.getJunxuManager().bots() == null) return false;
        cn.charlotte.pit.junxu.JunxuRoom room = plugin.getJunxuManager().bots().room(id);
        return room != null && normalRewardOwner(room.owner);
    }

    public static boolean normalRewardOwner(UUID id) {
        return SC_SHENGCHENG.equals(id) || SEVENTY_SEVEN_HZ.equals(id);
    }

    public static double amount(int prestige, double multiplier) {
        return (10.0 + Math.max(0, prestige) * 0.5) * RewardMath.positive(multiplier);
    }

    /** Bot primary receipts skip automatic newcomer bonuses, so include it exactly once here. */
    public static double trainingBaseMultiplier(int prestige, boolean privateTraining) {
        return trainingBaseMultiplier(prestige, privateTraining, false);
    }

    public static double trainingBaseMultiplier(int prestige, boolean privateTraining, boolean normalReward) {
        return privateTraining || normalReward ? ProgressionV2.newcomer(prestige) : .5;
    }

    /** All recipients and both currencies share one draw; splitting assists cannot reroll it. */
    public static double variableFactor(cn.charlotte.pit.data.PlayerProfile victim) {
        if (!victim.isBot() || !EconomyV2.active() && !EconomyV2.observing()) return 1;
        cn.charlotte.pit.data.sub.EconomyData state = CombatEconomy.state(victim);
        if (!Double.isFinite(state.botRewardFactor) || state.botRewardFactor <= 0)
            state.botRewardFactor = factorFromDraw(java.util.concurrent.ThreadLocalRandom.current().nextDouble());
        return state.botRewardFactor;
    }

    /** A full human-style account must not inherit the Bot-only random variation. */
    public static double variableFactor(cn.charlotte.pit.data.PlayerProfile victim, boolean normalReward) {
        return normalReward ? 1 : variableFactor(victim);
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
