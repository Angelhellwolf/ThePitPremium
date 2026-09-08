package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.type.normal.AntiBowSpammerEnchantP;
import cn.charlotte.pit.enchantment.type.normal.ComboDamageEnchant;
import cn.charlotte.pit.enchantment.type.normal.ComboXpEnchant;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.parm.type.ThrowOnly;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.combat.CombatContext;
import cn.charlotte.pit.util.combat.SecondaryDamage;
import cn.charlotte.pit.util.combat.TrueDamageRules;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.PluginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CombatConsistencyTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<PlayerProfile> profiles;
    private Player attacker, victim;
    private PlayerProfile profile, victimProfile;
    private EntityDamageByEntityEvent event;
    private CombatListener previousCombat;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        attacker = mock(Player.class);
        victim = mock(CraftPlayer.class, call -> {
            if (call.getMethod().getName().equals("getHealth")) {
                if (call.getMethod().getReturnType() == int.class) return 10;
                return 10.0;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));
        profile = mock(PlayerProfile.class); victimProfile = mock(PlayerProfile.class);
        when(victimProfile.isLoaded()).thenReturn(true);
        profiles = mockStatic(PlayerProfile.class);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId())).thenReturn(profile);
        profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId())).thenReturn(victimProfile);
        event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(victim);
        when(event.getFinalDamage()).thenReturn(4.0);
        previousCombat = CombatListener.INSTANCE;
        CombatListener.INSTANCE = null;
    }

    @After
    public void tearDown() {
        CombatListener.INSTANCE = previousCombat;
        if (profiles != null) profiles.close();
        if (bukkit != null) bukkit.close();
    }

    @Test
    public void comboCountsTheCurrentHitWithoutAwardingTheFirstHitOrChangingStatistics() {
        ComboDamageEnchant enchant = new ComboDamageEnchant();
        for (int hit = 1; hit <= 7; hit++) {
            when(profile.getMeleeHit()).thenReturn(hit - 1);
            AtomicDouble boost = new AtomicDouble(1);
            try (CombatContext ignored = CombatContext.open(event)) {
                enchant.handleAttackEntity(3, attacker, victim, 4, new AtomicDouble(), boost, new AtomicBoolean());
            }
            assertEquals(hit % 3 == 0 ? 1.4 : 1, boost.get(), 0.000001);
        }
        verify(profile, never()).setMeleeHit(anyInt());
    }

    @Test
    public void cancelledHitsDoNotCountAndNestedProjectilesDoNotOverwriteTheMeleeContext() {
        when(profile.getMeleeHit()).thenReturn(2);
        when(profile.getBowHit()).thenReturn(7);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent shot = mock(EntityDamageByEntityEvent.class);
        when(shot.getDamager()).thenReturn(arrow);
        when(shot.getEntity()).thenReturn(victim);
        when(shot.getFinalDamage()).thenReturn(2.0);
        try (CombatContext ignored = CombatContext.open(event)) {
            assertEquals(3, CombatContext.meleeHits(attacker));
            try (CombatContext nested = CombatContext.open(shot)) {
                assertEquals(8, CombatContext.bowHits(attacker));
                assertEquals(-1, CombatContext.meleeHits(attacker));
            }
            assertEquals(3, CombatContext.meleeHits(attacker));
            when(event.isCancelled()).thenReturn(true);
            assertEquals(-1, CombatContext.meleeHits(attacker));
        }
        assertEquals(2, CombatContext.meleeHits(attacker));
    }

    @Test
    public void bowDamageReductionReceivesTheArrowInsteadOfItsShooter() throws Exception {
        Method method = GameEffectListener.class.getDeclaredMethod("processPlayerDamaged", IPlayerDamaged.class,
                int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        AtomicDouble boost = new AtomicDouble(1);
        method.invoke(new GameEffectListener(), new AntiBowSpammerEnchantP(), 3, victim, arrow,
                4.0, new AtomicDouble(), boost, new AtomicBoolean());
        assertEquals(0.4, boost.get(), 0.000001);
    }

    @Test
    public void throwOnlyCallbacksAcceptSnowballsAndRejectArrows() throws Exception {
        Method method = GameEffectListener.class.getDeclaredMethod("processShootEntity", IPlayerShootEntity.class,
                int.class, Player.class, Entity.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
        method.setAccessible(true);
        ThrowEffect effect = new ThrowEffect();
        method.invoke(new GameEffectListener(), effect, 1, attacker, victim, mock(Arrow.class),
                4.0, new AtomicDouble(), new AtomicDouble(1), new AtomicBoolean());
        assertEquals(0, effect.calls);
        method.invoke(new GameEffectListener(), effect, 1, attacker, victim, mock(Snowball.class),
                4.0, new AtomicDouble(), new AtomicDouble(1), new AtomicBoolean());
        assertEquals(1, effect.calls);
    }

    @Test
    public void projectileCriticalUsesTheArrowFlag() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        when(arrow.isCritical()).thenReturn(true);
        when(event.getDamager()).thenReturn(arrow);
        try (CombatContext ignored = CombatContext.open(event)) {
            assertEquals(Boolean.TRUE, CombatContext.projectileCritical(attacker));
            when(arrow.isCritical()).thenReturn(false);
            assertEquals(Boolean.FALSE, CombatContext.projectileCritical(attacker));
        }
        assertNull(CombatContext.projectileCritical(attacker));
    }

    @Test
    public void comboXpCannotPayForBotTargetsOrSelfHits() {
        when(profile.getMeleeHit()).thenReturn(5);
        ComboXpEnchant enchant = new ComboXpEnchant();
        try (MockedStatic<RewardService> rewards = mockStatic(RewardService.class)) {
            when(victimProfile.isBot()).thenReturn(true);
            enchant.handleAttackEntity(3, attacker, victim, 4, new AtomicDouble(), new AtomicDouble(1), new AtomicBoolean());
            enchant.handleAttackEntity(3, attacker, attacker, 4, new AtomicDouble(), new AtomicDouble(1), new AtomicBoolean());
            rewards.verifyNoInteractions();
        }
    }

    @Test
    public void trueDamageResistanceExpiresAndUnblockableDamageBypassesIt() {
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class);
             MockedStatic<cn.charlotte.pit.junxu.JunxuManager> rooms = mockStatic(cn.charlotte.pit.junxu.JunxuManager.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            rooms.when(() -> cn.charlotte.pit.junxu.JunxuManager.permitsDamage(attacker, victim)).thenReturn(true);
            when(victim.getMetadata("true_damage_immune")).thenReturn(Collections.singletonList(
                    new FixedMetadataValue(plugin, System.currentTimeMillis() + 15000)));
            assertTrue(TrueDamageRules.hasResistance(victim));
            players.when(() -> PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2.0, true)).thenCallRealMethod();
            players.when(() -> PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2.0, false)).thenCallRealMethod();
            PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2.0, true);
            verify(victim, never()).setHealth(anyDouble());
            PlayerUtil.damage(attacker, victim, PlayerUtil.DamageType.TRUE, 2.0, false);
            verify(victim).setHealth(8);
            when(victim.getMetadata("true_damage_immune")).thenReturn(Collections.singletonList(
                    new FixedMetadataValue(plugin, System.currentTimeMillis() - 1)));
            assertFalse(TrueDamageRules.hasResistance(victim));
        }
    }

    @Test
    public void lethalTrueDamageDoesNotBecomeAWeaponSwingOrGrimReaperChain() {
        when(event.isApplicable(any(EntityDamageEvent.DamageModifier.class))).thenReturn(true);
        doAnswer(call -> {
            assertTrue(SecondaryDamage.isHit(event));
            assertFalse(SecondaryDamage.isGrimReaperKill(attacker, victim));
            new GameEffectListener().onPlayerDamagePlayer(event);
            return null;
        }).when(victim).damage(anyDouble(), eq(attacker));
        SecondaryDamage.lethalTrueDamage(attacker, victim, 10);
        verify(event).setDamage(10010);
        verify(event).setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
        verify(victim).setLastDamageCause(event);
        assertFalse(SecondaryDamage.isHit(event));
    }

    @Test
    public void acceptedEffectsWaitForFinalSettlementAndNeverPayForCancelledOrZeroDamageHits() {
        when(((CraftPlayer) victim).getHandle()).thenReturn(mock(net.minecraft.server.v1_8_R3.EntityPlayer.class));
        for (int state = 0; state < 3; state++) {
            java.util.concurrent.atomic.AtomicInteger grants = new java.util.concurrent.atomic.AtomicInteger();
            when(event.isCancelled()).thenReturn(false);
            when(event.getFinalDamage()).thenReturn(4.0);
            try (CombatContext ignored = CombatContext.open(event)) {
                CombatContext.afterAccepted(grants::incrementAndGet);
                assertEquals(0, grants.get());
            }
            if (state == 1) when(event.isCancelled()).thenReturn(true);
            if (state == 2) when(event.getFinalDamage()).thenReturn(0.0);
            CombatContext.complete(event);
            CombatContext.complete(event);
            assertEquals(state == 0 ? 1 : 0, grants.get());
        }
    }

    @Test
    public void armorQuestWeakensActualMitigationAndTrueConversionIsCappedAndScoped() {
        when(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)).thenReturn(true);
        when(event.getDamage(EntityDamageEvent.DamageModifier.ARMOR)).thenReturn(-6.0);
        try (CombatContext context = CombatContext.open(event)) {
            CombatContext.weakenArmor(0.6);
            context.applyArmorPenalty();
            verify(event).setDamage(eq(EntityDamageEvent.DamageModifier.ARMOR), doubleThat(value -> Math.abs(value + 2.4) < 0.000001));
            CombatContext.convertNormalToTrue(0.5);
            assertEquals(5, context.convertedDamage(10), 0);
            CombatContext.convertNormalToTrue(2);
            assertEquals(10, context.convertedDamage(10), 0);
        }
        try (CombatContext context = CombatContext.open(event)) {
            assertEquals(0, context.convertedDamage(10), 0);
        }
    }

    public static class ThrowEffect implements IPlayerShootEntity {
        int calls;
        @Override @ThrowOnly
        public void handleShootEntity(int level, Player attacker, Entity target, double damage,
                AtomicDouble trueDamage, AtomicDouble boost, AtomicBoolean cancel) { calls++; }
    }
}
