package cn.charlotte.pit.util.bossbar;

import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BossBarTest {
    private MockedStatic<Bukkit> bukkit;
    private TestBar bar;
    private Player player;
    private World world;
    private UUID id;
    private Location location;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        Plugin owner = mock(Plugin.class);
        when(owner.isEnabled()).thenReturn(true);
        when(owner.getLogger()).thenReturn(Logger.getAnonymousLogger());
        id = UUID.randomUUID();
        world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        location = new Location(world, 0, 64, 0);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenAnswer(call -> location.getWorld());
        when(player.getLocation()).thenAnswer(call -> location.clone());
        when(player.getEyeHeight()).thenReturn(1.62);
        when(player.isOnline()).thenReturn(true);
        bar = new TestBar(owner);
        bar.players.put(id, player);
    }

    @After
    public void tearDown() {
        if (bar != null) bar.close();
        if (bukkit != null) bukkit.close();
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private Map<Integer, Object> metadata(PacketPlayOutEntityMetadata packet) throws Exception {
        Map<Integer, Object> data = new HashMap<>();
        for (Object entry : (List<?>) field(packet, "b")) {
            data.put((Integer) field(entry, "b"), field(entry, "c"));
        }
        return data;
    }

    private PacketPlayOutEntityMetadata lastMetadata() {
        for (int i = bar.packets.size() - 1; i >= 0; i--) {
            if (bar.packets.get(i) instanceof PacketPlayOutEntityMetadata) return (PacketPlayOutEntityMetadata) bar.packets.get(i);
        }
        throw new AssertionError("No metadata update");
    }

    private long packets(Class<?> type) { return bar.packets.stream().filter(type::isInstance).count(); }

    @Test
    public void lateJoinStartsAtTheCurrentProgressAndUsesHiddenSilentWitherMetadata() throws Exception {
        bar.setProgress(0.4);
        bar.addPlayer(player);
        assertEquals(1, packets(PacketPlayOutSpawnEntityLiving.class));
        Map<Integer, Object> data = metadata(lastMetadata());
        assertEquals("first", data.get(2));
        assertEquals(120F, ((Number) data.get(6)).floatValue(), 0.0001F);
        assertEquals((byte) 32, data.get(0));
        assertEquals((byte) 0, data.get(3));
        assertEquals((byte) 1, data.get(4));
        assertEquals(880, data.get(20));
    }

    @Test
    public void titleAndHealthAreUpdatedTogetherOnTheSameEntity() throws Exception {
        bar.addPlayer(player);
        int initialId = bar.getWithers().get(id).getId();
        bar.packets.clear();
        bar.setTitle("second");
        bar.setProgress(0.25);
        bar.update();
        Map<Integer, Object> data = metadata(lastMetadata());
        assertEquals("second", data.get(2));
        assertEquals(75F, ((Number) data.get(6)).floatValue(), 0.0F);
        assertEquals(initialId, field(lastMetadata(), "a"));
        assertEquals(0, packets(PacketPlayOutSpawnEntityLiving.class));
    }

    @Test
    public void everyPitchKeepsTheAnchorAwayFromTheEyeAndInTheLookDirection() {
        for (float pitch : new float[]{-90F, -45F, 0F, 45F, 90F}) {
            Location eye = new Location(world, 12, 90, -20, 135, pitch);
            Location anchor = bar.getWitherLocation(eye);
            assertEquals(48.0, anchor.distance(eye), 0.00001);
            assertEquals(1.0, anchor.toVector().subtract(eye.toVector()).normalize().dot(eye.getDirection()), 0.00001);
        }
    }

    @Test
    public void movementUpdatesExistingEntityAndPeriodicResyncRecoversDiscardedEntities() {
        bar.addPlayer(player);
        int entityId = bar.getWithers().get(id).getId();
        bar.packets.clear();
        location.setYaw(180);
        bar.now += 60_000_000L;
        bar.update(player, location);
        assertEquals(1, packets(PacketPlayOutEntityTeleport.class));
        bar.now += 2_100_000_000L;
        bar.update();
        assertEquals(1, packets(PacketPlayOutSpawnEntityLiving.class));
        assertEquals(entityId, bar.getWithers().get(id).getId());
    }

    @Test
    public void worldChangeDestroysOldEntityAndRebuildsOnlyAfterTheDelayedCallback() {
        bar.addPlayer(player);
        int oldId = bar.getWithers().get(id).getId();
        World next = mock(World.class);
        when(next.getUID()).thenReturn(UUID.randomUUID());
        location.setWorld(next);
        bar.update();
        assertTrue(bar.getWithers().isEmpty());
        assertEquals(1, bar.getPendingPlayerCount());
        bar.runPending();
        assertEquals(0, bar.getPendingPlayerCount());
        assertNotEquals(oldId, bar.getWithers().get(id).getId());
    }

    @Test
    public void logoutAndEndingTheEventPreventDelayedRespawnFromRevivingTheBar() {
        bar.addPlayer(player);
        bar.rebuild(player);
        bar.removePlayer(player);
        bar.runPending();
        assertFalse(bar.hasPlayers());
        bar.addPlayer(player);
        bar.rebuild(player);
        bar.setTitle("§a §l");
        bar.runPending();
        assertFalse(bar.hasPlayers());
    }

    @Test
    public void modernClientsUsePublicShowHideAndNeverReceiveAnEntity() {
        bar.protocol = 774;
        bar.addPlayer(player);
        assertTrue(bar.modern.visible);
        assertEquals(1, bar.getModernPlayerCount());
        assertTrue(bar.packets.isEmpty());
        bar.setTitle("modern second");
        bar.setProgress(0.35);
        bar.update();
        assertEquals("modern second", bar.modern.title);
        assertEquals(0.35F, bar.modern.health, 0.00001);
        bar.setTitle("");
        assertFalse(bar.modern.visible);
        assertTrue(bar.modern.players.isEmpty());
    }

    @Test
    public void unknownProtocolAndFailedModernApiNeverFallBackToAFakeWither() {
        bar.protocol = -1;
        bar.addPlayer(player);
        assertFalse(bar.hasPlayers());
        assertTrue(bar.packets.isEmpty());
        bar.protocol = 774;
        bar.failModern = true;
        bar.addPlayer(player);
        assertFalse(bar.hasPlayers());
        assertTrue(bar.packets.isEmpty());
    }

    @Test
    public void invalidProgressStaysFiniteAndCloseClearsPendingRebuilds() throws Exception {
        bar.addPlayer(player);
        bar.setProgress(Double.NaN);
        bar.update();
        assertEquals(0.5F, ((Number) metadata(lastMetadata()).get(6)).floatValue(), 0.0F);
        bar.rebuild(player);
        bar.close();
        bar.runPending();
        assertFalse(bar.hasPlayers());
    }

    @Test
    public void periodicResyncDestroysTheOldClientEntityBeforeSendingCurrentState() throws Exception {
        bar.addPlayer(player);
        bar.packets.clear();
        bar.setTitle("current-countdown");
        bar.setProgress(0.3);
        bar.now += 2_100_000_000L;
        bar.update();
        assertTrue(bar.packets.get(0) instanceof PacketPlayOutEntityDestroy);
        assertTrue(bar.packets.get(1) instanceof PacketPlayOutSpawnEntityLiving);
        assertEquals("current-countdown", metadata(lastMetadata()).get(2));
        assertEquals(90F, ((Number) metadata(lastMetadata()).get(6)).floatValue(), 0.001F);
    }

    @Test
    public void npcPlayersDoNotReceiveOrRetainEventBars() {
        when(player.hasMetadata("NPC")).thenReturn(true);
        bar.addPlayer(player);
        assertFalse(bar.hasPlayers());
        assertTrue(bar.packets.isEmpty());
        when(player.hasMetadata("NPC")).thenReturn(false);
        bar.addPlayer(player);
        when(player.hasMetadata("NPC")).thenReturn(true);
        bar.update();
        assertFalse(bar.hasPlayers());
    }

    private static final class TestBar extends BossBar {
        final Map<UUID, Player> players = new HashMap<>();
        final List<Packet<?>> packets = new ArrayList<>();
        final List<Runnable> scheduled = new ArrayList<>();
        final Modern modern = new Modern();
        int protocol = 47;
        long now = 100_000_000_000L;
        boolean failModern;
        TestBar(Plugin plugin) { super("first", plugin); }
        @Override protected long clock() { return now; }
        @Override protected Player findPlayer(UUID id) { return players.get(id); }
        @Override protected int getClientProtocolVersion(Player player) { return protocol; }
        @Override protected void sendPacket(Player player, Packet<?> packet) { packets.add(packet); }
        @Override protected void later(Runnable action, long ticks) { scheduled.add(action); }
        @Override protected Object createModernBossBar(String title, float health) {
            if (failModern) throw new IllegalStateException("unavailable");
            return modern;
        }
        void runPending() {
            List<Runnable> actions = new ArrayList<>(scheduled); scheduled.clear(); actions.forEach(Runnable::run);
        }
        @Override protected EntityWither createWither(Player player) {
            return new EntityWither(null);
        }
    }

    public static final class Modern {
        String title;
        float health;
        boolean visible;
        final Set<UUID> players = new HashSet<>();
        public Modern setTitle(String title) { this.title=title;return this; }
        public Modern setHealth(float health) { this.health=health;return this; }
        public Modern addPlayer(UUID id) { players.add(id);return this; }
        public Modern removePlayer(UUID id) { players.remove(id);return this; }
        public Set<UUID> getPlayers() { return players; }
        public Modern show() { visible=true;return this; }
        public Modern hide() { visible=false;return this; }
        @SuppressWarnings("unused") private void setVisible(boolean visible) { throw new AssertionError("Private API must not be used"); }
    }
}
