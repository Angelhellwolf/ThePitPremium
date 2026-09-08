package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.type.normal.MixedCombatEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MixedCombatEnchantTest {
    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test
    public void arrowMarkRetainsItsLevelAfterSwitchingToASwordAndOnlyConsumesOnAValidMatchingHit() {
        Player attacker = player(), target = player(), other = player();
        ThePit plugin = mock(ThePit.class);
        MixedCombatEnchant enchant = new MixedCombatEnchant();
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        when(arrow.getMetadata("enchant")).thenReturn(Collections.singletonList(new FixedMetadataValue(plugin, "mixed_combat_enchant:3")));
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class); MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            pit.when(ThePit::getInstance).thenReturn(plugin);
            enchant.onAcceptedHit(new EntityDamageByEntityEvent(arrow, target, EntityDamageEvent.DamageCause.PROJECTILE, 4));
            when(attacker.getItemInHand()).thenReturn(new ItemStack(Material.IRON_SWORD));
            assertEquals(3, enchant.getMarkedMeleeLevel(attacker, target));
            assertEquals(-1, enchant.getMarkedMeleeLevel(attacker, other));
            AtomicDouble boost = new AtomicDouble(1.4);
            enchant.handleAttackEntity(3, attacker, target, 6, new AtomicDouble(), boost, new AtomicBoolean());
            assertEquals(1.7, boost.get(), 0.000001);
            EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6);
            cancelled.setCancelled(true);
            enchant.onAcceptedHit(cancelled);
            assertEquals(3, enchant.getMarkedMeleeLevel(attacker, target));
            enchant.onAcceptedHit(new EntityDamageByEntityEvent(attacker, other, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6));
            assertEquals(3, enchant.getMarkedMeleeLevel(attacker, target));
            enchant.onAcceptedHit(new EntityDamageByEntityEvent(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6));
            assertEquals(-1, enchant.getMarkedMeleeLevel(attacker, target));
        }
    }

    @Test
    public void cancelledArrowsNeverLockAndQuittingTargetsClearTheirMarks() {
        Player attacker = player(), target = player();
        ThePit plugin = mock(ThePit.class);
        MixedCombatEnchant enchant = new MixedCombatEnchant();
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        when(arrow.getMetadata("enchant")).thenReturn(Collections.singletonList(new FixedMetadataValue(plugin, "mixed_combat_enchant:2")));
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class); MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            pit.when(ThePit::getInstance).thenReturn(plugin);
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, target, EntityDamageEvent.DamageCause.PROJECTILE, 4);
            event.setCancelled(true);enchant.onAcceptedHit(event);
            assertEquals(-1,enchant.getMarkedMeleeLevel(attacker,target));
            event.setCancelled(false);enchant.onAcceptedHit(event);
            assertEquals(2,enchant.getMarkedMeleeLevel(attacker,target));
            enchant.onQuit(new PlayerQuitEvent(target,""));
            assertEquals(-1,enchant.getMarkedMeleeLevel(attacker,target));
        }
    }
}
