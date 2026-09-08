package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EconomyV2RulesTest {
    private MockedStatic<Bukkit> bukkit;
    @Before public void setUp() {
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
    }
    @After public void tearDown() { EconomyV2.setMode(EconomyV2.Mode.OFF); bukkit.close(); }
    private PlayerProfile profile(int prestige) {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "V2Rules");
        p.getEconomyData().version = 2; p.getEconomyData().newcomerMilestones = 15;
        p.setPrestige(prestige); p.setLoaded(true); return p;
    }

    @Test public void percentagesDoNotCompoundAndFlatBonusesDoNotBecomePercentageBases() {
        RewardModifiers m = new RewardModifiers(10, 0, 0);
        AtomicDouble g = new AtomicDouble(10), x = new AtomicDouble(10);
        m.apply("hermes", g, x, () -> g.addAndGet(12));
        m.apply("gold_explorer_enchant", g, x, () -> g.addAndGet(g.get() * .45));
        m.apply("CoinBoost", g, x, () -> g.addAndGet(g.get() * .5));
        assertEquals(31.5, g.get(), 1e-6);
        assertEquals(12, m.fixedGold(), 0);
        m.apply("grim_reaper_enchant", g, x, () -> { g.set(g.get() * .2); x.set(x.get() * .2); });
        assertEquals(6.3, g.get(), 1e-6); assertEquals(2, x.get(), 1e-6);
    }

    @Test public void passiveAccountEvidencePersistsAndOnlyRealAcceptedOutputRecoversIt() throws Exception {
        PlayerProfile a = profile(10), b = profile(10), bot = profile(0); bot.setBot(true);
        for (int i = 0; i < 5; i++) CombatEconomy.finishHumanLife(b, true, 1000 + i);
        assertEquals(.2, CombatEconomy.deathMultiplier(b), 0);
        ObjectMapper mapper = new ObjectMapper();
        b.setEconomyData(mapper.readValue(mapper.writeValueAsBytes(b.getEconomyData()), EconomyData.class));
        CombatEconomy.humanDamage(b, bot, 20);
        CombatEconomy.finishHumanLife(b, true, 2000);
        assertEquals(.2, CombatEconomy.deathMultiplier(b), 0);
        CombatEconomy.humanDamage(b, a, 2);
        CombatEconomy.finishHumanLife(b, true, 3000);
        assertEquals(1, CombatEconomy.deathMultiplier(b), 0);
    }

    @Test public void swappingKillerAndVictimDoesNotResetThePairAllowance() {
        PlayerProfile a = profile(10), b = profile(10);
        for (int i = 0; i < 10; i++) assertTrue(CombatEconomy.allowExtras(i % 2 == 0 ? a : b, i % 2 == 0 ? b : a, 1000 + i, true));
        assertFalse(CombatEconomy.allowExtras(a, b, 2000, true));
        assertTrue(CombatEconomy.allowExtras(b, a, 602000, true));
    }

    @Test public void oneFinalHumanKillDoesNotWashFortyNineLowHealthBotsIntoAFullReward() {
        PlayerProfile p = profile(20);
        for (int i = 0; i < 49; i++) CombatEconomy.contribution(p, 1, .05);
        CombatEconomy.contribution(p, 1, 1);
        assertEquals(.069, CombatEconomy.recentRatio(p, 50), 1e-9);
        assertFalse(CombatEconomy.claimRecent(p, "finale", 50));
        p.getEconomyData().newLife();
        assertEquals(.069, p.getEconomyData().fractionalRewards.get("finale"), 1e-9);
    }

    @Test public void delayedRestrictionsRemainScopedToTheOriginalRecipient() {
        PlayerProfile p = profile(20), other = profile(20);
        Runnable delayed;
        try (KillRewardContext context = KillRewardContext.open(p.getPlayerUuid(), 1)) {
            context.setEconomy(.1, true, false);
            delayed = KillRewardContext.wrap(() -> {
                assertEquals(0, RewardService.grant(p, RewardSource.ENCHANT, 10, 10).getCoins(), 0);
                assertEquals(16, RewardService.grant(other, RewardSource.ENCHANT, 10, 10).getCoins(), 1e-6);
            });
        }
        delayed.run();
    }

    @Test public void smallEventsHaveParticipationRewardsButNoAutomaticTopThreeJackpot() {
        assertArrayEquals(new double[]{500, 2000, 0}, MajorEventRewards.baseAward(true, 1, 1, false), 0);
        assertArrayEquals(new double[]{0, 0, 0}, MajorEventRewards.baseAward(false, 1, 20, true), 0);
        assertArrayEquals(new double[]{2500, 10000, 3}, MajorEventRewards.baseAward(true, 2, 10, true), 0);
        assertArrayEquals(new double[]{1000, 4000, 1}, MajorEventRewards.baseAward(true, 3, 10, false), 0);
        assertArrayEquals(new double[]{500, 2000, 0}, MajorEventRewards.baseAward(true, 7, 10, false), 0);
    }

    @Test public void weeklyRewardsAreExactlyOnceAndCannotClaimAllFiveOptions() throws Exception {
        PlayerProfile p = profile(10);
        EconomyData.WeekProgress week = GrowthProgress.currentWeek(p.getEconomyData(), System.currentTimeMillis());
        for (GrowthProgress.Goal goal : GrowthProgress.Goal.values()) week.progress.put(goal.name(), goal.target);
        double before = p.getCoins();
        assertTrue(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.KILLS));
        assertFalse(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.KILLS));
        assertTrue(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.QUESTS));
        assertTrue(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.EXPLORING));
        assertFalse(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.FISHING));
        assertEquals(1250 * 1.3, p.getCoins() - before, 1e-6);
        ObjectMapper mapper = new ObjectMapper();
        p.setEconomyData(mapper.readValue(mapper.writeValueAsBytes(p.getEconomyData()), EconomyData.class));
        assertFalse(GrowthProgress.claim(p, week.start, GrowthProgress.Goal.EVENTS));
    }

    @Test public void weeklyCarryIsBoundedAndClockRollbackDoesNotCreateAnotherWeek() {
        EconomyData data = new EconomyData(); long now = System.currentTimeMillis();
        long first = GrowthProgress.currentWeek(data, now).start;
        GrowthProgress.currentWeek(data, now + 7L * 86400000);
        assertEquals(2, data.weeks.size());
        GrowthProgress.currentWeek(data, now - 7L * 86400000);
        assertEquals(2, data.weeks.size());
        GrowthProgress.currentWeek(data, now + 21L * 86400000);
        assertTrue(data.weeks.stream().noneMatch(week -> week.start == first));
        assertTrue(data.weeks.size() <= 2);
    }

    @Test public void presetsUseAuthoritativeEntitlementsAndPreserveSavedDataAfterExpiry() {
        PlayerProfile p = profile(10);
        p.setSupporter(true);
        assertEquals(1, GrowthProgress.presetSlots(p));
        p.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.MYTHIC, System.currentTimeMillis() + 60000, 1));
        assertEquals(6, GrowthProgress.presetSlots(p)); assertEquals(7, GrowthProgress.reportDays(p));
        p.getEconomyData().builds.put("12", new EconomyData.BuildPreset());
        p.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED, System.currentTimeMillis() + 60000, 2));
        assertEquals(12, GrowthProgress.presetSlots(p)); assertEquals(30, GrowthProgress.reportDays(p));
        p.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED, 1, 3));
        assertEquals(3, GrowthProgress.presetSlots(p)); assertTrue(p.getEconomyData().builds.containsKey("12"));
    }

    @Test public void buildValidationDoesNotBypassLevelOrAdditionalSlotUnlocks() {
        PlayerProfile p = profile(10);
        AbstractPerk perk = mock(AbstractPerk.class); when(perk.getPerkType()).thenReturn(PerkType.PERK);
        try { BuildPresets.validateSlot(p, 4, perk); fail("locked slot"); } catch (IllegalArgumentException expected) { }
        p.setExperience(ProgressionV2.experienceAtLevel(10, 100));
        try { BuildPresets.validateSlot(p, 4, perk); fail("missing permanent unlock"); } catch (IllegalArgumentException expected) { }
        p.getUnlockedPerkMap().put("ExtractPerkSlot", new PerkData("ExtractPerkSlot", 1));
        BuildPresets.validateSlot(p, 4, perk);
    }

    @Test public void wardrobeRejectsForgedPricesAndDoesNotChargeForEquippingAgain() {
        PlayerProfile p = profile(10); p.setCoins(30000);
        assertFalse(GrowthProgress.cosmetic(p, "bronze", 1));
        assertTrue(GrowthProgress.cosmetic(p, "bronze", 20000));
        assertTrue(GrowthProgress.cosmetic(p, "bronze", 20000));
        assertEquals(10000, p.getCoins(), 0);
    }

    @Test public void legacyMigrationRecordDoesNotForceAnExtraChatTitle() {
        PlayerProfile p = profile(3);
        p.getEconomyData().migratedAt = 1000;
        p.getEconomyData().legacyPrestige = 3;
        p.getEconomyData().selectedCosmetic = "veteran";
        assertEquals("", GrowthProgress.badge(p));
    }

    @Test public void privateTrainingDifficultyDoesNotDiscountAnEarnedShare() {
        try (MockedStatic<cn.charlotte.pit.ThePit> pit = mockStatic(cn.charlotte.pit.ThePit.class)) {
            cn.charlotte.pit.ThePit plugin = mock(cn.charlotte.pit.ThePit.class);
            cn.charlotte.pit.junxu.JunxuManager rooms = mock(cn.charlotte.pit.junxu.JunxuManager.class);
            pit.when(cn.charlotte.pit.ThePit::getInstance).thenReturn(plugin);
            when(plugin.getJunxuManager()).thenReturn(rooms);
            PlayerProfile victim = profile(0); victim.setBot(true);
            org.bukkit.entity.Player entity = mock(org.bukkit.entity.Player.class);
            org.bukkit.Location at = new org.bukkit.Location(null, 18, 71, 45);
            when(entity.getLocation()).thenReturn(at);
            when(rooms.isTraining(entity, at)).thenReturn(true);
            cn.charlotte.pit.data.sub.DamageData hit = mock(cn.charlotte.pit.data.sub.DamageData.class);
            when(hit.getTimer()).thenReturn(new cn.charlotte.pit.util.cooldown.Cooldown(60000));
            victim.getDamageMap().put(UUID.randomUUID(), hit);
            for (double health : new double[]{1, 20, 200}) {
                when(hit.getDamage()).thenReturn(health);
                assertEquals(1, CombatEconomy.trainingUnits(victim, entity), 0);
            }
            victim.getDamageMap().clear();
            assertEquals(0, CombatEconomy.trainingUnits(victim, entity), 0);
        }
    }

    @Test public void botCurrencyVariationKeepsTheMeanAndOneSharedDrawForEachDefeatedBot() throws Exception {
        double total = 0;
        for (int i = 0; i < 1000; i++) {
            double factor = BotRewards.factorFromDraw((i + .5) / 1000.0);
            assertTrue(factor >= .9 && factor <= 1.1);
            total += factor;
        }
        assertEquals(1, total / 1000, 1e-12);
        PlayerProfile bot = profile(0); bot.setBot(true);
        double first = BotRewards.variableFactor(bot);
        assertEquals(first, BotRewards.variableFactor(bot), 0);
        ObjectMapper mapper = new ObjectMapper();
        bot.setEconomyData(mapper.readValue(mapper.writeValueAsBytes(bot.getEconomyData()), EconomyData.class));
        assertEquals(first, BotRewards.variableFactor(bot), 0);
        assertEquals(1, BotRewards.variableFactor(profile(0)), 0);
        bot.getEconomyData().newLife();
        double next = BotRewards.variableFactor(bot);
        assertTrue(next >= .9 && next <= 1.1);
    }

    @Test public void streakMoneyUsesTheWholeHumanContributionWindowEvenWhenTheLastKillIsABot() {
        PlayerProfile p = profile(10);
        for (int i = 0; i < 24; i++) {
            try (KillRewardContext context = KillRewardContext.openBot(p.getPlayerUuid())) {
                context.setEconomy(1, true, false); CombatEconomy.contribution(p, 1, 1);
            }
        }
        try (KillRewardContext context = KillRewardContext.open(p.getPlayerUuid(), 1)) {
            CombatEconomy.contribution(p, 1, 1);
        }
        assertEquals(.04, CombatEconomy.recentHumanRatio(p, 25), 1e-9);
        assertEquals(5.2, EconomyV2.streak(p, 25, 100, 0).getCoins(), 1e-6);
        p.getEconomyData().newLife();
        CombatEconomy.contribution(p, 1, 1);
        try (KillRewardContext context = KillRewardContext.openBot(p.getPlayerUuid())) {
            context.setEconomy(1, true, false); CombatEconomy.contribution(p, 1, 1);
            assertEquals(6.5, EconomyV2.streak(p, 2, 10, 0).getCoins(), 1e-6);
        }
    }
}
