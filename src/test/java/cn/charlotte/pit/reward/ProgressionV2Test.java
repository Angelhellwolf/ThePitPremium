package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.perk.PrestigeProgression;
import cn.charlotte.pit.util.level.LevelUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProgressionV2Test {
    @After public void reset() { EconomyV2.setMode(EconomyV2.Mode.OFF); }

    @Test public void fiftyPrestigesUseTwoThousandReferenceHoursWithoutAnOnlineTimeGate() {
        assertEquals(2000, ProgressionV2.referenceHoursTo(50), .01);
        assertTrue(ProgressionV2.referenceHoursTo(1) >= 4);
        assertTrue(ProgressionV2.referenceHoursTo(1) <= 6);
        double previous = 0;
        for (int p = 0; p <= 50; p++) {
            assertTrue(ProgressionV2.goldRequired(p) > previous);
            previous = ProgressionV2.goldRequired(p);
        }
    }

    @Test public void everyLevelHasPositiveCostAndEachRoundSumsExactly() {
        EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
        for (int p = 0; p <= 50; p++) {
            double sum = 0;
            for (int level = 0; level < 120; level++) {
                double cost = LevelUtil.getLevelExpRequired(p, level);
                assertTrue(cost > 0); sum += cost;
                assertEquals(level + 1, LevelUtil.getLevelByExp(p, sum));
            }
            assertEquals(ProgressionV2.totalExperience(p), sum, 0);
            assertEquals(sum, LevelUtil.getLevelTotalExperience(p, 120), 0);
        }
    }

    @Test public void migrationPreservesLevelAssetsAndOneTimeEligibility() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "MigrationCheck");
            profile.setPrestige(4);
            double original = LevelUtil.getLevelTotalExperience(4, 120);
            profile.setExperience(original); profile.setGrindedCoins(80000); profile.setCoins(12345);
            EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
            EconomyV2.migrate(profile);
            assertEquals(120, profile.getLevel());
            assertEquals(12345, profile.getCoins(), 0);
            assertEquals(80000, profile.getGrindedCoins(), 0);
            assertTrue(profile.getEconomyData().legacyEligible);
            double after = profile.getExperience();
            EconomyV2.migrate(profile);
            assertEquals(after, profile.getExperience(), 0);
            assertEquals(original, profile.getEconomyData().legacyExperience, 0);
        }
    }

    @Test public void experienceReserveIsCappedAndRefundsDoNotGrowPrestige() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "ReserveCheck");
            profile.getEconomyData().version = 2;
            profile.setExperience(ProgressionV2.totalExperience(0)); profile.setLoaded(true);
            RewardService.Receipt r = EconomyV2.grant(profile, RewardDetail.MILESTONE, 0, 0, 100, 100000);
            assertEquals(0, r.getExperience(), 0);
            assertEquals(ProgressionV2.totalExperience(1) * .1, r.getReservedExperience(), 0);
            assertEquals(100, profile.getGrindedCoins(), 0);
            assertEquals(0, EconomyV2.grant(profile, RewardDetail.REFUND, 1000, 1000, 1000, 1000).getReservedExperience(), 0);
            assertEquals(100, profile.getGrindedCoins(), 0);
        }
    }

    @Test public void topPrestigeReceivesHonorButCannotAdvanceToFiftyOne() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "HonorCheck");
            profile.getEconomyData().version = 2; profile.setPrestige(50);
            profile.setExperience(ProgressionV2.totalExperience(50)); profile.setGrindedCoins(1e9); profile.setLoaded(true);
            assertFalse(PrestigeProgression.eligible(profile, 50));
            assertEquals(500, EconomyV2.grant(profile, RewardDetail.MILESTONE, 0, 0, 0, 500).getHonorExperience(), 0);
            assertEquals(0, profile.getEconomyData().experienceReserve, 0);
        }
    }

    @Test public void standardTrainingIsFullWhileHighThroughputIsMonotonicAndSurvivesSerialization() throws Exception {
        EconomyData data = new EconomyData();
        long start = 1780000000000L;
        for (int i = 0; i < 600; i++) assertEquals(1, TrainingBudget.consume(data, 1, start + i * 6000L), 1e-6);
        EconomyData burst = new EconomyData();
        double sixty = TrainingBudget.consume(burst, 60, start);
        assertEquals(30 + 15 * .25 + 15 * .02, sixty, 1e-6);
        ObjectMapper mapper = new ObjectMapper();
        EconomyData loaded = mapper.readValue(mapper.writeValueAsBytes(burst), EconomyData.class);
        assertEquals(.02, TrainingBudget.consume(loaded, 1, start), 1e-6);
        assertEquals(.02, TrainingBudget.consume(loaded, 1, start - 100000), 1e-6);
        assertTrue(TrainingBudget.consume(loaded, 1, start + 6000) > .02);
    }

    @Test public void healthArmorAndActualDamageNormalizeWithoutOverkillOrHealingInflation() {
        assertEquals(1, TrainingBudget.normalized(20, 20, 14), 1e-6);
        assertEquals(.05, TrainingBudget.normalized(1, 1000, 14), 1e-6);
        assertEquals(.44, TrainingBudget.normalized(20, 20, 0), 1e-6);
        assertEquals(1, TrainingBudget.normalized(200, 200, 20), 1e-6);
        assertEquals(0, TrainingBudget.normalized(20, 0, 13), 0);
    }

    @Test public void shadowDoesNotMigrateProfilesOrEnableTheNewCurve() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            EconomyV2.setMode(EconomyV2.Mode.SHADOW);
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "ShadowCheck");
            EconomyV2.migrate(profile);
            assertEquals(0, profile.getEconomyData().version);
            assertEquals(65450, LevelUtil.getLevelTotalExperience(0, 120), 0);
            assertEquals(16000, EconomyV2.goldRequired(0), 0);
        }
    }
}
