package cn.charlotte.pit.bot;

import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicPlayerCombatTest {
    private org.mockito.MockedStatic<Bukkit> bukkit;
    private World world;
    private PublicPitArena arena;
    private PublicPlayerCombat combat;
    private final List<UUID> cached = new ArrayList<>();
    @Before public void setup() {
        bukkit = mockStatic(Bukkit.class);
        world = mock(World.class);
        PitConfig pit = mock(PitConfig.class);
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < 8; i++) locations.add(new Location(world, 10, 107, -6));
        when(pit.getSpawnLocations()).thenReturn(locations);
        when(pit.getPitLocA()).thenReturn(new Location(world, -30, 126, -30));
        when(pit.getPitLocB()).thenReturn(new Location(world, 26, 95, 35));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        arena = new PublicPitArena(config, pit);
        combat = new PublicPlayerCombat(arena, new Random(1));
    }
    @After public void cleanup() {
        try { for (UUID id : cached) PlayerProfile.getCacheProfile().remove(id); }
        finally { if (bukkit != null) bukkit.close(); }
    }
    @Test public void safeZoneAndMapBoundsFollowTheExistingPitGeometry() {
        assertTrue(arena.safeZone(new Location(world, 0, 107, 10)));
        assertFalse(arena.combat(new Location(world, 0, 107, 10)));
        assertTrue(arena.combat(new Location(world, 0, 77, 0)));
        assertFalse(arena.combat(new Location(world, 0, 40, 0)));
        assertTrue(arena.outside(new Location(world, 200, 77, 0)));
        assertTrue(arena.outside(new Location(mock(World.class), 0, 77, 0)));
    }
    @Test public void acquisitionRejectsNpcsSpectatorsSafeZonePlayersAndBlockedSight() {
        Player bot = player(0, 77, 0, true), target = player(2, 77, 0, false);
        PublicPlayerState state = new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0, bot.getUniqueId(), "MapleRush", "MapleRush"));
        state.entity = bot;
        when(bot.hasLineOfSight(target)).thenReturn(true);
        assertSame(target, combat.findTarget(state, Collections.singletonList(target)));
        when(target.isDead()).thenReturn(true);
        assertFalse(combat.targetable(bot, target));
        when(target.isDead()).thenReturn(false);
        when(target.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(combat.targetable(bot, target));
        when(target.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(target.getLocation()).thenReturn(new Location(world, 2, 107, 0));
        assertFalse(combat.targetable(bot, target));
        when(target.getLocation()).thenReturn(new Location(world, 2, 77, 0));
        when(target.hasMetadata("NPC")).thenReturn(true);
        assertFalse(combat.targetable(bot, target));
        when(target.hasMetadata("NPC")).thenReturn(false);
        when(bot.hasLineOfSight(target)).thenReturn(false);
        assertNull(combat.findTarget(state, Collections.singletonList(target)));
    }
    @Test public void movementUsesPlayerWalkSpeedSprintAndPotionModifiers() {
        Player player = mock(Player.class);
        when(player.getWalkSpeed()).thenReturn(0.2F);
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
        assertEquals(0.20, PublicPlayerCombat.speed(player, false), 0.000001);
        assertEquals(0.28, PublicPlayerCombat.speed(player, true), 0.000001);
        when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(new PotionEffect(PotionEffectType.SPEED, 100, 1)));
        assertEquals(0.392, PublicPlayerCombat.speed(player, true), 0.000001);
    }
    private Player player(double x, double y, double z, boolean npc) {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID(); cached.add(uuid);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
        when(player.hasMetadata("NPC")).thenReturn(npc);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.isLoaded()).thenReturn(true); when(profile.isBot()).thenReturn(npc); when(profile.isInArena()).thenReturn(y < 95);
        PlayerProfile.getCacheProfile().put(uuid, profile);
        return player;
    }
}
