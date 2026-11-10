package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tracks persistent entities created by events. Bukkit saves villagers in the
 * world, so an in-memory entity list alone cannot clean them after a crash.
 */
public final class EventEntityTracker {

    public static final String HAMBURGER_VILLAGER = "hamburger_villager";

    private static final String HAMBURGER_MARKER = "\u00a70\u00a70thepit:hamburger:event";
    private static final double LEGACY_LOCATION_RADIUS_SQUARED = 2.25D;
    private static final EventEntityTracker INSTANCE = new EventEntityTracker();

    private final Map<UUID, TrackedEntity> tracked = new LinkedHashMap<>();
    private File tempDir;
    private File snapshotFile;
    private File writeFile;

    private EventEntityTracker() {
    }

    public static EventEntityTracker get() {
        return INSTANCE;
    }

    /**
     * Removes crash leftovers before events are registered. The positional
     * pass is intentionally limited to the configured Hamburger NPC points so
     * villagers created by older plugin versions (without a marker) are also
     * removed without touching ordinary villagers elsewhere in the world.
     */
    public synchronized void init() {
        ensureFiles();
        loadSnapshot();

        int removed = removeJournaledEntities();
        removed += removeConfiguredHamburgerVillagers(true);
        persist();

        if (removed > 0) {
            Bukkit.getLogger().info("[ThePit] Removed " + removed + " leftover event villager(s).");
        }
    }

    public synchronized void track(String eventId, Entity entity) {
        if (eventId == null || eventId.isEmpty() || entity == null || entity.getWorld() == null) {
            return;
        }
        if (HAMBURGER_VILLAGER.equals(eventId) && entity instanceof Villager) {
            Villager villager = (Villager) entity;
            villager.setCustomName(HAMBURGER_MARKER);
            villager.setCustomNameVisible(false);
        }

        Location location = entity.getLocation();
        tracked.put(entity.getUniqueId(), new TrackedEntity(
                eventId,
                entity.getUniqueId(),
                entity.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        ));
        persist();
    }

    public synchronized void remove(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            if (!entity.isDead()) {
                entity.remove();
            }
            tracked.remove(entity.getUniqueId());
        } finally {
            persist();
        }
    }

    public synchronized void untrack(UUID entityUuid) {
        if (entityUuid != null && tracked.remove(entityUuid) != null) {
            persist();
        }
    }

    public synchronized void removeAll(String eventId) {
        Iterator<Map.Entry<UUID, TrackedEntity>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedEntity record = iterator.next().getValue();
            if (!record.eventId.equals(eventId)) {
                continue;
            }
            if (removeByRecord(record) != RemovalResult.WORLD_UNAVAILABLE) {
                iterator.remove();
            }
        }
        if (HAMBURGER_VILLAGER.equals(eventId)) {
            removeConfiguredHamburgerVillagers(false);
        }
        persist();
    }

    public synchronized void onPluginDisable() {
        removeJournaledEntities();
        removeConfiguredHamburgerVillagers(false);
        persist();
    }

    private int removeJournaledEntities() {
        int removed = 0;
        Iterator<Map.Entry<UUID, TrackedEntity>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedEntity record = iterator.next().getValue();
            RemovalResult result = removeByRecord(record);
            if (result == RemovalResult.WORLD_UNAVAILABLE) {
                continue;
            }
            if (result == RemovalResult.REMOVED) {
                removed++;
            }
            iterator.remove();
        }
        return removed;
    }

    private RemovalResult removeByRecord(TrackedEntity record) {
        World world = Bukkit.getWorld(record.worldName);
        if (world == null) {
            return RemovalResult.WORLD_UNAVAILABLE;
        }
        try {
            Chunk chunk = loadChunk(world, ((int) Math.floor(record.x)) >> 4, ((int) Math.floor(record.z)) >> 4);
            for (Entity entity : chunk.getEntities()) {
                if (record.uuid.equals(entity.getUniqueId())) {
                    entity.remove();
                    return RemovalResult.REMOVED;
                }
            }
            return RemovalResult.ALREADY_GONE;
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to remove tracked event entity " + record.uuid, exception);
            return RemovalResult.WORLD_UNAVAILABLE;
        }
    }

    private int removeConfiguredHamburgerVillagers(boolean includeLegacyLocations) {
        List<Location> configured = getHamburgerLocations();
        if (configured.isEmpty()) {
            return 0;
        }

        int removed = 0;
        Set<UUID> handled = new HashSet<>();
        Set<String> scannedChunks = new HashSet<>();
        for (Location target : configured) {
            if (target == null || target.getWorld() == null) {
                continue;
            }
            World world = target.getWorld();
            int chunkX = target.getBlockX() >> 4;
            int chunkZ = target.getBlockZ() >> 4;
            String chunkKey = world.getName() + "|" + chunkX + "|" + chunkZ;
            if (!scannedChunks.add(chunkKey)) {
                continue;
            }

            try {
                Chunk chunk = loadChunk(world, chunkX, chunkZ);
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Villager) || !handled.add(entity.getUniqueId())) {
                        continue;
                    }
                    Villager villager = (Villager) entity;
                    boolean marked = HAMBURGER_MARKER.equals(villager.getCustomName());
                    boolean legacyAtEventPoint = includeLegacyLocations && isNearConfiguredLocation(entity.getLocation(), configured);
                    if (marked || legacyAtEventPoint) {
                        entity.remove();
                        tracked.remove(entity.getUniqueId());
                        removed++;
                    }
                }
            } catch (Exception exception) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to clean Hamburger event villagers in chunk " + chunkKey, exception);
            }
        }
        return removed;
    }

    private List<Location> getHamburgerLocations() {
        List<Location> locations = new ArrayList<>();
        if (ThePit.getInstance() == null || ThePit.getInstance().getPitConfig() == null) {
            return locations;
        }
        Location offer = ThePit.getInstance().getPitConfig().getHamburgerOfferNpcLocA();
        if (offer != null) {
            locations.add(offer);
        }
        List<Location> delivery = ThePit.getInstance().getPitConfig().getHamburgerNpcLocA();
        if (delivery != null) {
            locations.addAll(delivery);
        }
        return locations;
    }

    private boolean isNearConfiguredLocation(Location entityLocation, List<Location> configured) {
        if (entityLocation == null || entityLocation.getWorld() == null) {
            return false;
        }
        for (Location target : configured) {
            if (target == null || target.getWorld() == null || !target.getWorld().equals(entityLocation.getWorld())) {
                continue;
            }
            if (target.distanceSquared(entityLocation) <= LEGACY_LOCATION_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private Chunk loadChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ, true);
        }
        return world.getChunkAt(chunkX, chunkZ);
    }

    private void loadSnapshot() {
        if (!snapshotFile.exists()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(snapshotFile.toPath(), StandardCharsets.UTF_8)) {
                String[] split = line.split("\\|", 6);
                if (split.length != 6) {
                    continue;
                }
                UUID uuid = UUID.fromString(split[1]);
                tracked.put(uuid, new TrackedEntity(
                        split[0],
                        uuid,
                        split[2],
                        Double.parseDouble(split[3]),
                        Double.parseDouble(split[4]),
                        Double.parseDouble(split[5])
                ));
            }
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to load event entity snapshot", exception);
        }
    }

    private void persist() {
        ensureFiles();
        try {
            if (tracked.isEmpty()) {
                Files.deleteIfExists(snapshotFile.toPath());
                Files.deleteIfExists(writeFile.toPath());
                return;
            }

            List<String> lines = new ArrayList<>();
            for (TrackedEntity record : tracked.values()) {
                lines.add(record.eventId + "|" + record.uuid + "|" + record.worldName + "|" +
                        record.x + "|" + record.y + "|" + record.z);
            }
            Files.write(writeFile.toPath(), lines, StandardCharsets.UTF_8);
            try {
                Files.move(writeFile.toPath(), snapshotFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(writeFile.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to persist event entity snapshot", exception);
        }
    }

    private void ensureFiles() {
        if (tempDir != null) {
            return;
        }
        tempDir = new File(ThePit.getInstance().getDataFolder(), "temp");
        snapshotFile = new File(tempDir, "event_entities.tmp");
        writeFile = new File(tempDir, "event_entities.tmp.writing");
        if (!tempDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();
        }
    }

    private enum RemovalResult {
        REMOVED,
        ALREADY_GONE,
        WORLD_UNAVAILABLE
    }

    private static final class TrackedEntity {
        private final String eventId;
        private final UUID uuid;
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;

        private TrackedEntity(String eventId, UUID uuid, String worldName, double x, double y, double z) {
            this.eventId = eventId;
            this.uuid = uuid;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
