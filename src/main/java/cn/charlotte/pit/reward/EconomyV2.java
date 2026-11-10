package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.menu.prestige.button.PrestigeStatusButton;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

public final class EconomyV2 {
    public enum Mode { OFF, SHADOW, ACTIVE }
    private static volatile Mode mode = Mode.OFF;
    private static Logger logger;
    private static long shadowStarted;
    static double fullRate = 10, fullCapacity = 30, bufferRate = 5, bufferCapacity = 15, bufferMultiplier = .25, overflowMultiplier = .02;
    static double privateReducedMultiplier = .25;
    private static double reserveFraction = .1;
    private static double honorStep = 100000;
    private static double botRewardVariation = .10;
    private EconomyV2() { }

    public static boolean active() { return mode == Mode.ACTIVE; }
    public static boolean observing() { return mode == Mode.SHADOW; }
    public static Mode mode() { return mode; }

    public static void load(ThePit plugin) {
        File file = new File(plugin.getDataFolder(), "economy-v2.yml");
        if (!file.exists()) plugin.saveResource("economy-v2.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mode = Mode.valueOf(config.getString("mode", "SHADOW").toUpperCase(Locale.ROOT));
        fullRate = positive(config, "training.full-units-per-minute", 10, 120);
        fullCapacity = positive(config, "training.full-capacity", 30, 360);
        bufferRate = positive(config, "training.buffer-units-per-minute", 5, 120);
        bufferCapacity = positive(config, "training.buffer-capacity", 15, 360);
        bufferMultiplier = positive(config, "training.buffer-multiplier", .25, 1);
        overflowMultiplier = positive(config, "training.overflow-multiplier", .02, bufferMultiplier);
        privateReducedMultiplier = positive(config, "training.private-reduced-multiplier", .25, 1);
        reserveFraction = positive(config, "experience-reserve-fraction", .1, .1);
        honorStep = positive(config, "growth.honor-experience-per-level", 100000, 1e9);
        botRewardVariation = positive(config, "training.reward-variation", .10, .50);
        if (config.getDouble("reference-hours-to-p50", 2000) != ProgressionV2.TARGET_HOURS
                || config.getInt("growth.weekly-carry-weeks", 2) != 2)
            throw new IllegalArgumentException("Unsupported progression/weekly rule version");
        String expectedDatabase = config.getString("required-game-database", "");
        if (active() && !expectedDatabase.isEmpty() && !expectedDatabase.equals(plugin.getPitConfig().getDatabaseName()))
            throw new IllegalStateException("V2 activation refused outside the verified gameplay database");
        String expectedCommerce = config.getString("required-commerce-scope", "");
        if (mode != Mode.OFF && !expectedCommerce.isEmpty()
                && !expectedCommerce.equals(plugin.getConfig().getString("supporter-commerce-scope", plugin.getPitConfig().getDatabaseName())))
            throw new IllegalStateException("Paid account scope differs from the preserved authoritative ledger");
        logger = plugin.getLogger();
        org.bukkit.Bukkit.getPluginManager().registerEvents(new MajorEventRewards(), plugin);
        org.bukkit.Bukkit.getPluginManager().registerEvents(new GrowthProgress(), plugin);
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, MajorEventRewards::tick, 20L, 20L);
        if (observing()) CombatEconomy.loadShadow(plugin.getDataFolder().toPath().resolve("economy-shadow-state.json"));
        shadowStarted = config.getLong("shadow-started-at", 0);
        if (observing() && shadowStarted == 0) {
            shadowStarted = System.currentTimeMillis(); config.set("shadow-started-at", shadowStarted);
            try { config.save(file); } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot persist observation start", failure); }
        }
        if (config.getInt("maximum-prestige", 50) != 50)
            throw new IllegalArgumentException("Economy v2 requires the approved P50 curve");
        if (active()) PrestigeStatusButton.limit = ProgressionV2.MAX_PRESTIGE;
        logger.info("ECONOMY_V2 mode=" + mode + " version=2 maxPrestige=50 referenceHours="
                + String.format(Locale.ROOT, "%.2f", ProgressionV2.referenceHoursTo(50))
                + " shadowStarted=" + shadowStarted);
    }

    /** Test-only configuration also permits deterministic pure regression checks. */
    public static void setMode(Mode next) { mode = next; }
    public static double reserveFraction() { return reserveFraction; }
    public static double honorStep() { return honorStep; }
    public static double botRewardVariation() { return botRewardVariation; }

    private static double positive(YamlConfiguration config, String path, double fallback, double maximum) {
        double value = config.getDouble(path, fallback);
        if (!Double.isFinite(value) || value <= 0 || value > maximum) throw new IllegalArgumentException("Invalid economy value: " + path);
        return value;
    }

    public static double goldRequired(int prestige) {
        return active() ? ProgressionV2.goldRequired(prestige) : 16000.0 * (prestige + 1);
    }

    public static double sideScale(PlayerProfile profile) {
        return active() ? ProgressionV2.sideScale(profile.getPrestige()) : 1;
    }

    public static void migrate(PlayerProfile profile) {
        if (!active() || profile.getEconomyData().version >= 2) return;
        EconomyData data = profile.getEconomyData();
        double oldXp = profile.getExperience();
        int p = profile.getPrestige();
        int level = 0;
        double before = 0;
        while (level < 120 && oldXp >= before + legacyLevelExperience(p, level)) {
            before += legacyLevelExperience(p, level++);
        }
        double fraction = level == 120 ? 0 : Math.max(0, Math.min(1,
                (oldXp - before) / legacyLevelExperience(p, level)));
        double mapped = ProgressionV2.experienceAtLevel(p, level)
                + (level == 120 ? 0 : ProgressionV2.experienceForLevel(p, level) * fraction);
        data.legacyPrestige = p;
        data.legacyExperience = oldXp;
        data.legacyGrindedCoins = profile.getGrindedCoins();
        data.legacyEligible = level == 120 && p < 35 && profile.getGrindedCoins() >= 16000.0 * (p + 1);
        data.migratedAt = System.currentTimeMillis();
        // Mark before invoking a setter that refreshes a live player's level.
        data.version = 2;
        boolean wasLoaded = profile.isLoaded();
        profile.setLoaded(false);
        profile.setExperience(Math.min(ProgressionV2.totalExperience(p), Math.max(oldXp, mapped)));
        profile.setLoaded(wasLoaded);
        if (oldXp > ProgressionV2.totalExperience(p) && p < 50)
            data.experienceReserve = Math.min(ProgressionV2.totalExperience(p + 1) * reserveFraction,
                    oldXp - ProgressionV2.totalExperience(p));
        data.newLife();
    }

    public static double legacyLevelExperience(int prestige, int level) {
        int[] values = {15,30,50,75,125,250,600,800,900,1000,1200,1500};
        return Math.round(values[Math.max(0, Math.min(11, level / 10))] * Math.pow(1.1, prestige));
    }

    /** Already converted rewards use an explicit detail to avoid applying a second conversion. */
    public static RewardService.Receipt grant(PlayerProfile profile, RewardDetail detail,
            double oldCoins, double oldXp, double newCoins, double newXp) {
        if (mode == Mode.OFF) return RewardService.grant(profile, detail.legacySource(), oldCoins, oldXp);
        return RewardService.grantDetailed(profile, detail, oldCoins, oldXp, newCoins, newXp);
    }

    public static RewardService.Receipt streak(PlayerProfile profile, double window, double coins, double xp) {
        double scale = ProgressionV2.sideScale(profile.getPrestige()) * CombatEconomy.recentHumanRatio(profile, window);
        return grant(profile, RewardDetail.STREAK_DERIVED, coins, xp, coins * scale, xp * 4 * scale);
    }

    public static double[] generic(PlayerProfile profile, RewardDetail detail, double coins, double xp) {
        double scale = ProgressionV2.sideScale(profile.getPrestige());
        if (detail.stored()) return new double[]{coins, xp};
        if (detail == RewardDetail.KILL || detail == RewardDetail.ASSIST) {
            double[] candidate = observing() ? KillRewardContext.candidate(profile.getPlayerUuid()) : null;
            return candidate == null ? new double[]{coins, xp * 4} : candidate;
        }
        return new double[]{coins * scale, xp * 4 * scale};
    }

    public static void shadow(PlayerProfile profile, RewardDetail detail, double oldCoins, double oldXp,
            double newCoins, double newXp) {
        if (observing() && logger != null && !profile.isBot())
            EconomyAudit.shadow(profile, detail, oldCoins, oldXp, newCoins, newXp);
    }
}
