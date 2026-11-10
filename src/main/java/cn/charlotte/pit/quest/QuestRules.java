package cn.charlotte.pit.quest;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.QuestData;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.time.TimeUtil;
import org.bukkit.entity.Player;

public final class QuestRules {
    private QuestRules() {}

    public static boolean isNight(PlayerProfile profile, QuestData quest) {
        return quest.getNightQuest() != null ? quest.getNightQuest()
                : profile.isNightQuestEnable() && TimeUtil.getMinecraftTick(quest.getStartTime()) > 12000;
    }

    public static int completionCost(PlayerProfile profile, int level, boolean night) {
        return 1 + (night && level > 1 ? 1 : 0)
                + (night && profile.getUnlockedPerkMap().containsKey("quest_master_perk") ? 1 : 0);
    }

    public static double coinMultiplier(Player player) {
        if (player == null) return 1;
        if (PlayerUtil.shouldIgnoreEnchant(player)) return 1;
        int level = Utils.getEnchantLevel(player.getInventory().getLeggings(), "negotiator_enchant");
        return level >= 3 ? 2 : level == 2 ? 1.6 : level == 1 ? 1.3 : 1;
    }

    public static double previewCoins(PlayerProfile profile, Player player, int level, boolean night) {
        if (!cn.charlotte.pit.reward.EconomyV2.active()) return night ? 0 : level * 100 * coinMultiplier(player);
        return (night ? 100 : 150) * level * coinMultiplier(player)
                * cn.charlotte.pit.reward.ProgressionV2.sideScale(profile.getPrestige())
                * cn.charlotte.pit.reward.ProgressionV2.newcomer(profile.getPrestige());
    }

    public static double previewExperience(PlayerProfile profile, int level, boolean night) {
        if (!cn.charlotte.pit.reward.EconomyV2.active()) return level * (night ? 250 : 40);
        return level * (night ? 800 : 600) * cn.charlotte.pit.reward.ProgressionV2.sideScale(profile.getPrestige())
                * cn.charlotte.pit.reward.ProgressionV2.newcomer(profile.getPrestige());
    }

    public static double trueDamageMultiplier(Player attacker) {
        if (attacker == null) return 1;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        QuestData quest = profile == null ? null : profile.getCurrentQuest();
        return quest != null && "High_Value_Target".equals(quest.getInternalName())
                ? Math.max(0, 1 - 0.4 * quest.getLevel()) : 1;
    }
}
