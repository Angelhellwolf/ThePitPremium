package cn.charlotte.pit.util.bossbar;

import cn.charlotte.pit.ThePit;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One event bar, with an isolated entity stream for legacy clients and Via's native modern API. */
public class BossBar {
    private static final float MAX_HEALTH = 300.0F;
    private static final float MIN_HEALTH = 0.5F;
    private static final long METADATA_REFRESH = 500_000_000L;
    private static final long ENTITY_REFRESH = 2_000_000_000L;
    private static final long MOVE_INTERVAL = 50_000_000L;
    private final Plugin owner;
    private final Map<UUID, LegacyState> legacy = new ConcurrentHashMap<>();
    private final Set<UUID> modernPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> retryAfter = new ConcurrentHashMap<>();
    private volatile String title;
    private volatile float progress = 1.0F;
    private volatile boolean closed;
    private long revision;
    private long rebuildSequence;
    private long lastWarning;
    private boolean warned;
    private Object modernBossBar;
    private long modernRevision = -1L;

    public BossBar(String title) { this(title, ThePit.getInstance()); }

    public BossBar(String title, Plugin owner) {
        this.title = title == null ? "" : title;
        this.owner = owner;
    }

    public void addPlayer(Player player) {
        if (!main(() -> addPlayer(player)) || closed || isBlankTitle() || !connected(player)) return;
        UUID id = player.getUniqueId();
        if (containsPlayer(id) || clock() < retryAfter.getOrDefault(id, 0L)) return;
        int version = getClientProtocolVersion(player);
        if (version <= 0) return;
        if (version > 47) { addModern(player); return; }
        try {
            EntityWither wither = createWither(player);
            LegacyState state = new LegacyState(wither, player.getWorld().getUID());
            configure(wither);
            Location target = target(player, player.getLocation());
            position(state, target);
            legacy.put(id, state);
            spawn(player, state);
            retryAfter.remove(id);
        } catch (Exception | LinkageError failure) {
            removeLegacy(id, player);
            retryAfter.put(id, clock() + 1_000_000_000L);
            warn(failure);
        }
    }

    public void removePlayer(Player player) {
        if (!main(() -> removePlayer(player))) return;
        UUID id = player.getUniqueId();
        pending.remove(id);
        retryAfter.remove(id);
        removeLegacy(id, player);
        if (modernPlayers.remove(id) && modernBossBar != null) {
            try { modern("removePlayer", UUID.class, id); }
            catch (Exception | LinkageError failure) { warn(failure); }
        }
    }

    /** A new client world discards entity ids; old teleport packets cannot recreate them. */
    public void rebuild(Player player) {
        if (!main(() -> rebuild(player)) || closed) return;
        removePlayer(player);
        if (isBlankTitle() || !connected(player)) return;
        UUID id = player.getUniqueId();
        long token = ++rebuildSequence;
        pending.put(id, token);
        later(() -> {
            if (!pending.remove(id, token) || closed || isBlankTitle()
                    || !connected(player) || findPlayer(id) != player) return;
            addPlayer(player);
        }, 2L);
    }

    public void setTitle(String title) {
        String next = title == null ? "" : title;
        if (!main(() -> setTitle(next)) || closed || this.title.equals(next)) return;
        this.title = next;
        revision++;
        if (isBlankTitle()) clearPlayers();
    }

    public void setProgress(double progress) {
        double value = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
        if (!main(() -> setProgress(value)) || closed) return;
        float next = (float) value;
        if (this.progress != next) { this.progress = next; revision++; }
    }

    public void update() {
        if (!main(this::update) || closed) return;
        if (isBlankTitle()) { clearPlayers(); return; }
        for (Map.Entry<UUID, LegacyState> entry : legacy.entrySet()) {
            Player player = findPlayer(entry.getKey());
            if (player == null || !connected(player)) { removeLegacy(entry.getKey(), player); continue; }
            update(player, player.getLocation());
        }
        for (UUID id : modernPlayers.toArray(new UUID[0])) {
            Player player = findPlayer(id);
            if (player == null || !connected(player)) {
                modernPlayers.remove(id);
                try { modern("removePlayer", UUID.class, id); }
                catch (Exception | LinkageError failure) { warn(failure); }
            }
        }
        if (modernBossBar != null && !modernPlayers.isEmpty() && modernRevision != revision) {
            try {
                modern("setTitle", String.class, title);
                modern("setHealth", float.class, progress);
                visible(true);
                modernRevision = revision;
            } catch (Exception | LinkageError failure) { warn(failure); }
        }
    }

    public void update(Player player) { update(player, player.getLocation()); }

    public void update(Player player, Location destination) {
        if (!main(() -> update(player, destination)) || closed || isBlankTitle()) return;
        LegacyState state = legacy.get(player.getUniqueId());
        if (state == null || pending.containsKey(player.getUniqueId()) || destination == null || destination.getWorld() == null) return;
        if (!state.world.equals(destination.getWorld().getUID())) { rebuild(player); return; }
        try {
            long now = clock();
            Location target = target(player, destination);
            if (now - state.lastSpawn >= ENTITY_REFRESH) {
                // Repair entities a 1.8 client discarded during chunk updates, without adding a world mob.
                configure(state.entity);
                position(state, target);
                spawn(player, state);
                return;
            }
            if (now - state.lastMove >= MOVE_INTERVAL && moved(state, target)) {
                position(state, target);
                sendPacket(player, new PacketPlayOutEntityTeleport(state.entity));
            }
            if (state.revision != revision || now - state.lastMetadata >= METADATA_REFRESH) metadata(player, state);
        } catch (Exception | LinkageError failure) {
            removeLegacy(player.getUniqueId(), player);
            retryAfter.put(player.getUniqueId(), clock() + 1_000_000_000L);
            warn(failure);
        }
    }

    private Location target(Player player, Location position) {
        return getWitherLocation(position.clone().add(0.0, player.getEyeHeight(), 0.0));
    }

    public Location getWitherLocation(Location eyeLocation) {
        return eyeLocation.clone().add(eyeLocation.getDirection().multiply(48.0));
    }

    private boolean moved(LegacyState state, Location location) {
        return state.x != MathHelper.floor(location.getX() * 32.0)
                || state.y != MathHelper.floor(location.getY() * 32.0)
                || state.z != MathHelper.floor(location.getZ() * 32.0);
    }

    private void position(LegacyState state, Location location) {
        state.x = MathHelper.floor(location.getX() * 32.0);
        state.y = MathHelper.floor(location.getY() * 32.0);
        state.z = MathHelper.floor(location.getZ() * 32.0);
        state.entity.setLocation(state.x / 32.0, state.y / 32.0, state.z / 32.0, 0.0F, 0.0F);
        state.lastMove = clock();
    }

    private void configure(EntityWither entity) {
        AttributeInstance attribute = entity.getAttributeInstance(GenericAttributes.maxHealth);
        if (attribute != null) attribute.setValue(MAX_HEALTH);
        entity.setInvisible(true);
        entity.setCustomName(title);
        entity.setCustomNameVisible(false);
        // The 1.8 renderer scales InvulTime=880 almost to zero (also used by ViaRewind).
        entity.r(880);
        entity.getDataWatcher().watch(4, (byte) 1);
        entity.getDataWatcher().watch(15, (byte) 1);
        float health = legacyHealth();
        entity.setHealth(health);
        entity.getDataWatcher().watch(6, health);
    }

    private float legacyHealth() { return Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, progress * MAX_HEALTH)); }

    private void spawn(Player player, LegacyState state) {
        if (state.lastSpawn != 0) {
            // A duplicate spawn without destroying the old entity can leave a stale client-side boss renderer.
            sendPacket(player, new PacketPlayOutEntityDestroy(state.entity.getId()));
        }
        sendPacket(player, new PacketPlayOutSpawnEntityLiving(state.entity));
        state.lastSpawn = clock();
        metadata(player, state);
    }

    private void metadata(Player player, LegacyState state) {
        configure(state.entity);
        // A complete, validated snapshot keeps title and health in the same update and never sends NaN.
        DataWatcher watcher = new DataWatcher(null);
        watcher.a(0, (byte) 0x20);
        watcher.a(2, title);
        watcher.a(3, (byte) 0);
        watcher.a(4, (byte) 1);
        watcher.a(6, legacyHealth());
        watcher.a(15, (byte) 1);
        watcher.a(20, 880);
        sendPacket(player, new PacketPlayOutEntityMetadata(state.entity.getId(), watcher, true));
        state.revision = revision;
        state.lastMetadata = clock();
    }

    private void removeLegacy(UUID id, Player player) {
        LegacyState state = legacy.remove(id);
        if (state != null && player != null) {
            try { sendPacket(player, new PacketPlayOutEntityDestroy(state.entity.getId())); }
            catch (Exception | LinkageError failure) { warn(failure); }
        }
    }

    private void addModern(Player player) {
        UUID id = player.getUniqueId();
        try {
            if (modernBossBar == null) modernBossBar = createModernBossBar(title, progress);
            modern("setTitle", String.class, title);
            modern("setHealth", float.class, progress);
            modern("addPlayer", UUID.class, id);
            visible(true);
            Object players = modernBossBar.getClass().getMethod("getPlayers").invoke(modernBossBar);
            if (!(players instanceof Set) || !((Set<?>) players).contains(id)) {
                retryAfter.put(id, clock() + 1_000_000_000L);
                return;
            }
            modernPlayers.add(id);
            retryAfter.remove(id);
            modernRevision = revision;
        } catch (Exception | LinkageError failure) {
            retryAfter.put(id, clock() + 1_000_000_000L);
            warn(failure);
        }
    }

    private void visible(boolean visible) throws ReflectiveOperationException {
        // CommonBoss#setVisible is private. Its public API is show()/hide().
        modernBossBar.getClass().getMethod(visible ? "show" : "hide").invoke(modernBossBar);
    }

    private void modern(String name, Class<?> parameter, Object value) throws ReflectiveOperationException {
        modernBossBar.getClass().getMethod(name, parameter).invoke(modernBossBar, value);
    }

    protected Object createModernBossBar(String title, float health) throws ReflectiveOperationException {
        Plugin via = Bukkit.getPluginManager().getPlugin("ViaVersion");
        ClassLoader loader = via.getClass().getClassLoader();
        Object api = Class.forName("com.viaversion.viaversion.api.Via", true, loader).getMethod("getAPI").invoke(null);
        Object legacyApi = api.getClass().getMethod("legacyAPI").invoke(api);
        Class<?> color = Class.forName("com.viaversion.viaversion.api.legacy.bossbar.BossColor", true, loader);
        Class<?> style = Class.forName("com.viaversion.viaversion.api.legacy.bossbar.BossStyle", true, loader);
        return legacyApi.getClass().getMethod("createLegacyBossBar", String.class, float.class, color, style)
                .invoke(legacyApi, title, health, color.getField("PURPLE").get(null), style.getField("SOLID").get(null));
    }

    protected int getClientProtocolVersion(Player player) {
        Plugin via = Bukkit.getPluginManager().getPlugin("ViaVersion");
        if (via == null) return 47;
        if (!via.isEnabled()) return -1;
        try {
            Object api = Class.forName("com.viaversion.viaversion.api.Via", true, via.getClass().getClassLoader())
                    .getMethod("getAPI").invoke(null);
            Object result = api.getClass().getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId());
            if (result instanceof Number) return ((Number) result).intValue();
            return ((Number) result.getClass().getMethod("getVersion").invoke(result)).intValue();
        } catch (Exception | LinkageError failure) { return -1; }
    }

    protected EntityWither createWither(Player player) {
        return new EntityWither(((CraftWorld) player.getWorld()).getHandle());
    }

    protected void sendPacket(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    protected Player findPlayer(UUID id) { return Bukkit.getPlayer(id); }
    protected boolean connected(Player player) { return player != null && player.isOnline() && !player.hasMetadata("NPC"); }
    protected long clock() { return System.nanoTime(); }
    protected void later(Runnable action, long ticks) { Bukkit.getScheduler().runTaskLater(owner, action, ticks); }

    private boolean main(Runnable action) {
        if (Bukkit.isPrimaryThread()) return true;
        if (owner != null && owner.isEnabled() && !closed) Bukkit.getScheduler().runTask(owner, action);
        return false;
    }

    private void warn(Throwable failure) {
        long now = clock();
        if (owner != null && (!warned || now - lastWarning >= 30_000_000_000L)) {
            warned = true;
            lastWarning = now;
            owner.getLogger().log(java.util.logging.Level.WARNING, "BossBar update failed", failure);
        }
    }

    private void clearPlayers() {
        pending.clear();
        retryAfter.clear();
        for (UUID id : legacy.keySet().toArray(new UUID[0])) removeLegacy(id, findPlayer(id));
        if (modernBossBar != null) {
            for (UUID id : modernPlayers.toArray(new UUID[0])) {
                try { modern("removePlayer", UUID.class, id); }
                catch (Exception | LinkageError failure) { warn(failure); }
            }
            try { visible(false); } catch (Exception | LinkageError failure) { warn(failure); }
        }
        modernPlayers.clear();
        modernRevision = -1L;
    }

    public void close() {
        if (!main(this::close)) return;
        closed = true;
        clearPlayers();
    }

    public String getTitle() { return title; }
    public float getProgress() { return progress; }
    public boolean isBlankTitle() {
        String value = title;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '§' && i + 1 < value.length()) { i++; continue; }
            if (!Character.isWhitespace(character)) return false;
        }
        return true;
    }
    public boolean containsPlayer(UUID id) { return legacy.containsKey(id) || modernPlayers.contains(id) || pending.containsKey(id); }
    public boolean hasPlayers() { return !legacy.isEmpty() || !modernPlayers.isEmpty() || !pending.isEmpty(); }
    public int getModernPlayerCount() { return modernPlayers.size(); }
    public int getPendingPlayerCount() { return pending.size(); }
    public ConcurrentHashMap<UUID, EntityWither> getWithers() {
        ConcurrentHashMap<UUID, EntityWither> result = new ConcurrentHashMap<>();
        legacy.forEach((id, state) -> result.put(id, state.entity));
        return result;
    }

    private static final class LegacyState {
        final EntityWither entity;
        final UUID world;
        int x, y, z;
        long revision = -1;
        long lastSpawn, lastMetadata, lastMove;
        LegacyState(EntityWither entity, UUID world) { this.entity = entity; this.world = world; }
    }
}
