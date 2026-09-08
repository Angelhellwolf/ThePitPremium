package cn.charlotte.pit.perk;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.menu.prestige.button.PrestigeStatusButton;
import cn.charlotte.pit.util.chat.*;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.rank.RankUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.ArrayList;

/** The same prestige transaction for menu confirmations and simulated players. */
public final class PrestigeProgression {
    private PrestigeProgression() {}

    public static boolean eligible(PlayerProfile profile, int limit) {
        return profile.getLevel() >= 120 && profile.getPrestige() < limit
                && profile.getGrindedCoins() >= 16000.0 * (profile.getPrestige() + 1);
    }

    public static int renownAward(int newPrestige) {
        return 10 + (newPrestige > 4 ? 10 : 0) + (newPrestige > 10 ? 10 : 0) + (newPrestige > 14 ? 10 : 0);
    }

    public static boolean advanceData(PlayerProfile profile, int limit) {
        if (!eligible(profile, limit)) return false;
        profile.setCoins(0);
        profile.setExperience(0);
        profile.setGrindedCoins(0);
        profile.setPrestige(profile.getPrestige() + 1);
        profile.setBoughtPerk(new ArrayList<>());
        profile.getBoughtPerkMap().clear();
        profile.setAutoBuyButtons(new ArrayList<>());
        if (profile.getUnlockedPerkMap().containsKey("gold_stack")) {
            profile.setGoldStackAddon(0.0);
            profile.setGoldStackMax(profile.getGoldStackMax() + 0.1);
        }
        if (profile.getUnlockedPerkMap().containsKey("xp_stack")) {
            profile.setXpStackAddon(0.0);
            profile.setXpStackMax(profile.getXpStackMax() + 0.2);
        }
        if (profile.getUnlockedPerkMap().containsKey("FastPass")) {
            profile.setExperience(LevelUtil.getLevelTotalExperience(profile.getPrestige(), 50));
        }
        profile.getChosePerk().clear();
        profile.getChosePerk().put(5, new PerkData("over_drive", 1));
        profile.setRenown(profile.getRenown() + renownAward(profile.getPrestige()));
        return true;
    }

    public static boolean advance(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!eligible(profile, PrestigeStatusButton.limit)) return false;
        if ("auction".equals(ThePit.getInstance().getEventFactory().getActiveNormalEventName())) return false;
        if (profile.getMailData().getMails().stream().anyMatch(mail -> !mail.isClaimed() && mail.getContent().contains("拍卖"))) return false;
        for (PerkData chosen : new ArrayList<>(profile.getChosePerk().values())) {
            if (chosen == null) continue;
            for (AbstractPerk perk : ThePit.getInstance().getPerkFactory().getPerks()) {
                if (perk.getInternalPerkName().equalsIgnoreCase(chosen.getPerkInternalName())) perk.onPerkInactive(player);
            }
        }
        if (!advanceData(profile, PrestigeStatusButton.limit)) return false;
        CC.boardCast(MessageType.PRESTIGE, "&e&l精通! &7" + RankUtil.getPlayerColoredName(player.getUniqueId())
                + " &7解锁了精通 &e" + RomanUtil.convert(profile.getPrestige()) + " &7,gg!");
        TitleUtil.sendTitle(player, "&e&l精通!", "&7你解锁了精通 &e" + RomanUtil.convert(profile.getPrestige()) + " &7!", 20, 100, 20);
        player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1, 1);
        return true;
    }
}
