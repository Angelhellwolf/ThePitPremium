package cn.charlotte.pit.perk;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.event.PitStreakKillChangeEvent;
import cn.charlotte.pit.perk.type.prestige.MonsterPerk;
import cn.charlotte.pit.perk.type.streak.beastmode.TacticalRetreatKillStreak;
import cn.charlotte.pit.perk.type.streak.hermit.HermitMegaStreak;
import cn.charlotte.pit.perk.type.streak.hermit.IceCubeKillStreak;
import cn.charlotte.pit.perk.type.streak.tothemoon.ToTheMoonMegaStreak;
import cn.charlotte.pit.perk.type.streak.tothemoon.XPStack;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.item.ItemUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StreakConsistencyTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private MockedStatic<PlayerUtil> players;
    private final UUID uuid = UUID.randomUUID();
    private Player player;
    private PlayerProfile profile;
    private AtomicDouble xpStack;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        player = mock(Player.class);
        profile = mock(PlayerProfile.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(profile.getPlayerUuid()).thenReturn(uuid);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        players = mockStatic(PlayerUtil.class);
        xpStack = new AtomicDouble();
        when(profile.getXpStackMax()).thenReturn(1.0);
        when(profile.getXpStackAddon()).thenAnswer(call -> xpStack.get());
        doAnswer(call -> { xpStack.set(call.getArgument(0)); return null; }).when(profile).setXpStackAddon(anyDouble());
    }

    @After
    public void tearDown() {
        if (players != null) players.close();
        if (profiles != null) profiles.close();
        if (bukkit != null) bukkit.close();
    }

    @Test
    public void xpStackReceivesEventsWhenInstalledInTheProductionFactory() {
        PluginManager manager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            PerkFactory factory = new PerkFactory();
            factory.init(Collections.singletonList(XPStack.class));
            verify(manager).registerEvents(isA(XPStack.class), eq(plugin));
            assertNotNull(factory.getPerkMap().get("xp_stack"));
        }
    }

    @Test
    public void xpStackOnlyAccumulatesWhileChosenAndOncePerCrossedMilestone() {
        XPStack perk = new XPStack();
        perk.assist(new PitStreakKillChangeEvent(profile, 9.8, 10.2));
        assertEquals(0, xpStack.get(), 0);
        players.when(() -> PlayerUtil.isPlayerChosePerk(player, "xp_stack")).thenReturn(true);
        perk.assist(new PitStreakKillChangeEvent(profile, 9.8, 10.2));
        assertEquals(0.05, xpStack.get(), 0.000001);
        perk.assist(new PitStreakKillChangeEvent(profile, 10.2, 10.9));
        perk.assist(new PitStreakKillChangeEvent(profile, 10.9, 0));
        assertEquals(0.05, xpStack.get(), 0.000001);
        xpStack.set(0.99);
        perk.assist(new PitStreakKillChangeEvent(profile, 19.8, 20.2));
        assertEquals(1, xpStack.get(), 0.000001);
    }

    @Test
    public void accumulatedXpStackSurvivesUnequippingAndAddsToFutureKills() {
        xpStack.set(0.75);
        PitKillEvent event = new PitKillEvent(player, mock(Player.class), 10, 10);
        new XPStack().assist(event);
        assertEquals(10.75, event.getExp(), 0.000001);
    }

    @Test
    public void moonDamageUsesTenPercentAndPointOneHeartPerTwentyKills() {
        ToTheMoonMegaStreak perk = new ToTheMoonMegaStreak();
        when(profile.getStreakKills()).thenReturn(119.0);
        AtomicDouble boost = new AtomicDouble(1), trueDamage = new AtomicDouble();
        perk.handlePlayerDamaged(1, player, player, 5, trueDamage, boost, new AtomicBoolean());
        assertEquals(1, boost.get(), 0.000001);
        when(profile.getStreakKills()).thenReturn(220.0);
        perk.handlePlayerDamaged(1, player, player, 5, trueDamage, boost, new AtomicBoolean());
        assertEquals(1.6, boost.get(), 0.000001);
        assertEquals(0.2, trueDamage.get(), 0.000001);
    }

    @Test
    public void hermitStartsAtPointThreePercentRatherThanSeventyFivePercent() {
        when(profile.getStreakKills()).thenReturn(51.0);
        AtomicDouble boost = new AtomicDouble(1);
        new HermitMegaStreak().handlePlayerDamaged(0, player, player, 5,
                new AtomicDouble(), boost, new AtomicBoolean());
        assertEquals(1.003, boost.get(), 0.000001);
    }

    @Test
    public void monsterCrossesFractionalMilestonesAndClearsOnlyItsOwnHealthBonus() {
        HashMap<String, Double> health = new HashMap<>();
        health.put("permanent", 2.0);
        when(profile.getExtraMaxHealth()).thenReturn(health);
        when(profile.getMaxHealth()).thenAnswer(call -> 20 + health.values().stream().mapToDouble(Double::doubleValue).sum());
        players.when(() -> PlayerUtil.isPlayerChosePerk(player, "monster")).thenReturn(true);
        MonsterPerk perk = new MonsterPerk();
        perk.onSteak(new PitStreakKillChangeEvent(profile, 24.8, 25.1));
        assertEquals(2.0, health.get("monster"), 0);
        perk.onSteak(new PitStreakKillChangeEvent(profile, 49.8, 50.1));
        assertEquals(4.0, health.get("monster"), 0);
        perk.handleRespawn(0, player);
        assertFalse(health.containsKey("monster"));
        assertEquals(2.0, health.get("permanent"), 0);
        verify(player).setMaxHealth(22);
    }

    @Test
    public void tacticalRetreatAppearsAmongKillStreakChoices() {
        assertEquals(PerkType.KILL_STREAK, new TacticalRetreatKillStreak().getPerkType());
    }

    @Test
    public void iceCubeSlowsBotsWithoutMintingExtraExperience() {
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(targetId);
        PlayerProfile targetProfile = mock(PlayerProfile.class);
        when(targetProfile.isLoaded()).thenReturn(true);
        when(targetProfile.isBot()).thenReturn(true);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(targetId)).thenReturn(targetProfile);
        ItemStack item = new ItemStack(Material.ICE);
        when(player.getItemInHand()).thenReturn(item);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(player);
        when(event.getEntity()).thenReturn(target);
        when(event.getFinalDamage()).thenReturn(1.0);
        try (MockedStatic<ItemUtil> items = mockStatic(ItemUtil.class);
             MockedStatic<RewardService> rewards = mockStatic(RewardService.class)) {
            items.when(() -> ItemUtil.getInternalName(item)).thenReturn("ice_cube");
            new IceCubeKillStreak().onPlayerDamage(event);
            players.verify(() -> PlayerUtil.addPotionEffect(target, new PotionEffect(PotionEffectType.SLOW, 80, 0)));
            rewards.verifyNoInteractions();
            when(targetProfile.isBot()).thenReturn(false);
            new IceCubeKillStreak().onPlayerDamage(event);
            rewards.verify(() -> RewardService.grant(profile, RewardSource.STREAK, 0, 40));
        }
    }
}
