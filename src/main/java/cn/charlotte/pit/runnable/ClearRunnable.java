package cn.charlotte.pit.runnable;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.sub.DroppedEntityData;
import cn.charlotte.pit.data.sub.PlacedBlockData;
import cn.charlotte.pit.events.EventBlockTracker;
import cn.charlotte.pit.util.cooldown.Cooldown;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 22:12
 */
@Getter
public class ClearRunnable extends BukkitRunnable {

    private static ClearRunnable clearRunnable;
    public final List<PlacedBlockData> placedBlock;
    private final List<DroppedEntityData> entityData;
    private final List<String> pendingRecoveryLines;
    private final File tempDir;
    private final File placedBlockTempFile;
    private final File placedBlockWriteFile;
    private final File crashLockFile;
    private final ExecutorService persistenceExecutor;
    private final AtomicBoolean persistenceDirty = new AtomicBoolean();
    private final Object persistenceLock = new Object();
    private List<String> pendingPersistenceSnapshot;
    private boolean persistenceWriterScheduled;
    private boolean persistenceStopping;
    private int recoveryTickCounter = 0;
    private static final int MAX_CLEARS_PER_TICK = 512;
    private static final long CLEANUP_ERROR_LOG_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private long nextCleanupErrorLogAt;

    public ClearRunnable() {
        clearRunnable = this;
        this.placedBlock = new ArrayList<>();
        this.entityData = new ArrayList<>();
        this.pendingRecoveryLines = new ArrayList<>();
        this.tempDir = new File(ThePit.getInstance().getDataFolder(), "temp");
        this.placedBlockTempFile = new File(tempDir, "placed_blocks.tmp");
        this.placedBlockWriteFile = new File(tempDir, "placed_blocks.tmp.writing");
        this.crashLockFile = new File(tempDir, "placed_blocks.lock");
        this.persistenceExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ThePit-placed-block-persistence");
            thread.setDaemon(true);
            return thread;
        });
        this.initPersistence();
        flushDirtyPlacedBlocks();
    }

    public static ClearRunnable getClearRunnable() {
        return clearRunnable;
    }

    @Override
    public void run() {
        try {
            processPlacedBlocks();
        } catch (Exception exception) {
            logCleanupError("Failed to process expiring placed blocks", exception);
        }

        try {
            processDroppedEntities();
        } catch (Exception exception) {
            logCleanupError("Failed to process expiring dropped entities", exception);
        }

        try {
            recoveryTickCounter++;
            if (recoveryTickCounter >= 20 && !pendingRecoveryLines.isEmpty()) {
                recoveryTickCounter = 0;
                cleanupRecoveredBlocks(new ArrayList<>(pendingRecoveryLines));
            }
        } catch (Exception exception) {
            logCleanupError("Failed to retry recovered placed blocks", exception);
        }

        try {
            flushDirtyPlacedBlocks();
        } catch (Exception exception) {
            persistenceDirty.set(true);
            logCleanupError("Failed to snapshot placed blocks", exception);
        }
    }

    private void processPlacedBlocks() {
        int clearedThisTick = 0;
        boolean removedAny = false;
        Iterator<PlacedBlockData> iterator = this.placedBlock.iterator();
        while (iterator.hasNext()) {
            PlacedBlockData data = iterator.next();
            if (data == null || data.getCooldown() == null) {
                iterator.remove();
                removedAny = true;
                continue;
            }
            if (!data.getCooldown().hasExpired()) {
                continue;
            }
            if (clearedThisTick >= MAX_CLEARS_PER_TICK) {
                break;
            }
            try {
                clearedThisTick++;
                // Do not discard the record while an event or schematic has
                // temporarily replaced the player's block. FAWE undo can restore
                // that obsidian later; retaining the record lets the next pass
                // remove it instead of leaving an orphan in the arena.
                if (clearPlacedBlock(data)) {
                    iterator.remove();
                    removedAny = true;
                }
            } catch (Exception exception) {
                logCleanupError("Failed to clear a tracked placed block", exception);
            }
        }
        if (removedAny) {
            markPersistenceDirty();
        }
    }

    private void processDroppedEntities() {
        Iterator<DroppedEntityData> iterator = entityData.iterator();
        while (iterator.hasNext()) {
            DroppedEntityData data = iterator.next();
            if (data == null || data.getTimer() == null) {
                iterator.remove();
                continue;
            }
            if (!data.getTimer().hasExpired()) {
                continue;
            }
            try {
                if (data.getEntity() != null) {
                    data.getEntity().remove();
                }
                iterator.remove();
            } catch (Exception exception) {
                logCleanupError("Failed to remove an expired dropped entity", exception);
            }
        }
    }

    public void placeBlock(Location location) {
        this.placeBlock(location, new Cooldown(360, TimeUnit.SECONDS));
    }

    public void placeBlock(Location location, Cooldown cooldown) {
        Block block = location.getBlock();
        this.placeBlock(location, cooldown, block.getType(), block.getData());
    }

    public void placeBlock(Location location, Cooldown cooldown, Material type, byte data) {
        Location blockLocation = location.getBlock().getLocation();
        this.placedBlock.removeIf(existing -> isSameBlock(existing.getLocation(), blockLocation));
        this.placedBlock.add(new PlacedBlockData(blockLocation, cooldown, type, data));
        markPersistenceDirty();
    }

    private boolean isSameBlock(Location left, Location right) {
        if (left == null || right == null || left.getWorld() == null || right.getWorld() == null) {
            return false;
        }
        return left.getWorld().equals(right.getWorld())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockY() == right.getBlockY()
                && left.getBlockZ() == right.getBlockZ();
    }

    public boolean isTrackedPlacement(PlacedBlockData data) {
        if (data == null || data.getLocation() == null) {
            return false;
        }
        if (EventBlockTracker.get().isTracked(data.getLocation())) return false;
        Block block = data.getLocation().getBlock();
        Material current = block.getType();
        if (current == Material.AIR) {
            return false;
        }
        if (data.getPlacedType() == null) {
            return isClearablePlayerBlock(current);
        }
        return matchesPlacement(block, data.getPlacedType(), data.getPlacedData());
    }

    public PlacedBlockData getTrackedPlacement(Location location) {
        for (PlacedBlockData data : placedBlock) {
            if (data != null && isSameBlock(data.getLocation(), location)) return data;
        }
        return null;
    }

    private boolean matchesPlacement(Block block, Material type, byte data) {
        Material current = block.getType();
        if (isWater(type)) return isWater(current);
        if (isLava(type)) return isLava(current);
        return current == type && block.getData() == data;
    }

    private static boolean isWater(Material type) {
        return type == Material.WATER || type == Material.STATIONARY_WATER;
    }

    private static boolean isLava(Material type) {
        return type == Material.LAVA || type == Material.STATIONARY_LAVA;
    }

    public void clearPlacedBlocks(Predicate<PlacedBlockData> filter) {
        List<PlacedBlockData> snapshot = new ArrayList<>(this.placedBlock);
        List<PlacedBlockData> removed = new ArrayList<>();
        for (PlacedBlockData data : snapshot) {
            if (!filter.test(data)) {
                continue;
            }
            try {
                if (clearPlacedBlock(data)) {
                    removed.add(data);
                }
            } catch (Exception exception) {
                logCleanupError("Failed to clear a selected placed block", exception);
            }
        }
        if (!removed.isEmpty()) {
            this.placedBlock.removeAll(removed);
            markPersistenceDirty();
        }
    }

    public void clearAllPlacedBlocks() {
        clearPlacedBlocks(data -> true);
    }

    public void removePlacedBlock(PlacedBlockData data) {
        if (this.placedBlock.remove(data)) {
            markPersistenceDirty();
        }
    }

    public void onPluginDisable() {
        // A normal plugin reload used to delete the recovery file while leaving
        // every still-live player block in the saved world. Clear what is
        // currently visible and retain any temporarily-overwritten entries for
        // startup recovery.
        clearAllPlacedBlocks();
        if (!pendingRecoveryLines.isEmpty()) {
            cleanupRecoveredBlocks(new ArrayList<>(pendingRecoveryLines));
        }

        List<String> finalSnapshot = createPersistenceSnapshot();
        Future<Boolean> finalWrite;
        synchronized (persistenceLock) {
            if (persistenceStopping) {
                return;
            }
            persistenceStopping = true;
            pendingPersistenceSnapshot = null;
            try {
                finalWrite = persistenceExecutor.submit(() -> writePersistenceSnapshot(finalSnapshot));
            } catch (RejectedExecutionException exception) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to queue final placed block persistence", exception);
                return;
            }
        }

        persistenceExecutor.shutdown();
        boolean finalWriteSucceeded = false;
        try {
            finalWriteSucceeded = finalWrite.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to finish final placed block persistence", exception);
        }

        boolean writerStopped = awaitPersistenceTermination();
        if (!finalWriteSucceeded || !writerStopped) {
            Bukkit.getLogger().warning("Placed block persistence did not shut down cleanly; keeping recovery files for the next startup.");
            return;
        }

        if (!finalSnapshot.isEmpty()) {
            Bukkit.getLogger().info("[ThePit] Kept " + finalSnapshot.size()
                    + " placed block recovery entr" + (finalSnapshot.size() == 1 ? "y" : "ies")
                    + " for the next startup.");
            return;
        }

        try {
            if (crashLockFile.exists()) {
                Files.deleteIfExists(crashLockFile.toPath());
            }
            if (placedBlockTempFile.exists()) {
                Files.deleteIfExists(placedBlockTempFile.toPath());
            }
            Files.deleteIfExists(placedBlockWriteFile.toPath());
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to clean placed block temp files", exception);
        }
    }

    private void initPersistence() {
        ensureTempDir();

        if (crashLockFile.exists()) {
            final List<String> staleLines = loadPersistedLines();
            if (!staleLines.isEmpty()) {
                cleanupRecoveredBlocks(staleLines);
            }
        }

        try {
            if (!crashLockFile.exists()) {
                Files.createFile(crashLockFile.toPath());
            }
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to create crash lock file for placed blocks", exception);
        }
    }

    private void cleanupRecoveredBlocks(List<String> staleLines) {
        pendingRecoveryLines.clear();
        int cleaned = 0;
        int processed = 0;
        for (int i = 0; i < staleLines.size(); i++) {
            if (processed >= MAX_CLEARS_PER_TICK) {
                pendingRecoveryLines.addAll(staleLines.subList(i, staleLines.size()));
                break;
            }
            String line = staleLines.get(i);
            RecoveryEntry entry = deserializeRecoveryEntry(line);
            if (entry == null) {
                continue;
            }
            World world = Bukkit.getWorld(entry.worldName);
            if (world == null) {
                pendingRecoveryLines.add(line);
                continue;
            }
            Location location = new Location(world, entry.x, entry.y, entry.z);

            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }

            processed++;
            Block block = location.getBlock();
            Material before = block.getType();
            if (clearRecoveredBlock(block, entry.placedType, entry.placedData)) {
                if (before != Material.AIR && block.getType() == Material.AIR) {
                    cleaned++;
                }
            } else {
                // An event block may currently cover this coordinate. Keep the
                // recovery entry until the event restoration exposes the original
                // player-placed material again.
                pendingRecoveryLines.add(line);
            }
        }

        markPersistenceDirty();

        if (cleaned > 0) {
            Bukkit.getLogger().info("[ThePit] Recovered and cleaned " + cleaned + " stale placed blocks after abnormal shutdown.");
        }
    }

    private void markPersistenceDirty() {
        persistenceDirty.set(true);
    }

    private void flushDirtyPlacedBlocks() {
        if (!persistenceDirty.compareAndSet(true, false)) {
            return;
        }

        queuePersistenceSnapshot(createPersistenceSnapshot());
    }

    private List<String> createPersistenceSnapshot() {
        List<String> lines = new ArrayList<>(pendingRecoveryLines);
        for (PlacedBlockData data : this.placedBlock) {
            Location location = data.getLocation();
            if (location.getWorld() == null) {
                continue;
            }
            lines.add(serializeLocation(data));
        }
        return Collections.unmodifiableList(lines);
    }

    private void queuePersistenceSnapshot(List<String> snapshot) {
        synchronized (persistenceLock) {
            if (persistenceStopping) {
                persistenceDirty.set(true);
                return;
            }

            // Keep only the newest not-yet-written snapshot. It represents the full
            // state, so superseded snapshots never need to queue behind slow disk I/O.
            pendingPersistenceSnapshot = snapshot;
            if (persistenceWriterScheduled) {
                return;
            }

            persistenceWriterScheduled = true;
            try {
                persistenceExecutor.execute(this::drainPersistenceSnapshots);
            } catch (RejectedExecutionException exception) {
                persistenceWriterScheduled = false;
                persistenceDirty.set(true);
                Bukkit.getLogger().log(Level.WARNING, "Failed to queue placed block persistence", exception);
            }
        }
    }

    private void drainPersistenceSnapshots() {
        while (true) {
            List<String> snapshot;
            synchronized (persistenceLock) {
                snapshot = pendingPersistenceSnapshot;
                pendingPersistenceSnapshot = null;
                if (snapshot == null) {
                    persistenceWriterScheduled = false;
                    return;
                }
            }

            if (!writePersistenceSnapshot(snapshot)) {
                persistenceDirty.set(true);
            }
        }
    }

    private boolean writePersistenceSnapshot(List<String> lines) {
        ensureTempDir();
        try {
            Files.write(placedBlockWriteFile.toPath(), lines, StandardCharsets.UTF_8);
            try {
                Files.move(
                        placedBlockWriteFile.toPath(),
                        placedBlockTempFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        placedBlockWriteFile.toPath(),
                        placedBlockTempFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            return true;
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to persist placed block temp file", exception);
            return false;
        }
    }

    private boolean awaitPersistenceTermination() {
        try {
            if (persistenceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }
            persistenceExecutor.shutdownNow();
            return persistenceExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            persistenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<String> loadPersistedLines() {
        if (!placedBlockTempFile.exists()) {
            return Collections.emptyList();
        }

        try {
            return Files.readAllLines(placedBlockTempFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to load placed block temp file", exception);
        }
        return Collections.emptyList();
    }

    private String serializeLocation(PlacedBlockData data) {
        Location location = data.getLocation();
        Material type = data.getPlacedType();
        if (type == null) {
            return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        }
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + "," + type.name() + "," + data.getPlacedData();
    }

    private RecoveryEntry deserializeRecoveryEntry(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] split = raw.split(",", 6);
        if (split.length < 4) {
            return null;
        }
        try {
            int x = Integer.parseInt(split[1]);
            int y = Integer.parseInt(split[2]);
            int z = Integer.parseInt(split[3]);
            Material placedType = null;
            byte placedData = 0;
            if (split.length >= 5) {
                placedType = Material.matchMaterial(split[4]);
                if (placedType == null) {
                    return null;
                }
            }
            if (split.length >= 6) {
                placedData = Byte.parseByte(split[5]);
            }
            return new RecoveryEntry(split[0], x, y, z, placedType, placedData);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean clearPlacedBlock(PlacedBlockData data) {
        if (data == null || data.getLocation() == null || data.getLocation().getWorld() == null) {
            return true;
        }
        Block block = data.getLocation().getBlock();
        if (EventBlockTracker.get().isTracked(data.getLocation())) return false;
        Material current = block.getType();
        if (current == Material.AIR) {
            return true;
        }
        if (data.getPlacedType() == null) {
            if (!isClearablePlayerBlock(current)) {
                return true;
            }
        } else if (!matchesPlacement(block, data.getPlacedType(), data.getPlacedData())) {
            return false;
        }
        setAir(block);
        return block.getType() == Material.AIR;
    }

    private boolean clearRecoveredBlock(Block block, Material placedType, byte placedData) {
        if (EventBlockTracker.get().isTracked(block.getLocation())) return false;
        Material current = block.getType();
        if (current == Material.AIR) {
            return true;
        }
        if (placedType == null) {
            if (!isClearablePlayerBlock(current)) {
                return true;
            }
        } else if (!matchesPlacement(block, placedType, placedData)) {
            return false;
        }
        setAir(block);
        return block.getType() == Material.AIR;
    }

    private boolean isClearablePlayerBlock(Material type) {
        return type == Material.OBSIDIAN
                || type == Material.STONE
                || type == Material.COBBLESTONE
                || type == Material.WOOD
                || type == Material.LOG
                || type == Material.WOOL
                || type == Material.GLASS
                || type == Material.SANDSTONE
                || type == Material.STEP
                || type == Material.WOOD_STEP;
    }

    private void setAir(Block block) {
        block.setType(Material.AIR, false);
    }

    private void logCleanupError(String message, Exception exception) {
        long now = System.currentTimeMillis();
        if (now < nextCleanupErrorLogAt) {
            return;
        }
        nextCleanupErrorLogAt = now + CLEANUP_ERROR_LOG_INTERVAL_MILLIS;
        Bukkit.getLogger().log(Level.WARNING, "[ThePit] " + message
                + "; the cleanup task will keep running and retry.", exception);
    }

    private void ensureTempDir() {
        if (!tempDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempDir.mkdirs();
        }
    }

    private static final class RecoveryEntry {
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final Material placedType;
        private final byte placedData;

        private RecoveryEntry(String worldName, int x, int y, int z, Material placedType, byte placedData) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.placedType = placedType;
            this.placedData = placedData;
        }
    }
}
