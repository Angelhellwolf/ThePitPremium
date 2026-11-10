package cn.charlotte.pit.events.impl.major;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

final class SpireTeleport {
    // The bundled spire.schematic contains nine playable floors.
    static final int FLOOR_COUNT = 9;
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2, 3, -3};

    private SpireTeleport() {
    }

    static int topFloor(List<Location> floors) {
        return Math.min(FLOOR_COUNT, floors.size()) - 1;
    }

    static Location findSafeLocation(Location center, int floor, Random random) {
        if (center == null || center.getWorld() == null || floor < 0 || floor >= FLOOR_COUNT) {
            return null;
        }

        // Leave space between the player's bounding box and the outer wall.
        int radius = Math.max(1, 30 - floor * 3);
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * radius);
            Location destination = findStandingPosition(center, x, z);
            if (destination != null) {
                return destination;
            }
        }

        // A blocked edge can fall back to the centre, but never to another floor.
        for (int radiusFromCenter = 0; radiusFromCenter <= 3; radiusFromCenter++) {
            for (int x = -radiusFromCenter; x <= radiusFromCenter; x++) {
                for (int z = -radiusFromCenter; z <= radiusFromCenter; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radiusFromCenter) continue;
                    Location destination = findStandingPosition(center,
                            center.getBlockX() + x, center.getBlockZ() + z);
                    if (destination != null) {
                        return destination;
                    }
                }
            }
        }
        return null;
    }

    private static Location findStandingPosition(Location center, int x, int z) {
        World world = center.getWorld();
        for (int offset : HEIGHT_OFFSETS) {
            int feetY = center.getBlockY() + offset;
            if (feetY < 1 || feetY + 1 >= world.getMaxHeight()) continue;

            Material support = world.getBlockAt(x, feetY - 1, z).getType();
            // Partial blocks (fences, slabs, etc.) are not safe full-height supports.
            if (!support.isOccluding() && support != Material.GLASS && support != Material.STAINED_GLASS) continue;
            if (world.getBlockAt(x, feetY, z).getType() != Material.AIR
                    || world.getBlockAt(x, feetY + 1, z).getType() != Material.AIR) continue;

            return new Location(world, x + 0.5, feetY + 0.05, z + 0.5,
                    center.getYaw(), center.getPitch());
        }
        return null;
    }
}
