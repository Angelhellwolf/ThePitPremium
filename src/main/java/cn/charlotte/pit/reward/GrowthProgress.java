package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.event.PitQuestCompleteEvent;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Optional goals reward existing play. There are no daily-login streaks or paid reward multipliers. */
public final class GrowthProgress implements Listener {
    public enum Goal {
        KILLS("战斗练习", 200), QUESTS("完成挑战", 5), FISHING("钓鱼收获", 30), EXPLORING("探索收集", 30), EVENTS("公共事件", 2);
        public final String title;
        public final double target;
        Goal(String title, double target) { this.title = title; this.target = target; }
    }
    private static final Map<UUID, double[]> sessions = new HashMap<>();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long WEEK = 7L * 86400000;

    public static int presetSlots(PlayerProfile profile) {
        SupporterAccount account = profile.getSupporterAccount();
        if (account == null || !account.isPermanent()) return 1;
        SupporterTier tier = account.activeTier(System.currentTimeMillis());
        return tier == SupporterTier.SACRED ? 12 : tier == SupporterTier.MYTHIC ? 6 : 3;
    }

    public static int reportDays(PlayerProfile profile) {
        SupporterAccount account = profile.getSupporterAccount();
        if (account == null || !account.isPermanent()) return 1;
        SupporterTier tier = account.activeTier(System.currentTimeMillis());
        return tier == SupporterTier.SACRED ? 30 : tier == SupporterTier.MYTHIC ? 7 : 1;
    }

    public static long weekStart(long now) {
        return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    public static EconomyData.WeekProgress currentWeek(EconomyData data, long now) {
        long start = weekStart(now);
        for (EconomyData.WeekProgress week : data.weeks) start = Math.max(start, week.start);
        final long cutoff = start - WEEK;
        data.weeks.removeIf(week -> week.start < cutoff);
        for (EconomyData.WeekProgress week : data.weeks) if (week.start == start) return week;
        EconomyData.WeekProgress result = new EconomyData.WeekProgress(); result.start = start;
        data.weeks.add(result); data.weeks.sort(Comparator.comparingLong(week -> week.start));
        while (data.weeks.size() > 2) data.weeks.remove(0);
        return result;
    }

    public static void action(PlayerProfile profile, Goal goal, double amount) {
        if (!EconomyV2.active() || profile == null || !profile.isLoaded() || profile.isBot() || amount <= 0) return;
        EconomyData data = profile.getEconomyData();
        currentWeek(data, System.currentTimeMillis());
        for (EconomyData.WeekProgress week : data.weeks) {
            if (week.claimed.size() >= 3) continue;
            week.progress.put(goal.name(), Math.min(goal.target,
                    week.progress.getOrDefault(goal.name(), 0.0) + RewardMath.positive(amount)));
            break;
        }
    }

    public static boolean claim(PlayerProfile profile, long weekStart, Goal goal) {
        if (!EconomyV2.active() || !profile.isLoaded() || profile.isBot()) return false;
        currentWeek(profile.getEconomyData(), System.currentTimeMillis());
        for (EconomyData.WeekProgress week : profile.getEconomyData().weeks) {
            if (week.start != weekStart || week.claimed.size() >= 3 || week.claimed.contains(goal.name())
                    || week.progress.getOrDefault(goal.name(), 0.0) < goal.target) continue;
            double side = ProgressionV2.sideScale(profile.getPrestige());
            week.claimed.add(goal.name());
            double coins = 250 * side, xp = 1000 * side;
            if (week.claimed.size() == 3 && !week.bonusClaimed) {
                week.bonusClaimed = true; coins += 500 * side; xp += 2000 * side;
            }
            RewardService.Receipt receipt = EconomyV2.grant(profile, RewardDetail.WEEKLY, 0, 0, coins, xp);
            tell(profile, "&a周目标完成 &6+" + number(receipt.getCoins()) + " 金币 &b+"
                    + number(receipt.getExperience()) + " 经验 &7（储备 +" + number(receipt.getReservedExperience()) + "）");
            return true;
        }
        return false;
    }

    public static void onReward(PlayerProfile profile, RewardDetail detail, RewardService.Receipt receipt) {
        if (!EconomyV2.active() || profile.isBot() || detail == RewardDetail.MILESTONE || detail == RewardDetail.WEEKLY
                || !detail.progression()) return;
        if (detail == RewardDetail.KILL && receipt.getCoins() > 0) {
            double units = KillRewardContext.units(profile.getPlayerUuid());
            profile.getEconomyData().newcomerUnits += units;
            action(profile, Goal.KILLS, units);
            if (units > 0) milestone(profile, 1, 50);
            if (profile.getEconomyData().newcomerUnits >= 10) milestone(profile, 2, 150);
        }
        if (detail == RewardDetail.FISH_CATCH) action(profile, Goal.FISHING, 1);
        if (detail == RewardDetail.GROUND_GOLD) action(profile, Goal.EXPLORING, 1);
        if (profile.getLevel() >= 60) milestone(profile, 8, 500);
    }

    private static void milestone(PlayerProfile profile, int flag, int coins) {
        EconomyData data = profile.getEconomyData();
        if (profile.getPrestige() > 2 || (data.newcomerMilestones & flag) != 0) return;
        data.newcomerMilestones |= flag;
        RewardService.Receipt receipt = EconomyV2.grant(profile, RewardDetail.MILESTONE, 0, 0, coins, coins * 4);
        tell(profile, "&a成长目标达成 &6+" + number(receipt.getCoins()) + " 金币 &b+" + number(receipt.getExperience()) + " 经验");
    }

    public static void event(PlayerProfile profile, String id) {
        if (!EconomyV2.active() || profile.isBot() || profile.getEconomyData().eventParticipation.contains(id)) return;
        profile.getEconomyData().eventParticipation.add(id);
        while (profile.getEconomyData().eventParticipation.size() > 128) profile.getEconomyData().eventParticipation.remove(0);
        action(profile, Goal.EVENTS, 1);
    }

    @EventHandler public void quest(PitQuestCompleteEvent event) {
        if (!EconomyV2.active()) return;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        String key = event.getQuestData().getInternalName() + ':' + event.getQuestData().getStartTime();
        if (key.equals(profile.getEconomyData().lastQuestRewardKey)) return;
        profile.getEconomyData().lastQuestRewardKey = key;
        action(profile, Goal.QUESTS, 1); milestone(profile, 4, 300);
    }

    @EventHandler public void loaded(PitProfileLoadedEvent event) {
        if (!EconomyV2.active()) return;
        PlayerProfile p = event.getPlayerProfile();
        EconomyV2.migrate(p);
        sessions.put(p.getPlayerUuid(), new double[4]);
    }

    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    public static double[] session(PlayerProfile profile) {
        return sessions.getOrDefault(profile.getPlayerUuid(), new double[4]).clone();
    }

    public static void sessionCredit(PlayerProfile profile, double coins, double expense, double xp, double reserve) {
        double[] values = sessions.computeIfAbsent(profile.getPlayerUuid(), ignored -> new double[4]);
        values[0] += coins; values[1] += expense; values[2] += xp; values[3] += reserve;
    }

    public static String badge(PlayerProfile profile) {
        if (!EconomyV2.active() || profile.isNicked() || profile.isBot()) return "";
        String selected = profile.getEconomyData().selectedCosmetic;
        if (!profile.getEconomyData().cosmetics.contains(selected)) return "";
        return "bronze".equals(selected) ? "&6[历练者] " : "silver".equals(selected) ? "&b[远征者] "
                : "gold".equals(selected) ? "&d[天坑传奇] " : "";
    }

    public static boolean cosmetic(PlayerProfile profile, String key, int price) {
        if (!EconomyV2.active() || !profile.isLoaded() || profile.isBot()) return false;
        int expected = "bronze".equals(key) ? 20000 : "silver".equals(key) ? 80000 : "gold".equals(key) ? 200000 : -1;
        if (expected < 0 || price != expected) return false;
        if (profile.getEconomyData().cosmetics.contains(key)) { profile.getEconomyData().selectedCosmetic = key; return true; }
        if (price < 0 || profile.getCoins() < price) return false;
        CoinLedger.set(profile, profile.getCoins() - price, CoinLedger.Flow.SPEND, "cosmetic." + key);
        profile.getEconomyData().cosmetics.add(key); profile.getEconomyData().selectedCosmetic = key;
        return true;
    }

    public static String number(double value) { return String.format(java.util.Locale.ROOT, "%,.0f", value); }
    private static void tell(PlayerProfile p, String message) {
        Player player = Bukkit.getPlayer(p.getPlayerUuid());
        if (player != null && player.isOnline()) player.sendMessage(CC.translate(message));
    }
}
