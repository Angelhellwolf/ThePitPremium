package cn.charlotte.pit.data.sub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned gameplay data stored with the owning profile, never in the points ledger. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EconomyData {
    public int version;
    public long migratedAt;
    public int legacyPrestige;
    public double legacyExperience;
    public double legacyGrindedCoins;
    public boolean legacyEligible;
    public double experienceReserve;
    public double honorExperience;
    public double trainingFull = 30;
    public double trainingBuffer = 15;
    public long trainingUpdatedAt;
    public Map<String, Double> fractionalRewards = new LinkedHashMap<>();
    public Map<String, List<Long>> opponentKills = new LinkedHashMap<>();
    public int passiveDeaths;
    public long passiveUpdatedAt;
    public double lifeRealOutput;
    public double lifeBaseCoins;
    public double lifeBaseExperience;
    public double lifeEconomicUnits;
    public double lifeRawKills;
    public boolean lifeStreakPaid;
    public long lifeSerial;
    public double botRewardFactor;
    public List<Contribution> recentContributions = new ArrayList<>();
    public double newcomerUnits;
    public int newcomerMilestones;
    public String lastQuestRewardKey = "";
    public List<String> eventParticipation = new ArrayList<>();
    public List<WeekProgress> weeks = new ArrayList<>();
    public Map<String, DailyReport> reports = new LinkedHashMap<>();
    public Map<String, BuildPreset> builds = new LinkedHashMap<>();
    public Map<String, Map<String, String>> trainingPresets = new LinkedHashMap<>();
    public List<String> cosmetics = new ArrayList<>();
    public String selectedCosmetic = "";
    public Deployment deployment;

    public EconomyData copy() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJson(this), EconomyData.class);
    }

    public void newLife() {
        lifeRealOutput = lifeBaseCoins = lifeBaseExperience = lifeEconomicUnits = lifeRawKills = 0;
        lifeStreakPaid = false;
        botRewardFactor = 0;
        lifeSerial++;
        recentContributions.clear();
    }

    public static class Contribution {
        public double raw, units, humanUnits;
        public Contribution() { }
        public Contribution(double raw, double units) { this(raw, units, units); }
        public Contribution(double raw, double units, double humanUnits) { this.raw = raw; this.units = units; this.humanUnits = humanUnits; }
    }

    public static class WeekProgress {
        public long start;
        public Map<String, Double> progress = new LinkedHashMap<>();
        public List<String> claimed = new ArrayList<>();
        public boolean bonusClaimed;
    }

    public static class DailyReport {
        public double coinsIn, coinsOut, experience, reserve, normalizedKills;
        public double transferredIn, transferredOut, escrowChange, refunds;
        public Map<String, Double> sources = new LinkedHashMap<>();
    }

    public static class BuildPreset {
        public Map<Integer, PerkData> selected = new LinkedHashMap<>();
        public List<String> autoBuy = new ArrayList<>();
    }

    public static class Deployment {
        public String world;
        public double x, y, z;
        public float yaw, pitch;
    }
}
