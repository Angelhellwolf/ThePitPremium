package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.KingsQuestsData;
import cn.charlotte.pit.data.sub.PlayerOption;
import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.event.PitRewardGrantedEvent;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.perk.PerkFactory;
import cn.charlotte.pit.perk.type.streak.nonpurchased.OverDriveMegaStreak;
import cn.charlotte.pit.perk.type.streak.tothemoon.ToTheMoonMegaStreak;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.rank.RankUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CombatSettlementTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerUtil> players;
    private MockedStatic<RankUtil> ranks;
    private MockedStatic<CC> chat;
    private CombatListener combat;
    private CombatListener previousCombat;
    private double previousBoost;
    private boolean previousNoobEnabled;
    private PerkFactory perks;
    private PitConfig config;
    private PluginManager pluginManager;
    private final List<UUID> profiles = new ArrayList<>();

    @Before
    public void setUp() {
        previousCombat = CombatListener.INSTANCE;
        previousBoost = CombatListener.eventBoost;
        previousNoobEnabled = NewConfiguration.INSTANCE.getNoobRewardsEnabled();
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(false);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        pluginManager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        ThePit plugin = mock(ThePit.class);
        config = mock(PitConfig.class);
        when(config.getBooster()).thenReturn(1.0);
        // Keep random item drops out of this settlement test; verify the remaining curfew factor.
        when(config.isCurfewEnable()).thenReturn(true);
        when(config.getCurfewStart()).thenReturn(0);
        when(config.getCurfewEnd()).thenReturn(23);
        when(plugin.getPitConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        perks = new PerkFactory();
        when(plugin.getPerkFactory()).thenReturn(perks);
        when(plugin.getEnchantmentFactor()).thenReturn(new EnchantmentFactor());
        when(plugin.getEventFactory()).thenReturn(mock(EventFactory.class));
        when(plugin.getSoundFactory()).thenReturn(mock(cn.charlotte.pit.util.sound.SoundFactory.class));
        pit = mockStatic(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        players = mockStatic(PlayerUtil.class);
        players.when(() -> PlayerUtil.getAmountOfActiveHealingPerk(any(Player.class))).thenReturn(1);
        ranks = mockStatic(RankUtil.class);
        ranks.when(() -> RankUtil.getPlayerColoredName(any(UUID.class))).thenReturn("TestPlayer");
        chat = mockStatic(CC.class);
        chat.when(() -> CC.translate(anyString())).thenAnswer(call -> call.getArgument(0));
        combat = new CombatListener();
    }

    @After
    public void tearDown() {
        for (UUID uuid : profiles) PlayerProfile.getCacheProfile().remove(uuid);
        CombatListener.INSTANCE = previousCombat;
        CombatListener.eventBoost = previousBoost;
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(previousNoobEnabled);
        if (chat != null) chat.close();
        if (ranks != null) ranks.close();
        if (players != null) players.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    @Test
    public void killModifierRunsBeforeCreditAndNotificationContainsActualCredit() {
        PlayerProfile recipient = profile();
        PlayerProfile victimProfile = profile();
        Player killer = player(recipient.getPlayerUuid());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(victimProfile.getPlayerUuid());
        List<String> order = new ArrayList<>();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) {
                assertEquals(0, recipient.getExperience(), 0);
                ((PitKillEvent) event).setExp(100);
                ((PitKillEvent) event).setCoins(40);
                order.add("modify");
            } else if (event instanceof PitRewardGrantedEvent) {
                PitRewardGrantedEvent granted = (PitRewardGrantedEvent) event;
                assertEquals(1, recipient.getExperience(), 0.000001);
                assertEquals(1, granted.getExperience(), 0.000001);
                assertEquals(0.4, granted.getCoins(), 0.000001);
                assertEquals(RewardSource.KILL, granted.getSource());
                order.add("credited");
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        combat.handleKill(killer, recipient, victim, victimProfile);

        assertEquals(java.util.Arrays.asList("modify", "credited"), order);
        assertEquals(1, recipient.getExperience(), 0.000001);
        assertEquals(1, victimProfile.getKillRecap().getTotalExp(), 0.000001);
    }

    @Test
    public void managedSimulatedKillerUsesTheOrdinarySettlementPipeline() {
        PlayerProfile recipient = profile(); recipient.setBot(true);
        cn.charlotte.pit.bot.PitBotManager manager = mock(cn.charlotte.pit.bot.PitBotManager.class);
        when(ThePit.getInstance().getPitBotManager()).thenReturn(manager);
        when(manager.ownsProfile(recipient)).thenReturn(true);
        PlayerProfile victimProfile = profile();
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(victimProfile.getPlayerUuid());
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) {
                ((PitKillEvent) event).setExp(100);
                ((PitKillEvent) event).setCoins(40);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        combat.handleKill(player(recipient.getPlayerUuid()), recipient, victim, victimProfile);
        assertEquals(1, recipient.getKills());
        assertEquals(1, recipient.getExperience(), 0.000001);
        assertEquals(0.4, recipient.getGrindedCoins(), 0.000001);
        verify(pluginManager).callEvent(isA(PitRewardGrantedEvent.class));
    }

    @Test
    public void postCreditNotificationUsesRemainingCapInsteadOfRequestedExperience() {
        PlayerProfile recipient = profile();
        double cap = LevelUtil.getLevelTotalExperience(0, 120);
        recipient.setExperience(cap - 0.5);
        PlayerProfile victimProfile = profile();
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(victimProfile.getPlayerUuid());
        List<Double> credited = new ArrayList<>();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) ((PitKillEvent) event).setExp(100);
            if (event instanceof PitRewardGrantedEvent) {
                credited.add(((PitRewardGrantedEvent) event).getExperience());
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));

        combat.handleKill(player(recipient.getPlayerUuid()), recipient, victim, victimProfile);

        assertEquals(Collections.singletonList(0.5), credited);
        assertEquals(cap, recipient.getExperience(), 0.000001);
        assertEquals(0.5, victimProfile.getKillRecap().getTotalExp(), 0.000001);
    }

    @Test
    public void botKillUsesHalfTheHumanMultiplierAndIgnoresCurfewEventAndNewcomerExtras() {
        for (double humanMultiplier : new double[]{1, 2, 3, 5}) {
        PlayerProfile recipient = profile();
        PlayerProfile victimProfile = profile();
        victimProfile.setBot(true);
        double before = recipient.getCoins();
        CombatListener.eventBoost = humanMultiplier;
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(true);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(victimProfile.getPlayerUuid());
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) {
                ((PitKillEvent) event).setExp(99_999);
                ((PitKillEvent) event).setCoins(99_999);
                cn.charlotte.pit.reward.RewardService.grant(recipient, RewardSource.PERK, 99_999, 99_999);
            }
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        combat.handleKill(player(recipient.getPlayerUuid()), recipient, victim, victimProfile);
        double expected = 10 * humanMultiplier * 0.5;
        assertEquals(expected, recipient.getCoins() - before, 0.000001);
        assertEquals(expected, recipient.getExperience(), 0.000001);
        assertEquals(expected, victimProfile.getKillRecap().getTotalExp(), 0.000001);
        ranks.verify(() -> RankUtil.getPlayerColoredName(victimProfile.getPlayerUuid()), never());
        }
    }

    @Test
    public void killerlessDeathUsesMoonPayoutWithoutPassingNullToItsEntityCallback() throws Exception {
        PlayerProfile victimProfile = profile();
        Player victim = player(victimProfile.getPlayerUuid());
        ToTheMoonMegaStreak moon = mock(ToTheMoonMegaStreak.class);
        when(moon.getPlayerLevel(victim)).thenReturn(1);
        perks.getPlayerBeKilledByEntities().add(moon);

        invokeDeathEffects(victim, null);

        verify(moon).rewardDeath(victim);
        verify(moon, never()).handlePlayerBeKilledByEntity(anyInt(), any(), any(), any(), any());
    }

    @Test
    public void aFailedDeathPerkDoesNotSwallowAnotherVictimsPayout() throws Exception {
        PlayerProfile victimProfile = profile();
        Player victim = player(victimProfile.getPlayerUuid());
        OverDriveMegaStreak broken = mock(OverDriveMegaStreak.class);
        when(broken.getPlayerLevel(victim)).thenReturn(1);
        doThrow(new IllegalStateException("broken callback")).when(broken)
                .handlePlayerBeKilledByEntity(anyInt(), eq(victim), isNull(), any(), any());
        ToTheMoonMegaStreak moon = mock(ToTheMoonMegaStreak.class);
        when(moon.getPlayerLevel(victim)).thenReturn(1);
        perks.getPlayerBeKilledByEntities().add(broken);
        perks.getPlayerBeKilledByEntities().add(moon);

        invokeDeathEffects(victim, null);

        verify(moon).rewardDeath(victim);
    }

    private void invokeDeathEffects(Player victim, Player killer) throws Exception {
        Method method = CombatListener.class.getDeclaredMethod("handleDeathEffects",
                Player.class, Player.class, AtomicDouble.class, AtomicDouble.class);
        method.setAccessible(true);
        method.invoke(combat, victim, killer, new AtomicDouble(), new AtomicDouble());
    }

    @Test
    public void solventPaysAndClearsBountyAtEveryPrestigeWithoutShopUnlock() throws Exception {
        for (int prestige : new int[] {0, 1, 3, 4, 20}) {
            PlayerProfile killerProfile = profile();
            killerProfile.setPrestige(prestige);
            onlinePlayer(killerProfile.getPlayerUuid());
            killerProfile.getBuffData().getBuff("bounty_solvent").stackBuffData(60_000);
            assertTrue(killerProfile.getUnlockedPerkMap().isEmpty());
            PlayerProfile victimProfile = profile();
            victimProfile.setBounty(1_000);
            AtomicDouble coins = new AtomicDouble(37);

            invokeBounty(killerProfile, victimProfile, coins);

            assertEquals("Solvent reward at prestige " + prestige, 1_537, coins.get(), 0);
            assertEquals("Victim bounty at prestige " + prestige, 0, victimProfile.getBounty());
            invokeBounty(killerProfile, victimProfile, coins);
            assertEquals("A cleared bounty cannot be claimed twice", 1_537, coins.get(), 0);
        }
    }

    @Test
    public void missingOrExpiredSolventStillPaysAndClearsBaseBounty() throws Exception {
        for (boolean expired : new boolean[] {false, true}) {
            PlayerProfile killerProfile = profile();
            onlinePlayer(killerProfile.getPlayerUuid());
            if (expired) {
                killerProfile.getBuffData().getBuff("bounty_solvent").stackBuffData(-60_000);
            }
            PlayerProfile victimProfile = profile();
            victimProfile.setBounty(1_000);
            AtomicDouble coins = new AtomicDouble(37);

            invokeBounty(killerProfile, victimProfile, coins);

            assertEquals(1_037, coins.get(), 0);
            assertEquals(0, victimProfile.getBounty());
        }
    }

    @Test
    public void realManSuppressesOnlySolventBonusAndStillClearsBounty() throws Exception {
        PlayerProfile killerProfile = profile();
        Player killer = onlinePlayer(killerProfile.getPlayerUuid());
        killerProfile.getBuffData().getBuff("bounty_solvent").stackBuffData(60_000);
        org.bukkit.metadata.MetadataValue realMan = mock(org.bukkit.metadata.MetadataValue.class);
        when(realMan.asLong()).thenReturn(System.currentTimeMillis() + 60_000);
        when(killer.getMetadata("real_man")).thenReturn(Collections.singletonList(realMan));
        PlayerProfile victimProfile = profile();
        victimProfile.setBounty(1_000);
        AtomicDouble coins = new AtomicDouble(37);

        invokeBounty(killerProfile, victimProfile, coins);

        assertEquals(1_037, coins.get(), 0);
        assertEquals(0, victimProfile.getBounty());
    }

    @Test
    public void unavailableKillerStillClearsBountyWithoutSolventBonus() throws Exception {
        PlayerProfile killerProfile = profile();
        killerProfile.getBuffData().getBuff("bounty_solvent").stackBuffData(60_000);
        PlayerProfile victimProfile = profile();
        victimProfile.setBounty(1_000);
        AtomicDouble coins = new AtomicDouble(37);

        invokeBounty(killerProfile, victimProfile, coins);

        assertEquals(1_037, coins.get(), 0);
        assertEquals(0, victimProfile.getBounty());
    }

    @Test
    public void lowPrestigeSolventKillCreditsOnceAndCompletesSettlement() {
        PlayerProfile killerProfile = profile();
        Player killer = onlinePlayer(killerProfile.getPlayerUuid());
        killerProfile.getBuffData().getBuff("bounty_solvent").stackBuffData(60_000);
        PlayerProfile victimProfile = profile();
        victimProfile.setBounty(1_000);
        Player victim = player(victimProfile.getPlayerUuid());
        double before = killerProfile.getCoins();

        combat.noteBountyDamage(killer.getUniqueId(), victim.getUniqueId(), 4);
        combat.noteBountyDamage(victim.getUniqueId(), killer.getUniqueId(), 4);

        combat.handleKill(killer, killerProfile, victim, victimProfile);

        // Both independent receipts retain curfew: 14 * .01 + 1,500 * .01.
        assertEquals(15.14, killerProfile.getCoins() - before, 0.000001);
        assertEquals(15.14, victimProfile.getKillRecap().getTotalCoin(), 0.000001);
        assertEquals(0, victimProfile.getBounty());
        verify(pluginManager, times(1)).callEvent(isA(PitRewardGrantedEvent.class));
        chat.verify(() -> CC.printError(eq(killer), any(Exception.class)), never());
    }

    private void invokeBounty(PlayerProfile killer, PlayerProfile victim, AtomicDouble coins) throws Exception {
        Method method = CombatListener.class.getDeclaredMethod("handleKillBounty",
                PlayerProfile.class, PlayerProfile.class, AtomicDouble.class);
        method.setAccessible(true);
        method.invoke(combat, killer, victim, coins);
    }

    private Player onlinePlayer(UUID uuid) {
        Player player = player(uuid);
        when(player.getPlayer()).thenReturn(player);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        return player;
    }

    @Test
    public void repeatedRealCombatKeepsAllKillEffectsAndEveryStreakEvent() {
        when(config.isCurfewEnable()).thenReturn(false);
        PlayerProfile killerProfile=profile(),victimProfile=profile();
        Player killer=player(killerProfile.getPlayerUuid()),victim=player(victimProfile.getPlayerUuid());
        cn.charlotte.pit.perk.type.prestige.DirtyPerk dirty=mock(cn.charlotte.pit.perk.type.prestige.DirtyPerk.class);
        when(dirty.getPlayerLevel(killer)).thenReturn(1);when(dirty.getInternalPerkName()).thenReturn("Dirty");
        perks.getPlayerKilledEntities().add(dirty);
        try(MockedStatic<cn.charlotte.pit.util.random.RandomUtil> random=mockStatic(cn.charlotte.pit.util.random.RandomUtil.class)){
            for(int i=0;i<8;i++){
                combat.noteCombatOutput(victim.getUniqueId(),4);
                combat.handleKill(killer,killerProfile,victim,victimProfile);
            }
        }
        assertEquals(8,killerProfile.getStreakKills(),0);
        verify(dirty,times(8)).handlePlayerKilled(eq(1),eq(killer),eq(victim),any(),any());
        verify(pluginManager,times(8)).callEvent(isA(cn.charlotte.pit.event.PitStreakKillChangeEvent.class));
        chat.verify(()->CC.printError(eq(killer),any(Exception.class)),never());
    }

    @Test
    public void lowInteractionReducesMoneyWithoutSilencingKillEffectsAndRecoversAfterOutput() {
        when(config.isCurfewEnable()).thenReturn(false);
        PlayerProfile killerProfile=profile(),victimProfile=profile();
        Player killer=player(killerProfile.getPlayerUuid()),victim=player(victimProfile.getPlayerUuid());
        cn.charlotte.pit.perk.type.prestige.DirtyPerk dirty=mock(cn.charlotte.pit.perk.type.prestige.DirtyPerk.class);
        when(dirty.getPlayerLevel(killer)).thenReturn(1);when(dirty.getInternalPerkName()).thenReturn("Dirty");perks.getPlayerKilledEntities().add(dirty);
        double[] delta=new double[6];
        List<Double> beforePolicy=new ArrayList<>();
        doAnswer(call->{if(call.getArgument(0) instanceof PitKillEvent)beforePolicy.add(((PitKillEvent)call.getArgument(0)).getCoins());return null;})
                .when(pluginManager).callEvent(any(Event.class));
        try(MockedStatic<cn.charlotte.pit.util.random.RandomUtil> random=mockStatic(cn.charlotte.pit.util.random.RandomUtil.class)){
            for(int i=0;i<6;i++){
                if(i==5)combat.noteCombatOutput(victim.getUniqueId(),4);
                double before=killerProfile.getCoins();combat.handleKill(killer,killerProfile,victim,victimProfile);
                delta[i]=killerProfile.getCoins()-before;
            }
        }
        assertTrue(delta[0]>0);assertEquals(delta[0],delta[1],.000001);
        assertEquals(beforePolicy.get(2)*.5,delta[2],.000001);assertEquals(beforePolicy.get(4)*.2,delta[4],.000001);
        assertEquals(beforePolicy.get(5),delta[5],.000001);assertEquals(6,killerProfile.getStreakKills(),0);
        verify(dirty,times(6)).handlePlayerKilled(eq(1),eq(killer),eq(victim),any(),any());
    }

    @Test
    public void failingKillPerkCannotCancelRemainingPerksOrTheAggregateReward() {
        PlayerProfile killerProfile=profile(),victimProfile=profile();
        Player killer=player(killerProfile.getPlayerUuid());LivingEntity victim=mock(LivingEntity.class);when(victim.getUniqueId()).thenReturn(victimProfile.getPlayerUuid());
        cn.charlotte.pit.perk.type.prestige.DirtyPerk broken=mock(cn.charlotte.pit.perk.type.prestige.DirtyPerk.class);
        when(broken.getPlayerLevel(killer)).thenReturn(1);when(broken.getInternalPerkName()).thenReturn("broken");
        doAnswer(call->{((AtomicDouble)call.getArgument(3)).set(Double.NaN);throw new IllegalStateException("test failure");})
                .when(broken).handlePlayerKilled(anyInt(),any(),any(),any(),any());
        cn.charlotte.pit.perk.type.prestige.DirtyPerk healthy=mock(cn.charlotte.pit.perk.type.prestige.DirtyPerk.class);
        when(healthy.getPlayerLevel(killer)).thenReturn(1);when(healthy.getInternalPerkName()).thenReturn("healthy");
        perks.getPlayerKilledEntities().add(broken);perks.getPlayerKilledEntities().add(healthy);
        double before=killerProfile.getCoins();combat.handleKill(killer,killerProfile,victim,victimProfile);
        assertTrue(killerProfile.getCoins()>before);verify(healthy).handlePlayerKilled(eq(1),eq(killer),eq(victim),any(),any());
    }

    @Test
    public void hillBonusCreditsFourTimesTheRewardEvenAfterManyValidKillsOfTheSamePlayer() throws Exception {
        PlayerProfile killerProfile=profile(),victimProfile=profile();
        Player killer=player(killerProfile.getPlayerUuid()),victim=player(victimProfile.getPlayerUuid());
        cn.charlotte.pit.events.impl.KingOfTheHillEvent hill=spy(new cn.charlotte.pit.events.impl.KingOfTheHillEvent());
        java.lang.reflect.Field active=cn.charlotte.pit.events.impl.KingOfTheHillEvent.class.getDeclaredField("isActive");
        active.setAccessible(true);active.set(hill,true);doReturn(true).when(hill).isRegion(killer);
        List<Double> expected=new ArrayList<>(),credited=new ArrayList<>();
        doAnswer(call->{
            Event event=call.getArgument(0);
            if(event instanceof PitKillEvent){PitKillEvent kill=(PitKillEvent)event;double before=kill.getCoins();hill.onKill(kill);assertEquals(4,kill.getHillMultiplier(),0);expected.add(before*4*.01);}
            if(event instanceof PitRewardGrantedEvent)credited.add(((PitRewardGrantedEvent)event).getCoins());
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        for(int i=0;i<8;i++){combat.noteCombatOutput(victim.getUniqueId(),4);combat.handleKill(killer,killerProfile,victim,victimProfile);}
        assertEquals(8,credited.size());
        for(int i=0;i<credited.size();i++)assertEquals(expected.get(i),credited.get(i),.000001);
    }

    @Test
    public void bountyDoesNotReceiveHillCalendarOrNewcomerMultipliers() {
        when(config.isCurfewEnable()).thenReturn(false);
        NewConfiguration.INSTANCE.setNoobRewardsEnabled(true);
        CombatListener.eventBoost = 5;
        PlayerProfile recipient = profile(), victimProfile = profile();
        Player killer = onlinePlayer(recipient.getPlayerUuid()), victim = player(victimProfile.getPlayerUuid());
        victimProfile.setBounty(1000);
        recipient.getBuffData().getBuff("bounty_solvent").stackBuffData(60_000);
        combat.noteBountyDamage(killer.getUniqueId(), victim.getUniqueId(), 4);
        combat.noteBountyDamage(victim.getUniqueId(), killer.getUniqueId(), 4);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) ((PitKillEvent) event).setCoins(((PitKillEvent) event).getCoins() * 4);
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        double before = recipient.getCoins();
        combat.handleKill(killer, recipient, victim, victimProfile);
        double expectedKill = 14 * 5 * 4 * NewConfiguration.INSTANCE.getNoobCoinRewardMultiplier()
                * NewConfiguration.INSTANCE.getNoobKilledRewardMultiplier();
        assertEquals(expectedKill + 1500, recipient.getCoins() - before, .000001);
        assertEquals(0, victimProfile.getBounty());
    }

    @Test
    public void highlanderAddsOnlyHalfTheBaseHumanRewardAndNeverPaysTheVictim() {
        when(config.isCurfewEnable()).thenReturn(false);
        PlayerProfile recipient = profile(), victimProfile = profile();
        recipient.setPrestige(20);
        recipient.setStreakKills(49);
        Player killer = onlinePlayer(recipient.getPlayerUuid()), victim = player(victimProfile.getPlayerUuid());
        players.when(() -> PlayerUtil.isPlayerChosePerk(killer, "high_lander")).thenReturn(true);
        combat.noteBountyDamage(killer.getUniqueId(), victim.getUniqueId(), 4);
        combat.noteBountyDamage(victim.getUniqueId(), killer.getUniqueId(), 4);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PitKillEvent) ((PitKillEvent) event).setCoins(1000);
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        double before = recipient.getCoins();
        combat.handleKill(killer, recipient, victim, victimProfile);
        assertEquals(1010, recipient.getCoins() - before, .000001);
        assertEquals(25, recipient.getBounty());
        cn.charlotte.pit.perk.type.streak.highlander.HighlanderMegaStreak highlander =
                new cn.charlotte.pit.perk.type.streak.highlander.HighlanderMegaStreak();
        AtomicDouble total = new AtomicDouble(1000);
        highlander.handlePlayerKilled(1, killer, victim, total, new AtomicDouble());
        assertEquals(1000, total.get(), 0);
        recipient.setBounty(10_000);
        before = recipient.getCoins();
        highlander.handlePlayerBeKilledByEntity(1, killer, victim, new AtomicDouble(), new AtomicDouble());
        assertEquals(before, recipient.getCoins(), 0);
    }

    @Test
    public void botKillsCannotGrowBountyOrTriggerHighlanderMoney() {
        when(config.isCurfewEnable()).thenReturn(false);
        PlayerProfile recipient = profile(), victimProfile = profile();
        recipient.setPrestige(20); recipient.setStreakKills(49); victimProfile.setBot(true);
        Player killer = onlinePlayer(recipient.getPlayerUuid()), victim = player(victimProfile.getPlayerUuid());
        players.when(() -> PlayerUtil.isPlayerChosePerk(killer, "high_lander")).thenReturn(true);
        double before = recipient.getCoins();
        for (int i = 0; i < 30; i++) {
            combat.noteBountyDamage(killer.getUniqueId(), victim.getUniqueId(), 4);
            combat.noteBountyDamage(victim.getUniqueId(), killer.getUniqueId(), 4);
            combat.handleKill(killer, recipient, victim, victimProfile);
        }
        assertEquals(300, recipient.getCoins() - before, .000001);
        assertEquals(0, recipient.getBounty());
    }

    @Test
    public void aPassiveDeathConsumesTheBountyWithoutPrintingMoney() {
        PlayerProfile recipient = profile(), victimProfile = profile();
        victimProfile.setBounty(5000);
        double before = recipient.getCoins();
        combat.handleKill(player(recipient.getPlayerUuid()), recipient, player(victimProfile.getPlayerUuid()), victimProfile);
        assertEquals(.14, recipient.getCoins() - before, .000001);
        assertEquals(0, victimProfile.getBounty());
    }

    private PlayerProfile profile() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "SettlementTester");
        profile.setPrestige(0);
        profile.setExperience(0);
        profile.setLoaded(true);
        profile.getPlayerOption().setBarPriority(PlayerOption.BarPriority.ENCHANT_ONLY);
        profile.kingsQuestsData = mock(KingsQuestsData.class);
        profiles.add(profile.getPlayerUuid());
        PlayerProfile.getCacheProfile().put(profile.getPlayerUuid(), profile);
        return profile;
    }

    private Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.iterator()).thenAnswer(ignored -> Collections.<ItemStack>emptyList().listIterator());
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        return player;
    }
}
