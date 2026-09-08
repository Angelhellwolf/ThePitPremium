package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.listener.CombatListener;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PitBotManagerTest {
    private PitBotManager manager;
    private CombatListener previous, settlement;
    private MockedStatic<Bukkit> bukkit;
    private PublicPlayerState state;
    private Player victim, killer;
    private NPC npc;
    @Before public void setup() throws Exception {
        ThePit plugin = mock(ThePit.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        bukkit = mockStatic(Bukkit.class);
        manager = new PitBotManager(plugin);
        previous = CombatListener.INSTANCE;
        settlement = mock(CombatListener.class); CombatListener.INSTANCE = settlement;
        UUID id = UUID.randomUUID();
        victim = mock(Player.class); killer = mock(Player.class);
        when(victim.getUniqueId()).thenReturn(id);
        when(victim.getLocation()).thenReturn(new Location(null, 0, 77, 0));
        when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
        npc = mock(NPC.class);
        when(npc.getNavigator()).thenReturn(mock(Navigator.class));
        when(npc.data()).thenReturn(mock(MetadataStore.class));
        state = new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0, id, "MapleRush", "MapleRush"));
        state.born(npc, victim, 0, 0); state.enteredPit(10);
        ((Map<UUID, PublicPlayerState>) field(manager, "identities").get(manager)).put(id, state);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenReturn(true); when(profile.isBot()).thenReturn(true);
        PlayerProfile.getCacheProfile().put(id, profile);
    }
    @After public void cleanup() {
        PlayerProfile.getCacheProfile().remove(state.identity.uuid);
        manager.shutdown();
        CombatListener.INSTANCE = previous;
        bukkit.close();
    }
    @Test public void cancelledOrNonlethalHitsAreNeverSettledEarly() {
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(killer, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 20);
        hit.setCancelled(true);
        manager.onDamage(hit); manager.onHit(hit);
        assertTrue(hit.isCancelled());
        verifyNoInteractions(settlement);
    }
    @Test public void defaultPublicSwitchLeavesPrivateTrainingDelegationAvailable() throws Exception {
        org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        assertFalse(manager.isPublicEnabled());
        manager.start();
        assertFalse(manager.isReady());
        assertNull(field(manager, "task").get(manager));
        JunxuTrainingBots privateBots = mock(JunxuTrainingBots.class);
        manager.setJunxuBots(privateBots);
        when(privateBots.isBot(killer)).thenReturn(true);
        when(privateBots.getPlayer(killer.getUniqueId())).thenReturn(killer);
        when(privateBots.handleDeath(killer, victim)).thenReturn(true);
        assertTrue(manager.isBot(killer));
        assertSame(killer, manager.getPlayer(killer.getUniqueId()));
        assertTrue(manager.handleDeath(killer, victim));
        verify(privateBots).handleDeath(killer, victim);
    }
    @Test public void duplicateDeathsAreSettledOnceAndTheIdentitySurvives() {
        state.hitHuman();
        assertTrue(manager.handleDeath(victim, killer));
        assertTrue(manager.handleDeath(victim, killer));
        verify(settlement, times(1)).handlePlayerDeath(victim, killer, false);
        verify(npc, times(1)).destroy();
        assertEquals(PublicPlayerState.Phase.RESPAWNING, state.phase);
        assertTrue(manager.isBot(victim));
        assertEquals(1, state.deaths);
    }
    @Test public void previousEntityWithTheSameUuidCannotKillTheNewLife() {
        Player old = mock(Player.class); when(old.getUniqueId()).thenReturn(state.identity.uuid);
        assertTrue(manager.handleDeath(old, killer));
        assertEquals(0, state.deaths);
        verifyNoInteractions(settlement);
    }
    @Test public void onlyCombatPhaseCanDealDamage() {
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(victim, killer, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 6);
        manager.onDamage(hit); assertFalse(hit.isCancelled());
        state.phase = PublicPlayerState.Phase.ENTERING;
        manager.onDamage(hit); assertTrue(hit.isCancelled());
    }
    @Test public void aFourthActorCannotBeCreatedEvenDuringRepeatedMaintenance() throws Exception {
        field(manager, "started").setBoolean(manager, true);
        NPCRegistry registry = mock(NPCRegistry.class);
        NPC one = managedNpc(), two = managedNpc(), three = managedNpc();
        when(registry.iterator()).thenAnswer(call -> Arrays.asList(one, two, three).iterator());
        try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistries).thenReturn(Collections.singletonList(registry));
            for (int i = 0; i < 100; i++) assertFalse(manager.spawn(state));
            verify(registry, never()).createNPC(any(), any(UUID.class), anyInt(), anyString());
        }
    }
    private NPC managedNpc() {
        NPC npc = mock(NPC.class); MetadataStore data = mock(MetadataStore.class);
        when(npc.data()).thenReturn(data);
        when(data.has("thepit-public-simulated-player")).thenReturn(true);
        when(npc.isSpawned()).thenReturn(true);
        return npc;
    }
    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
}
