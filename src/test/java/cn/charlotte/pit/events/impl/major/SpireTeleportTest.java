package cn.charlotte.pit.events.impl.major;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.medal.impl.challenge.SpireFloorMedal;
import cn.charlotte.pit.util.chat.TitleUtil;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SpireTeleportTest {
    private static final int[] LIVE_FEET_Y = {78, 94, 109, 125, 140, 155, 171, 185, 198};

    @Test
    public void allNineFloorsLandOnTheirOwnFloorInTheBundledSchematic() throws Exception {
        World world = schematicWorld();
        Random random = new Random(20260905);
        for (int floor = 0; floor < LIVE_FEET_Y.length; floor++) {
            Location center = new Location(world, -66.31957244873047, LIVE_FEET_Y[floor], -0.8025597929954529);
            for (int attempt = 0; attempt < 200; attempt++) {
                Location result = SpireTeleport.findSafeLocation(center, floor, random);
                assertNotNull("floor " + (floor + 1), result);
                assertEquals(LIVE_FEET_Y[floor] + 0.05, result.getY(), 0.00001);
                assertEquals(Material.WOOL, world.getBlockAt(result.getBlockX(), result.getBlockY() - 1, result.getBlockZ()).getType());
                assertEquals(Material.AIR, world.getBlockAt(result.getBlockX(), result.getBlockY(), result.getBlockZ()).getType());
                assertEquals(Material.AIR, world.getBlockAt(result.getBlockX(), result.getBlockY() + 1, result.getBlockZ()).getType());
                assertEquals(result.getBlockX() + 0.5, result.getX(), 0);
                assertEquals(result.getBlockZ() + 0.5, result.getZ(), 0);
            }
        }
    }

    @Test
    public void embeddedSecondFloorIsLiftedAboveTheFloorButBadThirdFloorNeverFallsToSecond() throws Exception {
        World world = schematicWorld();
        Location embedded = new Location(world, -66.31957244873047, 93.2381820678711, -0.8025597929954529);
        Location result = SpireTeleport.findSafeLocation(embedded, 1, new Random(1));
        assertNotNull(result);
        assertEquals(94.05, result.getY(), 0.00001);

        Location belowThirdFloor = embedded.clone();
        belowThirdFloor.setY(105.23251342773438);
        assertNull(SpireTeleport.findSafeLocation(belowThirdFloor, 2, new Random(1)));
    }

    @Test
    public void blockedEdgesFallBackToClearCentreOnTheSameFloor() {
        World world = world(position -> {
            if (position[1] == 99) return Material.WOOL;
            if (Math.abs(position[0]) > 3 || Math.abs(position[2]) > 3) return Material.STAINED_GLASS;
            return Material.AIR;
        });
        Location result = SpireTeleport.findSafeLocation(new Location(world, 0.5, 100, 0.5), 0, new Random(2));
        assertNotNull(result);
        assertEquals(0.5, result.getX(), 0);
        assertEquals(100.05, result.getY(), 0.00001);
        assertEquals(0.5, result.getZ(), 0);
    }

    @Test
    public void noGroundPartialBlocksAndObstructedHeadroomAreRejected() {
        for (Material support : new Material[]{Material.AIR, Material.WATER, Material.FENCE, Material.STEP}) {
            World world = world(position -> position[1] == 99 ? support : Material.AIR);
            assertNull(support.name(), SpireTeleport.findSafeLocation(new Location(world, 0.5, 100, 0.5), 0, new Random(3)));
        }
        World blocked = world(position -> position[1] >= 99 ? Material.WOOL : Material.AIR);
        assertNull(SpireTeleport.findSafeLocation(new Location(blocked, 0.5, 100, 0.5), 0, new Random(3)));
    }

    @Test
    public void extraConfiguredPointDoesNotCreateATenthFloorOrAllowTopFloorKillsToOverflow() throws Exception {
        SpireEvent spire = new SpireEvent();
        Player killer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(killer.getUniqueId()).thenReturn(uuid);
        SpireEvent.PlayerSpireData data = new SpireEvent.PlayerSpireData();
        field(data, "floor").setInt(data, 7);
        participants(spire).put(uuid, data);

        PitConfig config = mock(PitConfig.class);
        when(config.getSpireFloorLoc()).thenReturn(Collections.nCopies(10, new Location(null, 0, 100, 0)));
        ThePit plugin = mock(ThePit.class);
        when(plugin.getPitConfig()).thenReturn(config);
        PitKillEvent kill = mock(PitKillEvent.class);
        when(kill.isProgressionEligible()).thenReturn(true);
        when(kill.isHumanPvp()).thenReturn(true);
        when(kill.getKiller()).thenReturn(killer);
        when(kill.getTarget()).thenReturn(mock(Player.class));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<TitleUtil> titles = mockStatic(TitleUtil.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedConstruction<SpireFloorMedal> medals = mockConstruction(SpireFloorMedal.class)) {
            pit.when(ThePit::getInstance).thenReturn(plugin);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            spire.onKill(kill);
            assertEquals(8, field(data, "floor").getInt(data));
            spire.onKill(kill);
            assertEquals(8, field(data, "floor").getInt(data));
            assertEquals(1, medals.constructed().size());
        }
    }

    @Test
    public void successfulTeleportClearsMomentumAndFallDistanceButCancellationDoesNot() throws Exception {
        World world = schematicWorld();
        PitConfig config = mock(PitConfig.class);
        List<Location> floors = new ArrayList<>();
        for (int y : LIVE_FEET_Y) floors.add(new Location(world, -66.31957244873047, y, -0.8025597929954529));
        when(config.getSpireFloorLoc()).thenReturn(floors);
        ThePit plugin = mock(ThePit.class);
        when(plugin.getPitConfig()).thenReturn(config);
        Player player = mock(Player.class);
        SpireEvent spire = new SpireEvent();
        SpireEvent.PlayerSpireData data = new SpireEvent.PlayerSpireData();
        Method teleport = SpireEvent.class.getDeclaredMethod("teleportPlayer", Player.class, SpireEvent.PlayerSpireData.class);
        teleport.setAccessible(true);
        try (MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(player.teleport(any(Location.class))).thenReturn(false);
            assertEquals(false, teleport.invoke(spire, player, data));
            verify(player, never()).setVelocity(any());
            verify(player, never()).setFallDistance(anyFloat());
            when(player.teleport(any(Location.class))).thenReturn(true);
            assertEquals(true, teleport.invoke(spire, player, data));
            verify(player).setVelocity(new Vector());
            verify(player).setFallDistance(0);
        }
    }

    @Test
    public void deferredTeleportRejectsParticipantsWhoLeftRejoinedOrBelongToAnEarlierEvent() throws Exception {
        SpireEvent spire = new SpireEvent();
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        SpireEvent.PlayerSpireData data = new SpireEvent.PlayerSpireData();
        field(data, "inArea").setBoolean(data, true);
        Map<UUID, SpireEvent.PlayerSpireData> participants = participants(spire);
        participants.put(uuid, data);
        Method current = SpireEvent.class.getDeclaredMethod("isCurrentParticipant", Player.class, SpireEvent.PlayerSpireData.class, long.class);
        current.setAccessible(true);
        assertEquals(true, current.invoke(spire, player, data, 0L));
        participants.remove(uuid);
        assertEquals(false, current.invoke(spire, player, data, 0L));
        participants.put(uuid, new SpireEvent.PlayerSpireData());
        assertEquals(false, current.invoke(spire, player, data, 0L));
        participants.put(uuid, data);
        field(spire, "generation").setLong(spire, 1L);
        assertEquals(false, current.invoke(spire, player, data, 0L));
    }

    private World schematicWorld() throws Exception {
        CompoundTag schematic;
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream("spire.schematic");
             NBTInputStream input = new NBTInputStream(new GZIPInputStream(resource))) {
            schematic = (CompoundTag) input.readNamedTag().getTag();
        }
        int width = schematic.getShort("Width");
        int height = schematic.getShort("Height");
        int length = schematic.getShort("Length");
        int minX = -67 + schematic.getInt("WEOffsetX");
        int minY = 78 + schematic.getInt("WEOffsetY");
        int minZ = -1 + schematic.getInt("WEOffsetZ");
        byte[] blocks = schematic.getByteArray("Blocks");
        return world(position -> {
            int x = position[0] - minX;
            int y = position[1] - minY;
            int z = position[2] - minZ;
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) return Material.AIR;
            return Material.getMaterial(blocks[(y * length + z) * width + x] & 0xff);
        });
    }

    private World world(Function<int[], Material> materials) {
        World world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(256);
        Map<Material, Block> blocks = new EnumMap<>(Material.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Material material = materials.apply(new int[]{invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)});
            return blocks.computeIfAbsent(material, type -> {
                Block block = mock(Block.class);
                when(block.getType()).thenReturn(type);
                return block;
            });
        });
        return world;
    }

    private Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, SpireEvent.PlayerSpireData> participants(SpireEvent spire) throws Exception {
        return (Map<UUID, SpireEvent.PlayerSpireData>) field(spire, "dataMap").get(spire);
    }
}
