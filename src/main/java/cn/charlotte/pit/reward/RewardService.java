package cn.charlotte.pit.reward;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.combat.CombatContext;
import org.bukkit.Bukkit;

/** All gameplay grants run on the server thread and report the amount actually credited. */
public final class RewardService {
    private RewardService() {}

    private static boolean managedPublicPlayer(PlayerProfile profile) {
        return ThePit.getInstance() != null && ThePit.getInstance().getPitBotManager() != null
                && ThePit.getInstance().getPitBotManager().ownsProfile(profile);
    }

    public static boolean isNoob(PlayerProfile profile) {
        if (EconomyV2.active()) return profile != null && profile.isLoaded()
                && (!profile.isBot() || managedPublicPlayer(profile)) && profile.getPrestige() < 10;
        return profile != null && profile.isLoaded()
                && NewConfiguration.INSTANCE.getNoobRewardsEnabled()
                && RewardMath.isNoob(profile.isBot() && !managedPublicPlayer(profile), profile.getPrestige(), profile.getLevel(),
                    NewConfiguration.INSTANCE.getNoobProtectLevel(), NewConfiguration.INSTANCE.getNoobMaxPrestige());
    }

    public static double victimMultiplier(PlayerProfile victim) {
        if (EconomyV2.active()) return 1;
        return isNoob(victim) ? NewConfiguration.INSTANCE.getNoobKilledRewardMultiplier() : 1.0;
    }

    public static Receipt grant(PlayerProfile profile, RewardSource source, double coins, double experience) {
        RewardDetail detail = RewardDetail.of(source);
        double[] candidate = profile == null ? new double[]{0, 0} : EconomyV2.generic(profile, detail, coins, experience);
        return grantDetailed(profile, detail, coins, experience, candidate[0], candidate[1]);
    }

    public static Receipt grantDetailed(PlayerProfile profile, RewardDetail detail,
            double legacyCoins, double legacyExperience, double candidateCoins, double candidateExperience) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Reward grants must run on the server thread");
        }
        if (profile == null || !profile.isLoaded() || profile.isBot() && !managedPublicPlayer(profile)) {
            return new Receipt(0, 0);
        }
        EconomyV2.migrate(profile);
        RewardSource source = detail.source;
        boolean botPrimary = (source == RewardSource.KILL || source == RewardSource.ASSIST)
                && KillRewardContext.isBot(profile.getPlayerUuid());
        RewardSource legacySource = detail.legacySource();
        boolean newcomer = legacySource.appliesNoobBonus() && !botPrimary && isNoob(profile);
        double coinFactor = newcomer ? NewConfiguration.INSTANCE.getNoobCoinRewardMultiplier() : 1.0;
        double experienceFactor = newcomer ? NewConfiguration.INSTANCE.getNoobRewardMultiplier() : 1.0;
        double bonusFactor = KillRewardContext.bonusMultiplier(profile.getPlayerUuid(), legacySource);
        coinFactor *= bonusFactor;
        experienceFactor *= bonusFactor;
        double v2Factor = detail.newcomer && !botPrimary && (detail.humanDerived() || !KillRewardContext.training(profile.getPlayerUuid()))
                ? ProgressionV2.newcomer(profile.getPrestige()) : 1;
        double candidateBonus = KillRewardContext.candidateBonus(profile.getPlayerUuid(), source);
        double effectFactor = botEffectFactor(profile, source);
        double legacyRequestedCoins = RewardMath.positive(RewardMath.positive(legacyCoins) * coinFactor * effectFactor);
        double legacyRequestedExperience = RewardMath.positive(RewardMath.positive(legacyExperience) * experienceFactor * effectFactor);
        double newCoins = RewardMath.positive(candidateCoins) * v2Factor * candidateBonus * effectFactor;
        double newExperience = RewardMath.positive(candidateExperience) * v2Factor * candidateBonus * effectFactor;
        double requestedCoins = EconomyV2.active() ? newCoins : legacyRequestedCoins;
        double requestedExperience = EconomyV2.active() ? newExperience : legacyRequestedExperience;
        double oldCoins = profile.getCoins();
        double oldExperience = profile.getExperience();
        double remaining = Math.max(0,
                LevelUtil.getLevelTotalExperience(profile.getPrestige(), 120) - oldExperience);
        profile.setExperience(oldExperience + Math.min(remaining, requestedExperience));
        if (Double.isFinite(oldCoins + requestedCoins)) {
            CoinLedger.Flow flow = detail == RewardDetail.REFUND ? CoinLedger.Flow.REFUND
                    : detail == RewardDetail.ADMIN ? CoinLedger.Flow.ADMIN
                    : detail == RewardDetail.MAIL_LEGACY ? CoinLedger.Flow.LEGACY_CREDIT : CoinLedger.Flow.MINT;
            CoinLedger.set(profile, oldCoins + requestedCoins, flow, detail.name());
        }
        double actualCoins = RewardMath.positive(profile.getCoins() - oldCoins);
        double actualExperience = RewardMath.positive(profile.getExperience() - oldExperience);
        double reserved = 0, honor = 0;
        if (EconomyV2.active() && detail.progression()) {
            double overflow = Math.max(0, requestedExperience - actualExperience);
            if (profile.getPrestige() < ProgressionV2.MAX_PRESTIGE) {
                double capacity = ProgressionV2.totalExperience(profile.getPrestige() + 1) * EconomyV2.reserveFraction();
                reserved = Math.min(overflow, Math.max(0, capacity - profile.getEconomyData().experienceReserve));
                profile.getEconomyData().experienceReserve += reserved;
            } else {
                honor = overflow;
                profile.getEconomyData().honorExperience += honor;
            }
        }
        if (actualCoins > 0) {
            if (!EconomyV2.active() || detail.progression()) profile.grindCoins(actualCoins);
            EconomyAudit.record(profile, EconomyV2.active() ? source : legacySource, actualCoins);
        }
        EconomyV2.shadow(profile, detail, actualCoins, actualExperience, newCoins, newExperience);
        if (EconomyV2.active()) EconomyAudit.detail(profile, detail, actualCoins, actualExperience, reserved, honor);
        Receipt receipt = new Receipt(actualCoins, actualExperience, reserved, honor);
        GrowthProgress.onReward(profile, detail, receipt);
        return receipt;
    }

    private static double botEffectFactor(PlayerProfile profile, RewardSource source) {
        boolean effect = source == RewardSource.PERK || source == RewardSource.ENCHANT
                || source == RewardSource.STREAK || source == RewardSource.LOOT;
        if (profile != null && profile.isBot() && source == RewardSource.BOUNTY) effect = true;
        if (!effect) return 1;
        boolean botTarget = CombatContext.targetIsBot();
        // KillRewardContext already applies the reduced factor for a Bot-targeted kill.
        if (KillRewardContext.isBotReward()) return 1;
        return botTarget || profile != null && profile.isBot() ? BotRewards.BOT_ENCHANT_MULTIPLIER : 1;
    }

    public static final class Receipt {
        private final double coins;
        private final double experience;
        private final double reservedExperience;
        private final double honorExperience;
        public Receipt(double coins, double experience) {
            this(coins, experience, 0, 0);
        }
        public Receipt(double coins, double experience, double reservedExperience, double honorExperience) {
            this.coins = coins; this.experience = experience;
            this.reservedExperience = reservedExperience; this.honorExperience = honorExperience;
        }
        public double getCoins() { return coins; }
        public double getExperience() { return experience; }
        public double getReservedExperience() { return reservedExperience; }
        public double getHonorExperience() { return honorExperience; }
    }
}
