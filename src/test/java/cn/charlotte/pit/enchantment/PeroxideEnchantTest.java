package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.type.normal.PeroxideEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PeroxideEnchantTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerUtil> players;
    private PluginManager pluginManager;
    private Player player;
    private Player attacker;
    private ItemStack leggings;
    private UUID uuid;
    private PeroxideEnchant enchant;
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Double> healingRequests = new ArrayList<>();
    private final AtomicDouble health = new AtomicDouble(20);

    @Before
    public void setUp() {
        PeroxideEnchant.cooldown.clear();
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        players = mockStatic(PlayerUtil.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        pluginManager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        Server server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1L))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        // This 1.8 API retains both legacy int and current double health descriptors.
        // Supply a default answer to avoid Mockito selecting the legacy descriptor.
        player = mock(CraftPlayer.class, call -> {
            if (call.getMethod().getName().equals("getHealth")) {
                if (call.getMethod().getReturnType() == int.class) return (int) health.get();
                return health.get();
            }
            if (call.getMethod().getName().equals("getMaxHealth")) {
                if (call.getMethod().getReturnType() == int.class) return 20;
                return 20.0;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        attacker = mock(Player.class);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        doAnswer(call -> { health.set(((Number) call.getArgument(0)).doubleValue()); return null; }).when(player).setHealth(anyDouble());
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        when(inventory.getLeggings()).thenReturn(leggings);
        enchant = spy(new PeroxideEnchant());
        doReturn(3).when(enchant).getItemEnchantLevel(leggings);
        // Keep the established health/debuff service outside this enchantment test.
        players.when(() -> PlayerUtil.heal(eq(player), anyDouble())).thenAnswer(call -> {
            healingRequests.add(call.getArgument(1));
            return null;
        });
    }

    @After
    public void tearDown() {
        PeroxideEnchant.cooldown.clear();
        players.close();
        pit.close();
        bukkit.close();
    }

    private EntityDamageByEntityEvent hit() {
        return new EntityDamageByEntityEvent(attacker, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4);
    }

    @Test
    public void registersThePostDamageListenerInsteadOfThePreDamageCallback() {
        EnchantmentFactor factory = new EnchantmentFactor();
        factory.init(Collections.singletonList(PeroxideEnchant.class));
        verify(pluginManager).registerEvents(any(PeroxideEnchant.class), any(ThePit.class));
        assertFalse(factory.getPlayerDamageds().stream().anyMatch(e -> e instanceof PeroxideEnchant));
    }

    @Test
    public void restoresTheDocumentedHeartsAfterTheHitAtEveryLevel() {
        double[] healed = {1.0, 1.5, 2.0};
        for (int level = 1; level <= 3; level++) {
            PeroxideEnchant.cooldown.clear();
            doReturn(level).when(enchant).getItemEnchantLevel(leggings);
            health.set(20);
            enchant.onDamaged(hit());
            assertEquals(level - 1, healingRequests.size());
            health.set(16); // Bukkit commits the original damage after event listeners.
            tasks.get(tasks.size() - 1).run();
            assertEquals(healed[level - 1], healingRequests.get(level - 1), 0.000001);
        }
    }

    @Test
    public void repeatedHitsRespectTheCooldownAndOnlyRequestOneHeal() {
        enchant.onDamaged(hit());
        enchant.onDamaged(hit());
        assertEquals(1, tasks.size());
        health.set(19.5);
        tasks.get(0).run();
        assertEquals(Collections.singletonList(2.0), healingRequests);
        enchant.onDamaged(hit());
        assertEquals(1, tasks.size());
        PeroxideEnchant.cooldown.put(uuid, new Cooldown(0));
        enchant.onDamaged(hit());
        assertEquals(2, tasks.size());
    }

    @Test
    public void cancelledZeroDamageAndMissingEnchantHitsDoNotStartCooldown() {
        EntityDamageByEntityEvent cancelled = hit();
        cancelled.setCancelled(true);
        enchant.onDamaged(cancelled);
        EntityDamageByEntityEvent zero = hit();
        zero.setDamage(0);
        enchant.onDamaged(zero);
        doReturn(-1).when(enchant).getItemEnchantLevel(leggings);
        enchant.onDamaged(hit());
        assertTrue(tasks.isEmpty());
        assertFalse(PeroxideEnchant.cooldown.containsKey(uuid));
    }

    @Test
    public void deathOrQuitInvalidatesTheOldLifeHealingTask() {
        health.set(8);
        enchant.onDamaged(hit());
        enchant.onDeath(new PlayerDeathEvent(player, new ArrayList<>(), 0, null));
        tasks.get(0).run();
        assertTrue(healingRequests.isEmpty());
        enchant.onDamaged(hit());
        enchant.onQuit(new PlayerQuitEvent(player, ""));
        tasks.get(1).run();
        assertTrue(healingRequests.isEmpty());
    }

    @Test
    public void damageCancelledLaterOrOfflinePlayerDoesNotReceivePendingHealing() {
        health.set(8);
        EntityDamageByEntityEvent event = hit();
        enchant.onDamaged(event);
        event.setCancelled(true);
        tasks.get(0).run();
        assertTrue(healingRequests.isEmpty());
        enchant.onDamaged(hit());
        when(player.isOnline()).thenReturn(false);
        tasks.get(1).run();
        assertTrue(healingRequests.isEmpty());
    }

    @Test
    public void arrowUsesTheWearersSuppressionRulesAndResolvesItsShooter() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, player,
                EntityDamageEvent.DamageCause.PROJECTILE, 4);
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(player, attacker)).thenReturn(true);
        enchant.onDamaged(event);
        assertTrue(tasks.isEmpty());
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(player, attacker)).thenReturn(false);
        players.when(() -> PlayerUtil.shouldIgnoreEnchant(attacker, player)).thenReturn(true);
        enchant.onDamaged(event);
        assertEquals(1, tasks.size());
    }

    @Test
    public void oldTaskCannotHealAfterQuitAndANewActivation() {
        enchant.onDamaged(hit());
        enchant.onQuit(new PlayerQuitEvent(player, ""));
        enchant.onDamaged(hit());
        tasks.get(0).run();
        assertTrue(healingRequests.isEmpty());
        tasks.get(1).run();
        assertEquals(Collections.singletonList(2.0), healingRequests);
    }

    @Test
    public void aHitAbsorbedByGoldenHeartsStillTriggersHealing() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getFinalDamage()).thenReturn(0.0);
        when(event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)).thenReturn(true);
        when(event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)).thenReturn(-4.0);
        health.set(10);
        enchant.onDamaged(event);
        assertEquals(1, tasks.size());
        tasks.get(0).run();
        assertEquals(Collections.singletonList(2.0), healingRequests);
    }

    @Test
    public void customRespawnInvalidatesHealingEvenWithoutANativeDeathEvent() {
        enchant.onDamaged(hit());
        new cn.charlotte.pit.util.combat.PlayerLife().onSpawn(new cn.charlotte.pit.event.PitPlayerSpawnEvent(player));
        tasks.get(0).run();
        assertTrue(healingRequests.isEmpty());
    }
}
