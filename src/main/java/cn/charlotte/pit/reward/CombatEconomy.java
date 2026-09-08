package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reward quality, not an assertion that a real account is human-controlled. */
public final class CombatEconomy {
    private static final java.util.Map<String, EconomyData> shadow = new java.util.LinkedHashMap<>();
    private static java.nio.file.Path shadowFile;
    private static final java.util.concurrent.ExecutorService shadowWriter = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "pit-economy-shadow-writer"); thread.setDaemon(true); return thread;
    });
    private static java.util.concurrent.Future<?> shadowWrite;
    private CombatEconomy() { }

    public static EconomyData state(PlayerProfile profile) {
        return EconomyV2.observing() ? shadow.computeIfAbsent(profile.getUuid(), ignored -> new EconomyData()) : profile.getEconomyData();
    }

    public static void loadShadow(java.nio.file.Path file) {
        shadowFile = file;
        if (!java.nio.file.Files.exists(file)) return;
        try (java.io.Reader input = java.nio.file.Files.newBufferedReader(file)) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, EconomyData>>() { }.getType();
            java.util.Map<String, EconomyData> stored = new com.google.gson.Gson().fromJson(input, type);
            if (stored != null) shadow.putAll(stored);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read shadow reward state; retain the file for diagnosis", failure);
        }
    }

    public static void saveShadow() {
        if (!EconomyV2.observing() || shadowFile == null) return;
        if (shadowWrite != null && !shadowWrite.isDone()) return;
        java.util.Map<String, EconomyData> snapshot = new java.util.LinkedHashMap<>();
        shadow.forEach((id, source) -> {
            EconomyData d = new EconomyData();
            d.trainingFull = source.trainingFull; d.trainingBuffer = source.trainingBuffer; d.trainingUpdatedAt = source.trainingUpdatedAt;
            d.passiveDeaths = source.passiveDeaths; d.passiveUpdatedAt = source.passiveUpdatedAt; d.lifeRealOutput = source.lifeRealOutput;
            d.lifeSerial = source.lifeSerial; d.lifeBaseCoins = source.lifeBaseCoins; d.lifeBaseExperience = source.lifeBaseExperience;
            d.botRewardFactor = source.botRewardFactor;
            d.lifeRawKills = source.lifeRawKills; d.lifeEconomicUnits = source.lifeEconomicUnits;
            d.fractionalRewards.putAll(source.fractionalRewards);
            source.opponentKills.forEach((other, times) -> d.opponentKills.put(other, new ArrayList<>(times)));
            for (EconomyData.Contribution c : source.recentContributions) d.recentContributions.add(new EconomyData.Contribution(c.raw, c.units, c.humanUnits));
            snapshot.put(id, d);
        });
        shadowWrite = shadowWriter.submit(() -> {
            try {
                java.nio.file.Path temporary = shadowFile.resolveSibling(shadowFile.getFileName() + ".pending");
                java.nio.file.Files.writeString(temporary, new com.google.gson.Gson().toJson(snapshot));
                java.nio.file.Files.move(temporary, shadowFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException failure) {
                org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Could not persist economy observation; activation requires review", failure);
            }
        });
    }

    public static void finishShadow() {
        if (!EconomyV2.observing() || shadowFile == null) return;
        try {
            if (shadowWrite != null) shadowWrite.get(10, java.util.concurrent.TimeUnit.SECONDS);
            saveShadow();
            if (shadowWrite != null) shadowWrite.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception failure) {
            org.bukkit.Bukkit.getLogger().warning("Economy observation was not fully flushed: " + failure.getClass().getSimpleName());
        } finally { shadowWriter.shutdown(); }
    }

    public static double trainingUnits(PlayerProfile victim, LivingEntity entity) {
        double damage = victim.getDamageMap().values().stream()
                .filter(hit -> !hit.getTimer().hasExpired()).mapToDouble(hit -> RewardMath.positive(hit.getDamage())).sum();
        cn.charlotte.pit.ThePit plugin = cn.charlotte.pit.ThePit.getInstance();
        cn.charlotte.pit.junxu.JunxuManager training = plugin == null ? null : plugin.getJunxuManager();
        // Private difficulty settings do not reduce the earned training share.
        if (victim.isBot() && entity instanceof Player && training != null
                && training.isTraining((Player) entity, entity.getLocation())) return damage > 0 ? 1 : 0;
        int armor = 14;
        if (entity instanceof Player) {
            armor = 0;
            for (ItemStack item : ((Player) entity).getInventory().getArmorContents()) armor += armor(item);
        }
        return TrainingBudget.normalized(entity.getMaxHealth(), damage, armor);
    }

    private static int armor(ItemStack stack) {
        if (stack == null) return 0;
        String name = stack.getType().name();
        int slot = name.endsWith("HELMET") ? 0 : name.endsWith("CHESTPLATE") ? 1 : name.endsWith("LEGGINGS") ? 2 : name.endsWith("BOOTS") ? 3 : -1;
        if (slot < 0) return 0;
        int[] points = name.startsWith("DIAMOND") ? new int[]{3,8,6,3}
                : name.startsWith("IRON") ? new int[]{2,6,5,2}
                : name.startsWith("CHAINMAIL") ? new int[]{2,5,4,1}
                : name.startsWith("GOLD") ? new int[]{2,5,3,1} : new int[]{1,3,2,1};
        return points[slot];
    }

    public static boolean allowExtras(PlayerProfile attacker, PlayerProfile victim, long now, boolean countKill) {
        if (!EconomyV2.active() && !EconomyV2.observing()) return true;
        if (victim.isBot() || state(victim).passiveDeaths >= 5) return false;
        int total = pairCount(attacker, victim.getPlayerUuid(), now) + pairCount(victim, attacker.getPlayerUuid(), now);
        if (countKill) {
            state(attacker).opponentKills.computeIfAbsent(victim.getUuid(), ignored -> new ArrayList<>()).add(now);
        }
        return total < 10;
    }

    private static int pairCount(PlayerProfile profile, UUID other, long now) {
        state(profile).opponentKills.values().forEach(times -> times.removeIf(at -> now - at > 600000));
        state(profile).opponentKills.values().removeIf(List::isEmpty);
        List<Long> values = state(profile).opponentKills.get(other.toString());
        return values == null ? 0 : values.size();
    }

    public static void humanDamage(PlayerProfile attacker, PlayerProfile victim, double damage) {
        if ((!EconomyV2.active() && !EconomyV2.observing()) || attacker.isBot() || victim.isBot()) return;
        state(attacker).lifeRealOutput += RewardMath.positive(damage);
    }

    public static void finishHumanLife(PlayerProfile profile, boolean combatDeath, long now) {
        if ((!EconomyV2.active() && !EconomyV2.observing()) || profile.isBot()) return;
        EconomyData d = state(profile);
        if (now - d.passiveUpdatedAt > 900000) d.passiveDeaths = 0;
        if (combatDeath) d.passiveDeaths = d.lifeRealOutput >= 2 ? 0 : Math.min(5, d.passiveDeaths + 1);
        d.passiveUpdatedAt = now;
    }

    public static double deathMultiplier(PlayerProfile victim) {
        int deaths = state(victim).passiveDeaths;
        return deaths >= 5 ? .2 : deaths >= 3 ? .5 : 1;
    }

    public static void contribution(PlayerProfile profile, double raw, double units) {
        if ((!EconomyV2.active() && !EconomyV2.observing()) || raw <= 0) return;
        EconomyData data = state(profile);
        data.lifeRawKills += raw; data.lifeEconomicUnits += units;
        double earned = Math.max(0, Math.min(raw, units));
        data.recentContributions.add(new EconomyData.Contribution(raw, earned,
                KillRewardContext.training(profile.getPlayerUuid()) ? 0 : earned));
        double sum = data.recentContributions.stream().mapToDouble(c -> c.raw).sum();
        while (data.recentContributions.size() > 1 && sum - data.recentContributions.get(0).raw >= 500)
            sum -= data.recentContributions.remove(0).raw;
        if (EconomyV2.active() && !profile.isBot()) EconomyAudit.report(profile).normalizedKills += units;
    }

    public static double recentRatio(PlayerProfile profile, double window) {
        return recentRatio(profile, window, false);
    }

    public static double recentHumanRatio(PlayerProfile profile, double window) {
        return recentRatio(profile, window, true);
    }

    private static double recentRatio(PlayerProfile profile, double window, boolean humanOnly) {
        if (!EconomyV2.active() && !EconomyV2.observing()) return 1;
        double raw = 0, units = 0;
        List<EconomyData.Contribution> list = state(profile).recentContributions;
        for (int i = list.size() - 1; i >= 0 && raw < window; i--) {
            EconomyData.Contribution c = list.get(i);
            double take = Math.min(c.raw, window - raw);
            if (c.raw > 0) { raw += take; units += (humanOnly ? c.humanUnits : c.units) * take / c.raw; }
        }
        return window <= 0 ? 0 : Math.max(0, Math.min(1, units / window));
    }

    /** Fractional item/renown units are durable, so switching builds cannot create free whole items. */
    public static int wholeReward(PlayerProfile profile, String key, double units) {
        if (!EconomyV2.active()) return (int) units;
        double amount = profile.getEconomyData().fractionalRewards.getOrDefault(key, 0.0) + RewardMath.positive(units);
        int whole = (int) Math.min(Integer.MAX_VALUE, Math.floor(amount + 1e-9));
        profile.getEconomyData().fractionalRewards.put(key, Math.max(0, amount - whole));
        return whole;
    }

    public static boolean claimRecent(PlayerProfile profile, String key, double window) {
        if (EconomyV2.observing()) {
            double units = state(profile).fractionalRewards.getOrDefault(key, 0.0) + recentRatio(profile, window);
            double whole = Math.floor(units + 1e-9);
            state(profile).fractionalRewards.put(key, Math.max(0, units - whole));
            EconomyAudit.shadowItem(profile, key, whole);
            return true;
        }
        return !EconomyV2.active() || wholeReward(profile, key, recentRatio(profile, window)) > 0;
    }
}
