package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** One summary per player/source/minute, plus individual large credits. No database reads. */
public final class EconomyAudit {
    private static final Map<String, Total> totals = new LinkedHashMap<>();
    private static final Map<String, Comparison> comparisons = new LinkedHashMap<>();
    private static final Map<String, Comparison> details = new LinkedHashMap<>();
    private static Logger logger;
    private static BukkitTask task;

    private EconomyAudit() { }

    public static void start(ThePit plugin) {
        if (task != null || plugin == null || plugin.getConfig() == null
                || !plugin.getConfig().getBoolean("economy-audit.enabled", true)) return;
        logger = plugin.getLogger();
        task = Bukkit.getScheduler().runTaskTimer(plugin, EconomyAudit::flush, 1200L, 1200L);
        logger.info("ECONOMY_RULES version=2026-09-08 bountyGrowth=" + BountyRules.GROWTH_PER_KILL
                + " bountyGrowthPerMinute=" + (BountyRules.GROWTH_PER_KILL * BountyRules.GROWTHS_PER_MINUTE)
                + " bountyPairMinutes=5 bountyClaimPairMinutes=10 highlanderBaseBonus=0.5 highlanderDeathPayout=false");
    }

    public static void record(PlayerProfile profile, RewardSource source, double coins) {
        if (logger == null || profile.isBot() || coins <= 0) return;
        String key = "player=" + profile.getPlayerName() + " uuid=" + profile.getPlayerUuid() + " source=" + source;
        Total total = totals.computeIfAbsent(key, ignored -> new Total());
        total.count++;
        total.coins += coins;
        total.max = Math.max(total.max, coins);
        total.balance = profile.getCoins();
        if (coins >= 500) logger.info("ECONOMY_LARGE " + key + " coins=" + coins + " balance=" + total.balance);
    }

    public static void transfer(PlayerProfile from, PlayerProfile to, double coins) {
        if (logger != null && coins > 0) logger.info("ECONOMY_TRANSFER from=" + from.getPlayerName()
                + " to=" + to.getPlayerName() + " coins=" + coins);
    }

    public static void shadow(PlayerProfile profile, RewardDetail detail, double oldCoins, double oldXp,
            double newCoins, double newXp) {
        String key = "uuid=" + profile.getPlayerUuid() + " detail=" + detail + " prestige=" + profile.getPrestige();
        Comparison total = comparisons.computeIfAbsent(key, ignored -> new Comparison());
        total.count++; total.coins += oldCoins; total.experience += oldXp;
        total.candidateCoins += newCoins; total.candidateExperience += newXp;
    }

    public static void shadowItem(PlayerProfile profile, String key, double candidate) {
        if (logger != null && !profile.isBot()) logger.info("ECONOMY_SHADOW_ITEM uuid=" + profile.getPlayerUuid()
                + " detail=" + key + " legacy=1 candidate=" + candidate);
    }

    public static void detail(PlayerProfile profile, RewardDetail detail, double coins, double xp, double reserve, double honor) {
        if (profile.isBot()) return;
        cn.charlotte.pit.data.sub.EconomyData.DailyReport report = report(profile);
        double gameplayCoins = detail.progression() ? coins : 0;
        report.coinsIn += gameplayCoins; report.experience += xp; report.reserve += reserve;
        GrowthProgress.sessionCredit(profile, gameplayCoins, 0, xp, reserve);
        report.sources.merge(detail.name(), coins, Double::sum);
        String key = "uuid=" + profile.getPlayerUuid() + " detail=" + detail;
        Comparison total = details.computeIfAbsent(key, ignored -> new Comparison());
        total.count++; total.coins += coins; total.experience += xp;
        total.reserve += reserve; total.honor += honor;
    }

    public static void spend(PlayerProfile profile, String reason, double coins) {
        if (profile.isBot() || !Double.isFinite(coins) || coins <= 0) return;
        report(profile).coinsOut += coins;
        GrowthProgress.sessionCredit(profile, 0, coins, 0, 0);
        if (logger != null) logger.info("ECONOMY_DEBIT uuid=" + profile.getPlayerUuid()
                + " reason=" + reason + " coins=" + coins);
    }

    public static cn.charlotte.pit.data.sub.EconomyData.DailyReport report(PlayerProfile profile) {
        String day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        Map<String, cn.charlotte.pit.data.sub.EconomyData.DailyReport> reports = profile.getEconomyData().reports;
        String cutoff = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(29).toString();
        reports.keySet().removeIf(key -> key.compareTo(cutoff) < 0);
        return reports.computeIfAbsent(day, ignored -> new cn.charlotte.pit.data.sub.EconomyData.DailyReport());
    }

    public static void flush() {
        if (logger != null) totals.forEach((key, total) -> logger.info("ECONOMY_MINUTE " + key
                + " count=" + total.count + " coins=" + total.coins + " max=" + total.max + " balance=" + total.balance));
        totals.clear();
        if (logger != null) comparisons.forEach((key, sum) -> logger.info("ECONOMY_SHADOW " + key
                + " count=" + sum.count + " legacyCoins=" + sum.coins + " legacyXp=" + sum.experience
                + " candidateCoins=" + sum.candidateCoins + " candidateXp=" + sum.candidateExperience));
        if (logger != null) details.forEach((key, sum) -> logger.info("ECONOMY_V2_MINUTE " + key
                + " count=" + sum.count + " coins=" + sum.coins + " xp=" + sum.experience
                + " reserve=" + sum.reserve + " honor=" + sum.honor));
        comparisons.clear(); details.clear();
        CoinLedger.flush(logger);
        CombatEconomy.saveShadow();
    }

    public static void stop() {
        flush();
        CombatEconomy.finishShadow();
        if (task != null) task.cancel();
        task = null;
        logger = null;
    }

    private static final class Total {
        private int count;
        private double coins, max, balance;
    }

    private static final class Comparison {
        int count;
        double coins, experience, candidateCoins, candidateExperience, reserve, honor;
    }
}
