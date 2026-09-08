package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.type.normal.ComboDamageEnchant;
import cn.charlotte.pit.enchantment.type.normal.DiamondBreakerEnchant;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantmentBalanceTest {
    private MockedStatic<Bukkit> bukkit;
    @Before public void mockServerBeforeProfileInitialization() { bukkit = mockStatic(Bukkit.class); }
    @After public void closeServerMock() { if (bukkit != null) bukkit.close(); }
    @Test
    public void diamondBonusScalesByLevelAndEachWornArmorPieceForBothWeapons() {
        DiamondBreakerEnchant enchant = new DiamondBreakerEnchant();
        Player target = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(target.getInventory()).thenReturn(inventory);
        Material[] materials = {Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET};
        double[][] bonuses = {{0, 0.05, 0.10, 0.15, 0.20},
                {0, 0.10, 0.20, 0.25, 0.25}, {0, 0.15, 0.25, 0.25, 0.25}};
        for (int level = 1; level <= 3; level++) {
            for (int pieces = 0; pieces <= 4; pieces++) {
                ItemStack[] armor = new ItemStack[4];
                for (int i = 0; i < pieces; i++) armor[i] = new ItemStack(materials[i]);
                when(inventory.getArmorContents()).thenReturn(armor);
                AtomicDouble boost = new AtomicDouble(1.2);
                AtomicDouble trueDamage = new AtomicDouble();
                enchant.handleAttackEntity(level, null, target, 6, trueDamage, boost, new AtomicBoolean());
                assertEquals(1.2 + bonuses[level - 1][pieces], boost.get(), 0.000001);
                assertEquals(0, trueDamage.get(), 0);
                boost.set(1.2);
                enchant.handleShootEntity(level, null, target, 6, trueDamage, boost, new AtomicBoolean());
                assertEquals(1.2 + bonuses[level - 1][pieces], boost.get(), 0.000001);
            }
            assertTrue(enchant.getUsefulnessLore(level).contains("+" + (5 * level) + "%"));
            assertTrue(enchant.getUsefulnessLore(level).contains("+25%"));
        }
    }

    @Test
    public void diamondWeaponsAndEmptyArmorSlotsDoNotAddArmorBonus() {
        Player target = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(target.getInventory()).thenReturn(inventory);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[] {
                null, new ItemStack(Material.AIR), new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.DIAMOND_SWORD)});
        AtomicDouble boost = new AtomicDouble(1);
        new DiamondBreakerEnchant().handleAttackEntity(3, null, target, 6,
                new AtomicDouble(), boost, new AtomicBoolean());
        assertEquals(1, boost.get(), 0);
    }

    @Test
    public void comboUsesOriginalTwentyThirtyFortyPercentAtTheCorrectHitIntervals() {
        Player attacker = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(uuid);
        PlayerProfile profile = mock(PlayerProfile.class);
        ComboDamageEnchant enchant = new ComboDamageEnchant();
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class)) {
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
            for (int level = 1; level <= 3; level++) {
                int interval = level == 1 ? 4 : 3;
                for (int hit = 1; hit <= interval * 2; hit++) {
                    when(profile.getMeleeHit()).thenReturn(hit);
                    when(profile.getBowHit()).thenReturn(hit);
                    double expected = 1 + (hit % interval == 0 ? (level + 1) * 0.1 : 0);
                    AtomicDouble boost = new AtomicDouble(1);
                    enchant.handleAttackEntity(level, attacker, null, 6, new AtomicDouble(), boost, new AtomicBoolean());
                    assertEquals(expected, boost.get(), 0.000001);
                    boost.set(1);
                    enchant.handleShootEntity(level, attacker, null, 6, new AtomicDouble(), boost, new AtomicBoolean());
                    assertEquals(expected, boost.get(), 0.000001);
                }
                assertTrue(enchant.getUsefulnessLore(level).contains("+" + (10 * level + 10) + "%"));
            }
        }
    }

    @Test
    public void maxComboAndDiamondSwordBuildIsCappedAtSixtyFivePercentAgainstThreeDiamondPieces() {
        Player attacker = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(uuid);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getMeleeHit()).thenReturn(3);
        Player target = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(target.getInventory()).thenReturn(inventory);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[] {
                new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS),
                new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)});
        try (MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class)) {
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
            AtomicDouble boost = new AtomicDouble(1);
            AtomicDouble trueDamage = new AtomicDouble();
            new ComboDamageEnchant().handleAttackEntity(3, attacker, target, 6, trueDamage, boost, new AtomicBoolean());
            new DiamondBreakerEnchant().handleAttackEntity(3, attacker, target, 6, trueDamage, boost, new AtomicBoolean());
            assertEquals(1.65, boost.get(), 0.000001);
            assertEquals(9.9, 6 * boost.get(), 0.000001);
            assertEquals(0, trueDamage.get(), 0);
        }
    }
}
