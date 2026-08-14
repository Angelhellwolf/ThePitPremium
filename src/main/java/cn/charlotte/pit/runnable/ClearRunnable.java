package cn.charlotte.pit.runnable;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.sub.DroppedEntityData;
import cn.charlotte.pit.data.sub.PlacedBlockData;
import cn.charlotte.pit.util.cooldown.Cooldown;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    private final File crashLockFile;
    private int recoveryTickCounter = 0;

    public ClearRunnable() {
        clearRunnable = this;
        this.placedBlock = new ArrayList<>();
        this.entityData = new ArrayList<>();
        this.pendingRecoveryLines = new ArrayList<>();
        this.tempDir = new File(ThePit.getInstance().getDataFolder(), "temp");
        this.placedBlockTempFile = new File(tempDir, "placed_blocks.tmp");
        this.crashLockFile = new File(tempDir, "placed_blocks.lock");
        this.initPersistence();
    }

    public static ClearRunnable getClearRunnable() {
        return clearRunnable;
    }

    @Override
    public void run() {
        List<PlacedBlockData> shouldRemove = new ArrayList<>();

        for (PlacedBlockData data : this.placedBlock) {
            if (data.getCooldown().hasExpired()) {
                clearIfTrackedPlacement(data);
                shouldRemove.add(data);
            }
        }

        this.placedBlock.removeAll(shouldRemove);
        if (!shouldRemove.isEmpty()) {
            persistPlacedBlocks();
        }


        List<DroppedEntityData> shouldRemoveEntity = new ArrayList<>();
        for (DroppedEntityData data : entityData) {
            if (data.getTimer().hasExpired()) {
                data.getEntity().remove();
                shouldRemoveEntity.add(data);
            }
        }

        entityData.removeAll(shouldRemoveEntity);

        recoveryTickCounter++;
        if (recoveryTickCounter >= 20 && !pendingRecoveryLines.isEmpty()) {
            recoveryTickCounter = 0;
            cleanupRecoveredBlocks(new ArrayList<>(pendingRecoveryLines));
        }
    }

    public void placeBlock(Location location) {
        this.placeBlock(location, new Cooldown(360, TimeUnit.SECONDS));
    }

    public void placeBlock(Location location, Cooldown cooldown) {
        this.placedBlock.add(new PlacedBlockData(location, cooldown, location.getBlock().getType(), location.getBlock().getData()));
        persistPlacedBlocks();
    }

    public boolean isTrackedPlacement(PlacedBlockData data) {
        if (data == null || data.getPlacedType() == null) {
            return false;
        }
        Location location = data.getLocation();
        return location.getBlock().getType() == data.getPlacedType() && location.getBlock().getData() == data.getPlacedData();
    }

    public void clearPlacedBlocks(Predicate<PlacedBlockData> filter) {
        List<PlacedBlockData> snapshot = new ArrayList<>(this.placedBlock);
        List<PlacedBlockData> removed = new ArrayList<>();
        for (PlacedBlockData data : snapshot) {
            if (!filter.test(data)) {
                continue;
            }
            clearIfTrackedPlacement(data);
            removed.add(data);
        }
        if (!removed.isEmpty()) {
            this.placedBlock.removeAll(removed);
            persistPlacedBlocks();
        }
    }

    public void clearAllPlacedBlocks() {
        clearPlacedBlocks(data -> true);
    }

    public void removePlacedBlock(PlacedBlockData data) {
        if (this.placedBlock.remove(data)) {
            persistPlacedBlocks();
        }
    }

    public void onPluginDisable() {
        try {
            if (crashLockFile.exists()) {
                Files.deleteIfExists(crashLockFile.toPath());
            }
            if (placedBlockTempFile.exists()) {
                Files.deleteIfExists(placedBlockTempFile.toPath());
            }
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
        for (String line : staleLines) {
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

            if (entry.placedType != null && isTrackedPlacement(location, entry.placedType, entry.placedData)) {
                location.getBlock().setType(Material.AIR);
                cleaned++;
            }
        }

        persistRecoveredLines();

        if (cleaned > 0) {
            Bukkit.getLogger().info("[ThePit] Recovered and cleaned " + cleaned + " stale placed blocks after abnormal shutdown.");
        }
    }

    private void persistPlacedBlocks() {
        ensureTempDir();
        List<String> lines = new ArrayList<>(pendingRecoveryLines);
        for (PlacedBlockData data : this.placedBlock) {
            Location location = data.getLocation();
            if (location.getWorld() == null) {
                continue;
            }
            lines.add(serializeLocation(data));
        }

        try {
            Files.write(placedBlockTempFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to persist placed block temp file", exception);
        }
    }

    private void persistRecoveredLines() {
        ensureTempDir();
        try {
            List<String> lines = new ArrayList<>(pendingRecoveryLines);
            for (PlacedBlockData data : this.placedBlock) {
                Location location = data.getLocation();
                if (location.getWorld() == null) {
                    continue;
                }
                lines.add(serializeLocation(data));
            }

            if (lines.isEmpty()) {
                if (placedBlockTempFile.exists()) {
                    Files.deleteIfExists(placedBlockTempFile.toPath());
                }
                return;
            }
            Files.write(placedBlockTempFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to persist pending recovered placed blocks", exception);
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

    private void clearIfTrackedPlacement(PlacedBlockData data) {
        if (isTrackedPlacement(data)) {
            data.getLocation().getBlock().setType(Material.AIR);
        }
    }

    private boolean isTrackedPlacement(Location location, Material placedType, byte placedData) {
        return location.getBlock().getType() == placedType && location.getBlock().getData() == placedData;
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
