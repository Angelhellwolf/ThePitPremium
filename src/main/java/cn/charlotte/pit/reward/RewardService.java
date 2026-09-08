package cn.charlotte.pit.reward;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.util.level.LevelUtil;
import org.bukkit.Bukkit;

/** All gameplay grants run on the server thread and report the amount actually credited. */
public final class RewardService {
    private RewardService() {}

    private static boolean managedPublicPlayer(PlayerProfile profile) {
        return ThePit.getInstance() != null && ThePit.getInstance().getPitBotManager() != null
                && ThePit.getInstance().getPitBotManager().ownsProfile(profile);
    }

    public static boolean isNoob(PlayerProfile profile) {
        return profile != null && profile.isLoaded()
                && NewConfiguration.INSTANCE.getNoobRewardsEnabled()
                && RewardMath.isNoob(profile.isBot() && !managedPublicPlayer(profile), profile.getPrestige(), profile.getLevel(),
                    NewConfiguration.INSTANCE.getNoobProtectLevel(), NewConfiguration.INSTANCE.getNoobMaxPrestige());
    }

    public static double victimMultiplier(PlayerProfile victim) {
        return isNoob(victim) ? NewConfiguration.INSTANCE.getNoobKilledRewardMultiplier() : 1.0;
    }

    public static Receipt grant(PlayerProfile profile, RewardSource source, double coins, double experience) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Reward grants must run on the server thread");
        }
        if (profile == null || !profile.isLoaded() || profile.isBot() && !managedPublicPlayer(profile)) {
            return new Receipt(0, 0);
        }
        boolean botPrimary = (source == RewardSource.KILL || source == RewardSource.ASSIST)
                && KillRewardContext.isBot(profile.getPlayerUuid());
        boolean newcomer = source.appliesNoobBonus() && !botPrimary && isNoob(profile);
        double coinFactor = newcomer ? NewConfiguration.INSTANCE.getNoobCoinRewardMultiplier() : 1.0;
        double experienceFactor = newcomer ? NewConfiguration.INSTANCE.getNoobRewardMultiplier() : 1.0;
        double bonusFactor = KillRewardContext.bonusMultiplier(profile.getPlayerUuid(), source);
        coinFactor *= bonusFactor;
        experienceFactor *= bonusFactor;
        double requestedCoins = RewardMath.positive(RewardMath.positive(coins) * coinFactor);
        double requestedExperience = RewardMath.positive(RewardMath.positive(experience) * experienceFactor);
        double oldCoins = profile.getCoins();
        double oldExperience = profile.getExperience();
        double remaining = Math.max(0,
                LevelUtil.getLevelTotalExperience(profile.getPrestige(), 120) - oldExperience);
        profile.setExperience(oldExperience + Math.min(remaining, requestedExperience));
        if (Double.isFinite(oldCoins + requestedCoins)) {
            profile.setCoins(oldCoins + requestedCoins);
        }
        double actualCoins = RewardMath.positive(profile.getCoins() - oldCoins);
        double actualExperience = RewardMath.positive(profile.getExperience() - oldExperience);
        if (actualCoins > 0) {
            profile.grindCoins(actualCoins);
            EconomyAudit.record(profile, source, actualCoins);
        }
        return new Receipt(actualCoins, actualExperience);
    }

    public static final class Receipt {
        private final double coins;
        private final double experience;
        public Receipt(double coins, double experience) {
            this.coins = coins;
            this.experience = experience;
        }
        public double getCoins() { return coins; }
        public double getExperience() { return experience; }
    }
}
