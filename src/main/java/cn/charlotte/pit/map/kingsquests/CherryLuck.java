package cn.charlotte.pit.map.kingsquests;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;

/** Cherry luck is one temporary point; eating again refreshes its expiry, never its strength. */
public final class CherryLuck {
    private CherryLuck() { }

    public static int points(PlayerProfile profile, long now) {
        return profile.isLoaded() && !profile.isBot() && profile.getCherryLuckExpiresAt() > now ? 1 : 0;
    }

    public static void refresh(PlayerProfile profile, long now) {
        profile.setCherryLuckExpiresAt(now + NewConfiguration.INSTANCE.getCherryLuckDurationSeconds() * 1000L);
    }

    public static double rareChance(PlayerProfile profile, double baseChance, long now) {
        return points(profile, now) == 0 ? baseChance
                : Math.min(1.0, baseChance * NewConfiguration.INSTANCE.getCherryRareChanceMultiplier());
    }

    public static double dropChance(double rewardMultiplier) {
        if (!Double.isFinite(rewardMultiplier)) return 0.0;
        return NewConfiguration.INSTANCE.getCherryDropChance() * Math.max(0.0, Math.min(1.0, rewardMultiplier));
    }
}
