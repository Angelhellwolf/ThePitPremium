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

    public static void flush() {
        if (logger != null) totals.forEach((key, total) -> logger.info("ECONOMY_MINUTE " + key
                + " count=" + total.count + " coins=" + total.coins + " max=" + total.max + " balance=" + total.balance));
        totals.clear();
    }

    public static void stop() {
        flush();
        if (task != null) task.cancel();
        task = null;
        logger = null;
    }

    private static final class Total {
        private int count;
        private double coins, max, balance;
    }
}
