package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.event.PitPotionEffectEvent;
import cn.charlotte.pit.enchantment.type.normal.ElectrolytesEnchant;
import cn.charlotte.pit.enchantment.type.normal.SprintDrainEnchant;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.PlayerUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CombatPotionsTest {
    @Test
    public void refreshedSpeedDoesNotShortenTheDurationGrantedByElectrolytes() {
        Player player = mock(Player.class);
        List<PotionEffect> effects = new ArrayList<>();
        effects.add(new PotionEffect(PotionEffectType.SPEED, 140, 1));
        when(player.getActivePotionEffects()).thenReturn(effects);
        when(player.addPotionEffect(any(PotionEffect.class), eq(true))).thenAnswer(call -> {
            effects.clear(); effects.add(call.getArgument(0)); return true;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            CombatPotions.extendSpeed(player, 3);
            assertEquals(200, effects.get(0).getDuration());
            new SprintDrainEnchant().handleShootEntity(3, player, mock(Player.class), 4,
                    new AtomicDouble(), new AtomicDouble(1), new AtomicBoolean());
            assertEquals(200, effects.get(0).getDuration());
            assertEquals(1, effects.get(0).getAmplifier());
        }
    }

    @Test
    public void speedOneGetsFullExtensionAndInfiniteEffectsDoNotOverflow() {
        Player player = mock(Player.class);
        try (MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(new PotionEffect(PotionEffectType.SPEED, 80, 0)));
            CombatPotions.extendSpeed(player, 3);
            players.verify(() -> PlayerUtil.addPotionEffect(eq(player), argThat(e -> e.getDuration() == 200 && e.getAmplifier() == 0), eq(true)));
            when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1)));
            CombatPotions.extendSpeed(player, 3);
            players.verify(() -> PlayerUtil.addPotionEffect(eq(player), argThat(e -> e.getDuration() == Integer.MAX_VALUE), eq(true)));
        }
    }

    @Test
    public void aStrongerExistingResistanceIsNotDowngradedByDirty() {
        Player player = mock(Player.class);
        when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 2)));
        try (MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            new cn.charlotte.pit.perk.type.prestige.DirtyPerk().handlePlayerKilled(1, player,
                    mock(Player.class), new AtomicDouble(), new AtomicDouble());
            players.verifyNoInteractions();
        }
    }

    @Test
    public void potionCancellationStillPreventsARefresh() {
        Player player = mock(Player.class);
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
        PluginManager manager = mock(PluginManager.class);
        doAnswer(call -> { ((PitPotionEffectEvent) call.getArgument(0)).setCancelled(true); return null; })
                .when(manager).callEvent(any(Event.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            CombatPotions.refresh(player, new PotionEffect(PotionEffectType.SPEED, 80, 0));
            verify(player, never()).addPotionEffect(any(PotionEffect.class), anyBoolean());
        }
    }

    @Test
    public void electrolytesReadsSpeedAfterOtherKillEffectsAndCannotCrossRespawn() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
        PluginManager manager = mock(PluginManager.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getPluginManager()).thenReturn(manager);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        Player player = mock(org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer.class, call -> {
            if (call.getMethod().getName().equals("getHealth")) {
                if (call.getMethod().getReturnType() == int.class) return 20;
                return 20.0;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getLeggings()).thenReturn(leggings);
        List<PotionEffect> effects = new ArrayList<>();
        when(player.getActivePotionEffects()).thenReturn(effects);
        List<Runnable> tasks = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        ThePit plugin = mock(ThePit.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return mock(BukkitTask.class); });
        ElectrolytesEnchant enchant = spy(new ElectrolytesEnchant());
        doReturn(3).when(enchant).getItemEnchantLevel(leggings);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            enchant.handlePlayerKilled(3, player, mock(Player.class), new AtomicDouble(), new AtomicDouble());
            effects.add(new PotionEffect(PotionEffectType.SPEED, 80, 0));
            tasks.get(0).run();
            players.verify(() -> PlayerUtil.addPotionEffect(eq(player), argThat(e -> e.getDuration() == 200), eq(true)));
            enchant.handlePlayerKilled(3, player, mock(Player.class), new AtomicDouble(), new AtomicDouble());
            new PlayerLife().onSpawn(new cn.charlotte.pit.event.PitPlayerSpawnEvent(player));
            tasks.get(1).run();
            players.verify(() -> PlayerUtil.addPotionEffect(eq(player), any(PotionEffect.class), eq(true)), times(1));
        }
    }
}
