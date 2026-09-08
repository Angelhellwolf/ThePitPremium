package cn.charlotte.pit.events;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.runnable.ClearRunnable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Persists event-generated blocks so they can be restored after an event ends
 * or after an abnormal shutdown. FAWE undo is memory-only and is lost on crash.
 */
public final class EventBlockTracker {

    public static final String CAKE = "cake";
    public static final String RAGE_PIT_WALL = "rage_pit_wall";
    public static final String SPIRE = "spire";
    public static final String SPIRE_WALL = "spire_wall";
    public static final String SPIRE_PORTAL = "spire_portal";
    public static final String HAMBURGER = "hamburger";
    public static final String KOTH = "koth";
    public static final String CARE_PACKAGE = "care_package";
    public static final String DRAGON_EGG = "dragon_egg";
    public static final String SQUADS = "squads";
    public static final String BLOCK_HEAD = "block_head";

    private static final EventBlockTracker INSTANCE = new EventBlockTracker();
    private static final int MAX_RESTORES_PER_TICK = 512;
    private static final String REGION_PREFIX = "R|";
    private static final String BLOCK_PREFIX = "B|";

    private final Map<String, RestorableRegion> regions = new LinkedHashMap<>();
    // In-flight work remains part of the crash journal until rollback finishes.
    private final Map<String, RestorableRegion> restoring = new LinkedHashMap<>();
    private final List<BukkitRunnable> restoreTasks = new ArrayList<>();
    private static final String FORMAT_VERSION = "V|2";
    private File tempDir;
    private File snapshotFile;
    private File writeFile;
    private boolean dirty;
    private int capturesSincePersist;

    private EventBlockTracker() {
    }

    public static EventBlockTracker get() {
        return INSTANCE;
    }

    public void init() {
        ensureFiles();
        loadSnapshot();
        if (!regions.isEmpty()) {
            Bukkit.getLogger().info("[ThePit] Found " + regions.size() + " leftover event block region(s); restoring after abnormal shutdown.");
            restoreAllAsync();
        }
    }

    public void captureRegion(String regionId, World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null || regionId == null || regionId.isEmpty()) {
            return;
        }
        finishPreviousRestore(regionId);
        int fromX = Math.min(minX, maxX);
        int fromY = Math.max(0, Math.min(minY, maxY));
        int fromZ = Math.min(minZ, maxZ);
        int toX = Math.max(minX, maxX);
        int toY = Math.min(world.getMaxHeight() - 1, Math.max(minY, maxY));
        int toZ = Math.max(minZ, maxZ);

        // Event snapshots must represent the arena baseline, not temporary
        // player blocks. Otherwise FAWE/event restoration can resurrect expired
        // obsidian after its cleanup record has already been consumed.
        clearTrackedPlacements(location ->
                world.equals(location.getWorld())
                        && location.getBlockX() >= fromX && location.getBlockX() <= toX
                        && location.getBlockY() >= fromY && location.getBlockY() <= toY
                        && location.getBlockZ() >= fromZ && location.getBlockZ() <= toZ
        );

        Map<Long, RestorableBlock> blocks = new LinkedHashMap<>();
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.put(pack(x, y, z), captureState(block));
                }
            }
        }
        regions.put(regionId, new RestorableRegion(world.getName(), fromX, fromY, fromZ, toX, toY, toZ, blocks));
        persist();
    }

    public void captureHollowCylinder(String regionId, Location center, int radius, int height) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        finishPreviousRestore(regionId);
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int minY = Math.max(0, cy);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + Math.max(1, height) - 1);
        // WorldEdit rounds its hollow cylinder using radius + 0.5 and adjacent
        // cells. Keep a conservative shell so no diagonal glass escapes rollback.
        int outer = (radius + 1) * (radius + 1);
        int inner = Math.max(0, radius - 2);
        int inner2 = inner * inner;

        clearTrackedPlacements(location -> {
            if (!world.equals(location.getWorld())
                    || location.getBlockY() < minY || location.getBlockY() > maxY) {
                return false;
            }
            int dx = location.getBlockX() - cx;
            int dz = location.getBlockZ() - cz;
            int dist2 = dx * dx + dz * dz;
            return dist2 <= outer && dist2 >= inner2;
        });

        Map<Long, RestorableBlock> blocks = new LinkedHashMap<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                int dx = x - cx;
                int dz = z - cz;
                int dist2 = dx * dx + dz * dz;
                if (dist2 > outer || dist2 < inner2) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.put(pack(x, y, z), captureState(block));
                }
            }
        }
        regions.put(regionId, new RestorableRegion(world.getName(), cx - radius, minY, cz - radius, cx + radius, maxY, cz + radius, blocks));
        persist();
    }

    public void captureBlock(String regionId, Location location) {
        captureBlock(regionId, location, true);
    }

    public void captureBlock(String regionId, Location location, boolean persistNow) {
        if (location == null || location.getWorld() == null || regionId == null) {
            return;
        }
        finishPreviousRestore(regionId);
        clearTrackedPlacements(candidate -> sameBlock(candidate, location));
        Block block = location.getBlock();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        long key = pack(x, y, z);
        RestorableRegion existing = regions.get(regionId);
        if (existing != null && !existing.worldName.equals(location.getWorld().getName())) {
            throw new IllegalArgumentException("An event block region cannot span multiple worlds: " + regionId);
        }
        Map<Long, RestorableBlock> blocks = existing == null ? new LinkedHashMap<>() : existing.blocks;
        if (blocks.containsKey(key)) {
            return;
        }
        blocks.put(key, captureState(block));
        if (existing == null) {
            regions.put(regionId, new RestorableRegion(location.getWorld().getName(), x, y, z, x, y, z, blocks));
        }
        if (persistNow) {
            persist();
        } else {
            dirty = true;
        }
    }

    public void captureBlocks(String regionId, Iterable<Location> locations) {
        if (locations == null || regionId == null) {
            return;
        }
        finishPreviousRestore(regionId);
        ensureFiles();
        boolean needsFullSnapshot = dirty || !snapshotFile.exists();
        boolean newRegion = !regions.containsKey(regionId);
        List<String> delta = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) continue;
            long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            RestorableRegion region = regions.get(regionId);
            if (region != null && region.blocks.containsKey(key)) continue;
            captureBlock(regionId, location, false);
            RestorableBlock block = regions.get(regionId).blocks.get(key);
            delta.add(BLOCK_PREFIX + regionId + "|" + location.getBlockX() + "|" + location.getBlockY()
                    + "|" + location.getBlockZ() + "|" + block.type.name() + "|" + block.data
                    + (block.tileData == null ? "" : "|" + block.tileData));
        }
        if (needsFullSnapshot) {
            persist();
        } else if (!delta.isEmpty()) {
            if (newRegion) delta.add(0, serializeRegion(regionId, regions.get(regionId)));
            try {
                // Block Head grows to tens of thousands of blocks. Append only
                // fresh baselines instead of rewriting the entire journal per kill.
                Files.write(snapshotFile.toPath(), delta, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                dirty = false;
            } catch (Exception failure) {
                persist();
                if (dirty) throw new IllegalStateException("Failed to journal event blocks", failure);
            }
        }
    }

    public void restore(String regionId) {
        RestorableRegion region = regions.remove(regionId);
        if (region != null) {
            Map<String, RestorableRegion> work = Collections.singletonMap(regionId, region);
            restoring.putAll(work);
            persist();
            restoreAsync(work);
        }
    }

    /**
     * Restores a small event region before removing its crash journal. This is
     * used by Hamburger's 72-block schematic so an immediate process stop
     * cannot land between deleting the snapshot and applying the rollback.
     */
    public void restoreImmediately(String regionId) {
        finishPreviousRestore(regionId);
        RestorableRegion region = regions.remove(regionId);
        if (region == null) {
            return;
        }
        restoreSync(Collections.singletonMap(regionId, region));
        persist();
    }

    private void finishPreviousRestore(String regionId) {
        RestorableRegion previous = restoring.remove(regionId);
        if (previous != null) {
            restoreSync(Collections.singletonMap(regionId, previous));
            persist();
            if (regions.containsKey(regionId)) {
                throw new IllegalStateException("Cannot replace an event region whose rollback failed: " + regionId);
            }
        }
    }

    public void restoreAll() {
        restoreAllAsync();
    }

    public void finishRestorations() {
        List<String> ids = new ArrayList<>(restoring.keySet());
        Collections.reverse(ids);
        for (String id : ids) finishPreviousRestore(id);
    }

    public boolean isTracked(Location location) {
        if (location == null || location.getWorld() == null) return false;
        long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        for (RestorableRegion region : regions.values()) {
            if (region.worldName.equals(location.getWorld().getName()) && region.blocks.containsKey(key)) return true;
        }
        for (RestorableRegion region : restoring.values()) {
            if (region.worldName.equals(location.getWorld().getName()) && region.blocks.containsKey(key)) return true;
        }
        return false;
    }

    public void onPluginDisable() {
        for (BukkitRunnable task : new ArrayList<>(restoreTasks)) {
            task.cancel();
        }
        restoreTasks.clear();
        Map<String, RestorableRegion> leftover = new LinkedHashMap<>(regions);
        leftover.putAll(restoring);
        restoring.clear();
        regions.clear();
        if (!leftover.isEmpty()) {
            restoreSync(leftover);
        }
        persistNow();
        if (snapshotFile != null && snapshotFile.exists() && regions.isEmpty()) {
            try {
                Files.deleteIfExists(snapshotFile.toPath());
                if (writeFile != null) {
                    Files.deleteIfExists(writeFile.toPath());
                }
            } catch (Exception exception) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to clean event block snapshot files", exception);
            }
        }
    }

    private void restoreAllAsync() {
        if (regions.isEmpty()) {
            return;
        }
        Map<String, RestorableRegion> leftover = new LinkedHashMap<>(regions);
        regions.clear();
        restoring.putAll(leftover);
        persist();
        restoreAsync(leftover);
    }

    private void restoreAsync(Map<String, RestorableRegion> leftover) {
        if (leftover.isEmpty()) {
            return;
        }
        if (ThePit.getInstance() != null && !ThePit.getInstance().isEnabled()) {
            restoreSync(leftover);
            leftover.forEach((id, region) -> restoring.remove(id, region));
            persist();
            return;
        }
        Runnable start = () -> startRestoreTask(leftover);
        if (Bukkit.isPrimaryThread()) {
            start.run();
        } else {
            Bukkit.getScheduler().runTask(ThePit.getInstance(), start);
        }
    }

    private void startRestoreTask(Map<String, RestorableRegion> leftover) {
        BukkitRunnable task = new BukkitRunnable() {
            private final List<RestoreWork> queue = flatten(leftover);
            private int restored;

            @Override
            public void run() {
                int processed = 0;
                Iterator<RestoreWork> iterator = queue.iterator();
                while (iterator.hasNext() && processed < MAX_RESTORES_PER_TICK) {
                    RestoreWork work = iterator.next();
                    iterator.remove();
                    if (restoring.get(work.regionId) != leftover.get(work.regionId)) {
                        continue;
                    }
                    if (restoreBlock(work)) {
                        restored++;
                    }
                    processed++;
                }
                if (queue.isEmpty()) {
                    orderFailedRestores(leftover);
                    leftover.forEach((id, region) -> restoring.remove(id, region));
                    if (restored > 0) {
                        Bukkit.getLogger().info("[ThePit] Restored " + restored + " leftover event blocks.");
                    }
                    persist();
                    restoreTasks.remove(this);
                    cancel();
                }
            }
        };
        restoreTasks.add(task);
        task.runTaskTimer(ThePit.getInstance(), 1L, 1L);
    }

    private void restoreSync(Map<String, RestorableRegion> leftover) {
        int restored = 0;
        for (RestoreWork work : flatten(leftover)) {
            if (restoreBlock(work)) {
                restored++;
            }
        }
        orderFailedRestores(leftover);
        if (restored > 0) {
            Bukkit.getLogger().info("[ThePit] Restored " + restored + " leftover event blocks on shutdown.");
        }
    }

    private void orderFailedRestores(Map<String, RestorableRegion> originalOrder) {
        Map<String, RestorableRegion> ordered = new LinkedHashMap<>();
        for (String id : originalOrder.keySet()) {
            if (regions.containsKey(id)) ordered.put(id, regions.get(id));
        }
        ordered.putAll(regions);
        regions.clear();
        regions.putAll(ordered);
    }

    private boolean restoreBlock(RestoreWork work) {
        try {
            World world = Bukkit.getWorld(work.worldName);
            if (world == null) {
                requeue(work);
                return false;
            }
            int chunkX = work.x >> 4;
            int chunkZ = work.z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) && !world.loadChunk(chunkX, chunkZ, true)) {
                requeue(work);
                return false;
            }
            Block block = world.getBlockAt(work.x, work.y, work.z);
            if (block.getType() != work.type || block.getData() != work.data) {
                block.setType(work.type, false);
                if (work.data != 0 || block.getData() != work.data) {
                    block.setData(work.data, false);
                }
                restoreTile(block, work.tileData);
                return true;
            }
            restoreTile(block, work.tileData);
            return false;
        } catch (Exception exception) {
            requeue(work);
            Bukkit.getLogger().log(Level.WARNING, "Failed to restore event block " + work.regionId
                    + " at " + work.worldName + ":" + work.x + "," + work.y + "," + work.z, exception);
            return false;
        }
    }

    private void requeue(RestoreWork work) {
        RestorableRegion region = regions.computeIfAbsent(work.regionId, id ->
                new RestorableRegion(work.worldName, work.x, work.y, work.z, work.x, work.y, work.z, new LinkedHashMap<>()));
        region.blocks.put(pack(work.x, work.y, work.z), new RestorableBlock(work.type, work.data, work.tileData));
        dirty = true;
    }

    private List<RestoreWork> flatten(Map<String, RestorableRegion> leftover) {
        List<RestoreWork> work = new ArrayList<>();
        List<Map.Entry<String, RestorableRegion>> ordered = new ArrayList<>(leftover.entrySet());
        Collections.reverse(ordered);
        for (Map.Entry<String, RestorableRegion> regionEntry : ordered) {
            RestorableRegion region = regionEntry.getValue();
            for (Map.Entry<Long, RestorableBlock> entry : region.blocks.entrySet()) {
                int[] xyz = unpack(entry.getKey());
                work.add(new RestoreWork(regionEntry.getKey(), region.worldName, xyz[0], xyz[1], xyz[2], entry.getValue().type, entry.getValue().data, entry.getValue().tileData));
            }
        }
        return work;
    }

    private void persist() {
        dirty = true;
        persistNow();
    }

    private void persistNow() {
        ensureFiles();
        if (!dirty && regions.isEmpty() && (snapshotFile == null || !snapshotFile.exists())) {
            return;
        }
        List<String> lines = serialize();
        try {
            Files.write(writeFile.toPath(), lines, StandardCharsets.UTF_8);
            try {
                Files.move(writeFile.toPath(), snapshotFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(writeFile.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            capturesSincePersist = 0;
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to persist event block snapshot", exception);
        }
    }

    private List<String> serialize() {
        List<String> lines = new ArrayList<>();
        lines.add(FORMAT_VERSION);
        Map<String, RestorableRegion> journal = new LinkedHashMap<>(regions);
        journal.putAll(restoring);
        for (Map.Entry<String, RestorableRegion> entry : journal.entrySet()) {
            RestorableRegion region = entry.getValue();
            lines.add(serializeRegion(entry.getKey(), region));
            for (Map.Entry<Long, RestorableBlock> blockEntry : region.blocks.entrySet()) {
                int[] xyz = unpack(blockEntry.getKey());
                RestorableBlock block = blockEntry.getValue();
                lines.add(BLOCK_PREFIX + entry.getKey() + "|" + xyz[0] + "|" + xyz[1] + "|" + xyz[2] + "|" +
                        block.type.name() + "|" + block.data + (block.tileData == null ? "" : "|" + block.tileData));
            }
        }
        return lines;
    }

    private String serializeRegion(String id, RestorableRegion region) {
        return REGION_PREFIX + id + "|" + region.worldName + "|" + region.minX + "|" + region.minY
                + "|" + region.minZ + "|" + region.maxX + "|" + region.maxY + "|" + region.maxZ;
    }

    private void loadSnapshot() {
        ensureFiles();
        if (!snapshotFile.exists()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(snapshotFile.toPath(), StandardCharsets.UTF_8);
            boolean legacyCoordinates = !lines.contains(FORMAT_VERSION);
            for (String line : lines) {
                try {
                if (line.startsWith(REGION_PREFIX)) {
                    String[] split = line.substring(REGION_PREFIX.length()).split("\\|", 8);
                    if (split.length < 8) {
                        continue;
                    }
                    regions.put(split[0], new RestorableRegion(
                            split[1],
                            Integer.parseInt(split[2]),
                            Integer.parseInt(split[3]),
                            Integer.parseInt(split[4]),
                            Integer.parseInt(split[5]),
                            Integer.parseInt(split[6]),
                            Integer.parseInt(split[7]),
                            new LinkedHashMap<>()
                    ));
                } else if (line.startsWith(BLOCK_PREFIX)) {
                    String[] split = line.substring(BLOCK_PREFIX.length()).split("\\|", 7);
                    if (split.length < 6) {
                        continue;
                    }
                    RestorableRegion region = regions.get(split[0]);
                    if (region == null) {
                        continue;
                    }
                    Material type = Material.matchMaterial(split[4]);
                    if (type == null) {
                        continue;
                    }
                    int x = Integer.parseInt(split[1]);
                    int y = Integer.parseInt(split[2]);
                    int z = Integer.parseInt(split[3]);
                    // The old signed z shift inverted x when serializing z < 0.
                    if (legacyCoordinates && z < 0) {
                        x = ~x;
                    }
                    region.blocks.put(pack(x, y, z), new RestorableBlock(type, Byte.parseByte(split[5]), split.length == 7 ? split[6] : null));
                }
                } catch (IllegalArgumentException malformedLine) {
                    Bukkit.getLogger().warning("[ThePit] Ignoring a malformed event block journal line; continuing recovery.");
                }
            }
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to load event block snapshot", exception);
        }
    }

    private void ensureFiles() {
        if (tempDir != null) {
            return;
        }
        this.tempDir = new File(ThePit.getInstance().getDataFolder(), "temp");
        this.snapshotFile = new File(tempDir, "event_blocks.tmp");
        this.writeFile = new File(tempDir, "event_blocks.tmp.writing");
        if (!tempDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();
        }
    }

    private void clearTrackedPlacements(Predicate<Location> filter) {
        ClearRunnable clearRunnable = ClearRunnable.getClearRunnable();
        if (clearRunnable == null) {
            return;
        }
        clearRunnable.clearPlacedBlocks(data -> {
            Location location = data == null ? null : data.getLocation();
            return location != null && location.getWorld() != null && filter.test(location);
        });
    }

    private boolean sameBlock(Location left, Location right) {
        return left != null && right != null
                && left.getWorld() != null && left.getWorld().equals(right.getWorld())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static int[] unpack(long packed) {
        int y = (int) (packed & 0xFFFL);
        int z = (int) (packed << 26 >> 38);
        int x = (int) (packed >> 38);
        return new int[]{x, y, z};
    }

    private static final class RestorableRegion {
        private final String worldName;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final Map<Long, RestorableBlock> blocks;

        private RestorableRegion(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Map<Long, RestorableBlock> blocks) {
            this.worldName = worldName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blocks = blocks;
        }
    }

    private static final class RestorableBlock {
        private final Material type;
        private final byte data;
        private final String tileData;

        private RestorableBlock(Material type, byte data) {
            this(type, data, null);
        }

        private RestorableBlock(Material type, byte data, String tileData) {
            this.type = type == null ? Material.AIR : type;
            this.data = data;
            this.tileData = tileData;
        }
    }

    private static final class RestoreWork {
        private final String regionId;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final Material type;
        private final byte data;
        private final String tileData;

        private RestoreWork(String regionId, String worldName, int x, int y, int z, Material type, byte data, String tileData) {
            this.regionId = regionId;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.data = data;
            this.tileData = tileData;
        }
    }

    private RestorableBlock captureState(Block block) {
        String tileData = null;
        TileEntity tile = tileAt(block);
        if (tile != null) {
            try {
                NBTTagCompound tag = new NBTTagCompound();
                tile.b(tag);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NBTCompressedStreamTools.a(tag, bytes);
                tileData = Base64.getEncoder().encodeToString(bytes.toByteArray());
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot snapshot block entity at " + block.getLocation(), failure);
            }
        }
        return new RestorableBlock(block.getType(), block.getData(), tileData);
    }

    TileEntity tileAt(Block block) {
        if (!(block.getWorld() instanceof CraftWorld)) return null;
        return ((CraftWorld) block.getWorld()).getHandle()
                .getTileEntity(new BlockPosition(block.getX(), block.getY(), block.getZ()));
    }

    private void restoreTile(Block block, String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return;
        TileEntity tile = tileAt(block);
        if (tile == null) throw new IllegalStateException("Block entity was not restored at " + block.getLocation());
        NBTTagCompound tag = NBTCompressedStreamTools.a(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
        tag.setInt("x", block.getX());
        tag.setInt("y", block.getY());
        tag.setInt("z", block.getZ());
        tile.a(tag);
        tile.update();
    }
}
