package cn.charlotte.pit.util.bossbar;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.*;

/** Checks actual NMS packet contents without registering a player, adding a world mob or sending to clients. */
public final class BossBarRuntimeCheck {
    private BossBarRuntimeCheck() { }

    public static List<String> run(Plugin owner) throws Exception {
        WorldServer world = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        int entitiesBefore = world.entityList.size();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        EntityPlayer handle = new EntityPlayer(server, world, new GameProfile(UUID.randomUUID(), "PitBarCheck"),
                new PlayerInteractManager(world));
        handle.setPositionRotation(0.25, 90, 0.25, 0, 0);
        Player player = (Player) handle.getBukkitEntity();
        Recorder bar = new Recorder(owner, player);
        List<String> result = new ArrayList<>();
        try {
            bar.setProgress(0.4);
            bar.addPlayer(player);
            require(bar.getWithers().size() == 1, "legacy entity was not created");
            EntityWither wither = bar.getWithers().get(player.getUniqueId());
            Map<Integer, Object> initial = bar.lastMetadata();
            require("probe-first".equals(initial.get(2)), "initial title");
            require(Math.abs(((Number) initial.get(6)).floatValue() - 120F) < 0.001F, "initial progress");
            require(((Number) initial.get(0)).intValue() == 32, "invisibility");
            require(((Number) initial.get(3)).intValue() == 0, "hidden nameplate");
            require(((Number) initial.get(4)).intValue() == 1, "silence");
            require(((Number) initial.get(20)).intValue() == 880, "small renderer scale");
            result.add("BOSSBAR_SMOKE legacy-spawn-and-current-progress PASS");

            int entityId = wither.getId();
            bar.packets.clear();
            bar.setTitle("probe-updated");
            bar.setProgress(0.25);
            bar.update();
            Map<Integer, Object> changed = bar.lastMetadata();
            require("probe-updated".equals(changed.get(2)), "updated title");
            require(((Number) changed.get(6)).floatValue() == 75F, "updated health");
            require(bar.getWithers().get(player.getUniqueId()).getId() == entityId, "stable entity id");
            result.add("BOSSBAR_SMOKE title-health-snapshot PASS");

            for (float pitch : new float[]{-90F, 0F, 90F}) {
                handle.setPositionRotation(0.25, 90, 0.25, 135, pitch);
                bar.now += 100_000_000L;
                bar.update(player);
                Location eye = player.getEyeLocation();
                double dx = wither.locX - eye.getX(), dy = wither.locY - eye.getY(), dz = wither.locZ - eye.getZ();
                require(Math.abs(Math.sqrt(dx * dx + dy * dy + dz * dz) - 48.0) < 0.06, "anchor distance");
            }
            result.add("BOSSBAR_SMOKE pitch-and-position-updates PASS");

            bar.rebuild(player);
            require(bar.getWithers().isEmpty() && bar.getPendingPlayerCount() == 1, "deferred rebuild");
            bar.runPending();
            require(bar.getWithers().get(player.getUniqueId()).getId() != entityId, "fresh entity after reset");
            bar.rebuild(player);
            bar.setTitle("");
            bar.runPending();
            require(!bar.hasPlayers(), "cancel late rebuild");
            require(world.entityList.size() == entitiesBefore, "world entity count changed");
            result.add("BOSSBAR_SMOKE rebuild-cancellation-and-world-cleanup PASS");

            Object modern = bar.nativeBar();
            modern.getClass().getMethod("show").invoke(modern);
            modern.getClass().getMethod("setTitle", String.class).invoke(modern, "modern-updated");
            modern.getClass().getMethod("setHealth", float.class).invoke(modern, 0.25F);
            require(Boolean.TRUE.equals(modern.getClass().getMethod("isVisible").invoke(modern)), "native show");
            require("modern-updated".equals(modern.getClass().getMethod("getTitle").invoke(modern)), "native title");
            require(((Number) modern.getClass().getMethod("getHealth").invoke(modern)).floatValue() == 0.25F, "native health");
            modern.getClass().getMethod("hide").invoke(modern);
            require(Boolean.FALSE.equals(modern.getClass().getMethod("isVisible").invoke(modern)), "native hide");
            result.add("BOSSBAR_SMOKE actual-via-public-show-hide-and-updates PASS");
            return result;
        } finally {
            bar.close();
        }
    }

    private static void require(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class Recorder extends BossBar {
        final Player player;
        final List<Packet<?>> packets = new ArrayList<>();
        final List<Runnable> pending = new ArrayList<>();
        long now = 100_000_000_000L;
        Recorder(Plugin plugin, Player player) { super("probe-first", plugin); this.player = player; }
        @Override protected boolean connected(Player candidate) { return candidate == player; }
        @Override protected Player findPlayer(UUID id) { return id.equals(player.getUniqueId()) ? player : null; }
        @Override protected int getClientProtocolVersion(Player player) { return 47; }
        @Override protected void sendPacket(Player player, Packet<?> packet) { packets.add(packet); }
        @Override protected long clock() { return now; }
        @Override protected void later(Runnable action, long ticks) { pending.add(action); }
        Object nativeBar() throws ReflectiveOperationException { return super.createModernBossBar("native-probe", 0.5F); }
        void runPending() { List<Runnable> actions = new ArrayList<>(pending); pending.clear(); actions.forEach(Runnable::run); }
        Map<Integer, Object> lastMetadata() throws ReflectiveOperationException {
            for (int i = packets.size() - 1; i >= 0; i--) {
                if (!(packets.get(i) instanceof PacketPlayOutEntityMetadata)) continue;
                Map<Integer, Object> metadata = new HashMap<>();
                for (Object entry : (List<?>) field(packets.get(i), "b")) metadata.put((Integer) field(entry, "b"), field(entry, "c"));
                return metadata;
            }
            throw new IllegalStateException("Missing metadata packet");
        }
    }
}
