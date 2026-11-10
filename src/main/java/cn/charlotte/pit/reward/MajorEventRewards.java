package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.junxu.PublicPitParticipants;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Economic rankings exclude idle/absent participants without changing an event's gameplay score. */
public final class MajorEventRewards implements Listener {
    private static final Map<String, Round> rounds = new HashMap<>();
    private static final Map<String, Integer> MINIMUM = Map.ofEntries(
            Map.entry("cake", 1), Map.entry("dragon_egg", 1), Map.entry("quick_math_event", 1),
            Map.entry("care_package", 1), Map.entry("ham", 1), Map.entry("patronage", 2),
            Map.entry("hunt", 2), Map.entry("koth", 2), Map.entry("everyone_bounty_event", 2),
            Map.entry("auction", 2), Map.entry("spire", 4), Map.entry("rage_pit", 4),
            Map.entry("red_vs_blue", 6), Map.entry("block_head", 999), Map.entry("squads", 999));

    public static int minimum(String name, int original) {
        return EconomyV2.active() ? MINIMUM.getOrDefault(name, original) : original;
    }

    public static void start(String name) {
        if (EconomyV2.active() || EconomyV2.observing()) rounds.put(name, new Round());
    }

    public static void end(String name) { rounds.remove(name); }

    public static void mark(String name, Player player) {
        Round round = rounds.get(name);
        if (round == null || !PublicPitParticipants.isHuman(player)) return;
        Activity activity = round.players.computeIfAbsent(player.getUniqueId(), ignored -> new Activity());
        activity.lastAction = System.currentTimeMillis();
        activity.scored = true;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (Round round : rounds.values()) for (Map.Entry<UUID, Activity> entry : round.players.entrySet()) {
            Activity activity = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline() && PublicPitParticipants.isHuman(player)
                    && now - activity.lastAction <= 10000) activity.activeMillis += 1000;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().distanceSquared(event.getTo()) < .01) return;
        if (!PublicPitParticipants.isHuman(event.getPlayer())) return;
        for (Round round : rounds.values()) {
            Activity activity = round.players.get(event.getPlayer().getUniqueId());
            if (activity != null && activity.scored) activity.lastAction = System.currentTimeMillis();
        }
    }

    private static boolean qualified(Round round, UUID id) {
        if (round == null) return false;
        Activity a = round.players.get(id);
        Player player = Bukkit.getPlayer(id);
        return a != null && a.scored && a.activeMillis >= 120000 && player != null
                && player.isOnline() && PublicPitParticipants.isHuman(player);
    }

    public static Result settle(Player player, String name, double oldCoins, int oldRenown,
            int rank, Map<UUID, Integer> ranks, boolean objective, int paparazzi) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        Round round = rounds.get(name);
        boolean eligible = qualified(round, player.getUniqueId());
        int count = 0, ahead = 0;
        if (ranks != null) for (Map.Entry<UUID, Integer> entry : ranks.entrySet()) {
            if (!qualified(round, entry.getKey())) continue;
            count++; if (entry.getValue() < rank) ahead++;
        }
        double[] award = baseAward(eligible, ahead + 1, count, objective);
        double scale = ProgressionV2.sideScale(profile.getPrestige());
        int renown = (int) award[2];
        if (eligible && ThePit.getInstance().getPitConfig().isGenesisEnable()
                && profile.getGenesisData().getTier() >= 5 && renown > 0) renown++;
        double factor = 1 + .5 * Math.max(0, paparazzi);
        double newCoins = award[0] * scale * factor;
        double newXp = award[1] * scale * factor;
        renown += (int) Math.floor((factor - 1) * renown);
        int confidenceBonus = eligible && PlayerUtil.isPlayerUnlockedPerk(player, "self_confidence")
                ? selfConfidenceBonus(rank) : 0;
        if (EconomyV2.active()) {
            if (round == null || !round.paid.add(player.getUniqueId())) return new Result(0, 0, 0);
            if (eligible && paparazzi > 0) consumePaparazzi(player);
        }
        RewardService.Receipt receipt = EconomyV2.grant(profile, RewardDetail.MAJOR_EVENT,
                oldCoins, 0, newCoins, newXp);
        double confidenceCoins = 0;
        if ((EconomyV2.active() || EconomyV2.observing()) && confidenceBonus > 0) {
            RewardService.Receipt confidenceReceipt = EconomyV2.grant(profile, RewardDetail.SELF_CONFIDENCE,
                    0, 0, confidenceBonus, 0);
            confidenceCoins = confidenceReceipt.getCoins();
            if (confidenceCoins > 0) player.sendMessage(CC.translate(String.format(java.util.Locale.ROOT,
                    "&6自信! &7大型事件排名奖励: &6+%,.0f 硬币", confidenceCoins)));
        }
        if (eligible && EconomyV2.active()) GrowthProgress.event(profile, name + ':' + round.id);
        if (receipt.getExperience() > 0 || receipt.getReservedExperience() > 0)
            player.sendMessage(CC.translate(String.format(java.util.Locale.ROOT,
                    "&6事件奖励 &b+%.2f 经验 &7（储备 +%.2f）", receipt.getExperience(), receipt.getReservedExperience())));
        return new Result(receipt.getCoins() + confidenceCoins, receipt.getExperience(), EconomyV2.active() ? renown : oldRenown);
    }

    static int selfConfidenceBonus(int rank) {
        if (rank < 1) return 0;
        if (rank <= 5) return 5000;
        if (rank <= 10) return 2500;
        if (rank <= 15) return 1000;
        return 0;
    }

    public static double[] baseAward(boolean eligible, int rank, int count, boolean objective) {
        if (!eligible) return new double[]{0, 0, 0};
        double gold = 500; int renown = 0;
        if (count >= 5 && rank <= (int) Math.floor(count * .2)) { gold = 2000; renown = 2; }
        else if (count >= 5 && rank <= (int) Math.floor(count * .6)) { gold = 1000; renown = 1; }
        if (objective) { gold += 500; renown++; }
        return new double[]{gold, gold * 4, renown};
    }

    private static void consumePaparazzi(Player player) {
        MythicLeggingsItem pants = new MythicLeggingsItem();
        pants.loadFromItemStack(player.getInventory().getLeggings());
        if (!pants.isEnchanted() || pants.getLive() < 2) throw new IllegalStateException("Paparazzi changed before settlement");
        if (pants.getMaxLive() > 0 && pants.getLive() <= 2) player.getInventory().setLeggings(new ItemStack(Material.AIR));
        else { pants.setLive(pants.getLive() - 2); player.getInventory().setLeggings(pants.toItemStack()); }
    }

    public static final class Result {
        public final double coins, experience;
        public final int renown;
        Result(double coins, double experience, int renown) { this.coins = coins; this.experience = experience; this.renown = renown; }
    }
    private static final class Round {
        final String id = UUID.randomUUID().toString();
        final Map<UUID, Activity> players = new HashMap<>();
        final Set<UUID> paid = new HashSet<>();
    }
    private static final class Activity { long lastAction, activeMillis; boolean scored; }
}
