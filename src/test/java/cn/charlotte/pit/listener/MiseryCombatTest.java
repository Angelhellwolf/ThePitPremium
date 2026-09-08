package cn.charlotte.pit.listener;

import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.enchantment.type.dark_normal.MiseryEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MiseryCombatTest {
    private Player wearing(Material material) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getLeggings()).thenReturn(material == null ? null : new ItemStack(material));
        return player;
    }

    @Test
    public void miseryAddsTrueDamageToTheOriginalMeleeHitWithoutNestedDamage() {
        Player attacker = wearing(Material.LEATHER_LEGGINGS);
        Player target = wearing(Material.LEATHER_LEGGINGS);
        AtomicDouble trueDamage = new AtomicDouble(0.5);
        AtomicDouble boost = new AtomicDouble(1.2);
        try (MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            new MiseryEnchant().handleAttackEntity(3, attacker, target, 6, trueDamage, boost, new AtomicBoolean());
            assertEquals(1.5, trueDamage.get(), 0);
            assertEquals(1.2, boost.get(), 0);
            players.verifyNoInteractions();
        }
    }

    @Test
    public void miseryPantsFireOnArrowHitsEvenWhenSomberDisablesNormalEnchants() {
        Player shooter = wearing(Material.LEATHER_LEGGINGS);
        Player target = wearing(Material.LEATHER_LEGGINGS);
        ItemStack pants = shooter.getInventory().getLeggings();
        MiseryEnchant misery = spy(new MiseryEnchant());
        doReturn(3).when(misery).getItemEnchantLevel(pants);
        EnchantmentFactor factory = new EnchantmentFactor();
        factory.getPlayerShootEntities().add(misery);
        AtomicDouble trueDamage = new AtomicDouble();
        try (MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            players.when(() -> PlayerUtil.shouldIgnoreEnchant(shooter, target)).thenReturn(true);
            new GameEffectListener().processArmorShootEnchantments(factory, shooter, target,
                    mock(Arrow.class), 6, trueDamage, new AtomicDouble(1), new AtomicBoolean());
            assertEquals(1, trueDamage.get(), 0);
        }
    }

    @Test
    public void missingOrNonLeatherPantsAndCancelledHitsDoNotTakeMiseryDamage() {
        MiseryEnchant misery = new MiseryEnchant();
        for (Material material : new Material[] {null, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS}) {
            AtomicDouble trueDamage = new AtomicDouble();
            misery.handleAttackEntity(3, null, wearing(material), 6, trueDamage, new AtomicDouble(1), new AtomicBoolean());
            assertEquals(0, trueDamage.get(), 0);
        }
        AtomicDouble trueDamage = new AtomicDouble();
        misery.handleShootEntity(3, null, wearing(Material.LEATHER_LEGGINGS), 6, trueDamage,
                new AtomicDouble(1), new AtomicBoolean(true));
        assertEquals(0, trueDamage.get(), 0);
    }
}
