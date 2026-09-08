package cn.charlotte.pit.reward;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.util.level.LevelUtil;
import org.bukkit.Bukkit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

public class RewardServiceTest {
    private MockedStatic<Bukkit> bukkit;
    private boolean oldEnabled;
    private int oldLimit;
    private double oldRewardFactor;
    private double oldVictimFactor;
    private double oldCoinFactor;
    private int oldMaxPrestige;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        oldEnabled = NewConfiguration.INSTANCE.getNoobRewardsEnabled();
        oldLimit = NewConfiguration.INSTANCE.getNoobProtectLevel();
        oldRewardFactor = NewConfiguration.INSTANCE.getNoobRewardMultiplier();
        oldVictimFactor = NewConfiguration.INSTANCE.getNoobKilledRewardMultiplier();
        oldCoinFactor = NewConfiguration.INSTANCE.getNoobCoinRewardMultiplier();
        oldMaxPrestige = NewConfiguration.INSTANCE.getNoobMaxPrestige();
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(true);
        NewConfiguration.INSTANCE.setNoobProtectLevel(120);
        NewConfiguration.INSTANCE.setNoobRewardMultiplier(1.5);
        NewConfiguration.INSTANCE.setNoobKilledRewardMultiplier(0.7);
        NewConfiguration.INSTANCE.setNoobCoinRewardMultiplier(1.5);
        NewConfiguration.INSTANCE.setNoobMaxPrestige(5);
    }

    @After
    public void tearDown() {
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(oldEnabled);
        NewConfiguration.INSTANCE.setNoobProtectLevel(oldLimit);
        NewConfiguration.INSTANCE.setNoobRewardMultiplier(oldRewardFactor);
        NewConfiguration.INSTANCE.setNoobKilledRewardMultiplier(oldVictimFactor);
        NewConfiguration.INSTANCE.setNoobCoinRewardMultiplier(oldCoinFactor);
        NewConfiguration.INSTANCE.setNoobMaxPrestige(oldMaxPrestige);
        if (bukkit != null) bukkit.close();
    }

    @Test
    public void bothNoobFactorsApplyOnceAndReceiptReportsTheActualDelta() {
        PlayerProfile recipient = profile();
        PlayerProfile victim = profile();
        double oldCoins = recipient.getCoins();
        double victimFactor = RewardService.victimMultiplier(victim);
        RewardService.Receipt receipt = RewardService.grant(recipient, RewardSource.KILL,
                20 * victimFactor, 100 * victimFactor);
        assertEquals(21, receipt.getCoins(), 0.000001);
        assertEquals(105, receipt.getExperience(), 0.000001);
        assertEquals(oldCoins + 21, recipient.getCoins(), 0.000001);
        assertEquals(105, recipient.getExperience(), 0.000001);
        assertEquals(21, recipient.getGrindedCoins(), 0.000001);
    }

    @Test
    public void mailboxAndStoredPayoutDoNotApplyTheGameplayBonusAgain() {
        for (RewardSource source : new RewardSource[]{RewardSource.MAIL, RewardSource.ADMIN, RewardSource.STREAK_PAYOUT,
                RewardSource.BOUNTY, RewardSource.HIGHLANDER}) {
            PlayerProfile recipient = profile();
            RewardService.Receipt receipt = RewardService.grant(recipient, source, 20, 100);
            assertEquals(source.name(), 20, receipt.getCoins(), 0.000001);
            assertEquals(source.name(), 100, receipt.getExperience(), 0.000001);
        }
    }

    @Test
    public void rewardCrossingLevel120ReportsOnlyRemainingCapacity() {
        PlayerProfile recipient = profile();
        double cap = LevelUtil.getLevelTotalExperience(0, 120);
        recipient.setExperience(cap - 5);
        assertTrue(RewardService.isNoob(recipient));
        RewardService.Receipt receipt = RewardService.grant(recipient, RewardSource.LOOT, 20, 100);
        assertEquals(5, receipt.getExperience(), 0.000001);
        assertEquals(30, receipt.getCoins(), 0.000001);
        assertEquals(cap, recipient.getExperience(), 0.000001);
        assertEquals(120, recipient.getLevel());
        assertEquals(0, RewardService.grant(recipient, RewardSource.LOOT, 0, 100).getExperience(), 0);
    }

    @Test
    public void botsAndUnloadedProfilesCannotReceiveOrScaleRewards() {
        PlayerProfile bot = profile();
        bot.setBot(true);
        double coinsBefore = bot.getCoins();
        assertEquals(1, RewardService.victimMultiplier(bot), 0);
        assertEquals(0, RewardService.grant(bot, RewardSource.KILL, 100, 100).getExperience(), 0);
        assertEquals(coinsBefore, bot.getCoins(), 0);
        PlayerProfile unloaded = profile();
        unloaded.setLoaded(false);
        assertEquals(0, RewardService.grant(unloaded, RewardSource.MAIL, 100, 100).getExperience(), 0);
    }

    @Test(expected = IllegalStateException.class)
    public void workerThreadsCannotMutateGameplayBalances() {
        PlayerProfile recipient = profile();
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        RewardService.grant(recipient, RewardSource.LOOT, 1, 1);
    }

    @Test
    public void botContextDoesNotChangeAnotherPlayersRewardOrIndependentQuestAndStoredPayouts() {
        PlayerProfile recipient = profile(), other = profile();
        try (KillRewardContext ignored = KillRewardContext.openBot(recipient.getPlayerUuid())) {
            assertEquals(10, RewardService.grant(recipient, RewardSource.KILL, 10, 10).getCoins(), 0);
            assertEquals(10, RewardService.grant(recipient, RewardSource.ASSIST, 10, 10).getCoins(), 0);
            assertEquals(0, RewardService.grant(recipient, RewardSource.ENCHANT, 10, 10).getCoins(), 0);
            assertEquals(15, RewardService.grant(other, RewardSource.KILL, 10, 10).getCoins(), 0);
            assertEquals(15, RewardService.grant(recipient, RewardSource.QUEST, 10, 10).getCoins(), 0);
            assertEquals(10, RewardService.grant(recipient, RewardSource.STREAK_PAYOUT, 10, 10).getCoins(), 0);
        }
        assertEquals(15, RewardService.grant(recipient, RewardSource.KILL, 10, 10).getCoins(), 0);
    }

    @Test
    public void newcomerExperienceAndCoinBonusesEndAfterPrestigeFiveAndDoNotApplyToMail() {
        NewConfiguration.INSTANCE.setNoobRewardMultiplier(2.0);
        NewConfiguration.INSTANCE.setNoobCoinRewardMultiplier(1.5);
        NewConfiguration.INSTANCE.setNoobKilledRewardMultiplier(1.0);
        for(int prestige=0;prestige<=6;prestige++){
            PlayerProfile recipient=profile();recipient.setPrestige(prestige);
            assertEquals(prestige<=5,RewardService.isNoob(recipient));
            RewardService.Receipt receipt=RewardService.grant(recipient,RewardSource.EVENT,20,40);
            assertEquals(prestige<=5?30:20,receipt.getCoins(),.000001);
            assertEquals(prestige<=5?80:40,receipt.getExperience(),.000001);
            assertEquals(1,RewardService.victimMultiplier(recipient),0);
            receipt=RewardService.grant(recipient,RewardSource.MAIL,20,40);
            assertEquals(20,receipt.getCoins(),.000001);assertEquals(40,receipt.getExperience(),.000001);
        }
    }

    private PlayerProfile profile() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "RewardTester");
        profile.setPrestige(0);
        profile.setExperience(0);
        profile.setGrindedCoins(0);
        profile.setLoaded(true);
        return profile;
    }

    @Test
    public void registeredPublicPlayersReceiveTheSameKillAndAssistCreditAsPlayers() {
        PlayerProfile simulated = profile(); simulated.setBot(true);
        PlayerProfile human = profile();
        cn.charlotte.pit.ThePit plugin = org.mockito.Mockito.mock(cn.charlotte.pit.ThePit.class);
        cn.charlotte.pit.bot.PitBotManager manager = org.mockito.Mockito.mock(cn.charlotte.pit.bot.PitBotManager.class);
        org.mockito.Mockito.when(plugin.getPitBotManager()).thenReturn(manager);
        org.mockito.Mockito.when(manager.ownsProfile(simulated)).thenReturn(true);
        try (MockedStatic<cn.charlotte.pit.ThePit> pit = mockStatic(cn.charlotte.pit.ThePit.class)) {
            pit.when(cn.charlotte.pit.ThePit::getInstance).thenReturn(plugin);
            for (RewardSource source : new RewardSource[]{RewardSource.KILL, RewardSource.ASSIST}) {
                RewardService.Receipt expected = RewardService.grant(human, source, 20, 40);
                RewardService.Receipt actual = RewardService.grant(simulated, source, 20, 40);
                assertEquals(expected.getCoins(), actual.getCoins(), 0);
                assertEquals(expected.getExperience(), actual.getExperience(), 0);
            }
            assertEquals(human.getGrindedCoins(), simulated.getGrindedCoins(), 0);
        }
    }

    @Test
    public void immediateKillBonusesAreReducedOnceWithoutTaxingVictimsOrStoredPayouts() {
        PlayerProfile killer=profile(),victim=profile();
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(false);
        try(KillRewardContext context=KillRewardContext.open(killer.getPlayerUuid(),.2)){
            RewardService.Receipt perk=RewardService.grant(killer,RewardSource.PERK,20,100);
            assertEquals(4,perk.getCoins(),.000001);assertEquals(20,perk.getExperience(),.000001);
            RewardService.Receipt aggregate=RewardService.grant(killer,RewardSource.KILL,4,20);
            assertEquals(4,aggregate.getCoins(),.000001);assertEquals(20,aggregate.getExperience(),.000001);
            assertEquals(100,RewardService.grant(victim,RewardSource.STREAK_PAYOUT,0,100).getExperience(),.000001);
            assertEquals(100,RewardService.grant(killer,RewardSource.MAIL,0,100).getExperience(),.000001);
        }
        assertEquals(100,RewardService.grant(killer,RewardSource.PERK,0,100).getExperience(),.000001);
    }
}
