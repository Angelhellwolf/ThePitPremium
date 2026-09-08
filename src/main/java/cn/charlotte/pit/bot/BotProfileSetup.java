package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.MedalData;
import cn.charlotte.pit.medal.AbstractMedal;

/** NPC profiles do not emit the human login event that normally creates medal records. */
public final class BotProfileSetup {
    private BotProfileSetup() {}
    public static void initializeMedals(ThePit plugin, PlayerProfile profile) {
        if (plugin.getMedalFactory() == null) return;
        if (profile.getMedalData() == null) profile.setMedalData(new MedalData());
        for (AbstractMedal medal : plugin.getMedalFactory().getMedals()) {
            for (int level = 1; level <= medal.getMaxLevel(); level++) {
                profile.getMedalData().getMedalStatus().computeIfAbsent(medal.getInternalName() + "#" + level,
                        ignored -> new MedalData.MedalStatus());
            }
        }
    }
}
