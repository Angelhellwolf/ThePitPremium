package cn.charlotte.pit.bot;

import cn.charlotte.pit.config.PitConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

final class PublicPitArena implements CombatArena {
    final World world;
    final double entryY, minY, minX, maxX, minZ, maxZ;
    final Location jumpCenter;
    final List<Location> spawns;
    final List<List<Location>> routes = new ArrayList<>();
    final int[] waits = new int[3], skinIndexes = new int[3];
    private final Location safeA, safeB;

    PublicPitArena(ConfigurationSection config, PitConfig pit) {
        List<Location> configured = pit.getSpawnLocations();
        if (configured == null || configured.isEmpty()) throw new IllegalArgumentException("Normal player spawn locations are missing");
        world = configured.get(0).getWorld();
        if (world == null) throw new IllegalArgumentException("Spawn world is not loaded");
        safeA = pit.getPitLocA(); safeB = pit.getPitLocB();
        if (safeA == null || safeB == null) throw new IllegalArgumentException("Pit safe zone is missing");
        entryY = config.getDouble("public-simulated-players.area.entry-y", 84);
        minY = config.getDouble("public-simulated-players.area.min-y", 50);
        minX = config.getDouble("public-simulated-players.area.min-x", -140);
        maxX = config.getDouble("public-simulated-players.area.max-x", 140);
        minZ = config.getDouble("public-simulated-players.area.min-z", -140);
        maxZ = config.getDouble("public-simulated-players.area.max-z", 140);
        jumpCenter = new Location(world, config.getDouble("public-simulated-players.jump-center.x", 0.5),
                configured.get(0).getY(), config.getDouble("public-simulated-players.jump-center.z", 0.5));
        spawns = new ArrayList<>();
        List<Map<?, ?>> profiles = config.getMapList("public-simulated-players.profiles");
        if (profiles.size() != 3) throw new IllegalArgumentException("Exactly three public profiles are required");
        for (int i = 0; i < 3; i++) {
            Map<?, ?> profile = profiles.get(i);
            int index = number(profile.get("spawn-index"), i * configured.size() / 3);
            if (index < 0 || index >= configured.size()) throw new IllegalArgumentException("Invalid normal spawn index");
            Location spawn = configured.get(index).clone();
            spawns.add(spawn);
            waits[i] = Math.max(10, number(profile.get("departure-delay-ticks"), 25 + i * 30));
            skinIndexes[i] = Math.max(0, number(profile.get("skin-index"), i));
            List<Location> path = new ArrayList<>();
            Object raw = profile.get("route");
            if (!(raw instanceof List)) throw new IllegalArgumentException("Each profile needs an upper-lobby route");
            for (Object point : (List<?>) raw) {
                String[] xz = point.toString().split(",");
                if (xz.length != 2) throw new IllegalArgumentException("Route point must be x,z");
                path.add(new Location(world, Double.parseDouble(xz[0].trim()), spawn.getY(), Double.parseDouble(xz[1].trim())));
            }
            if (path.isEmpty() || path.size() > 12) throw new IllegalArgumentException("Invalid departure route");
            routes.add(Collections.unmodifiableList(path));
        }
    }
    private static int number(Object value, int fallback) { return value instanceof Number ? ((Number) value).intValue() : fallback; }

    boolean safeZone(Location at) {
        return at.getWorld() == world && at.getX() + 0.8 > Math.min(safeA.getX(), safeB.getX())
                && at.getX() < Math.max(safeA.getX(), safeB.getX())
                && at.getY() + 2 > Math.min(safeA.getY(), safeB.getY()) && at.getY() < Math.max(safeA.getY(), safeB.getY())
                && at.getZ() + 0.8 > Math.min(safeA.getZ(), safeB.getZ()) && at.getZ() < Math.max(safeA.getZ(), safeB.getZ());
    }
    boolean outside(Location at) {
        return at.getWorld() != world || at.getY() < minY || at.getY() > 160
                || at.getX() < minX || at.getX() > maxX || at.getZ() < minZ || at.getZ() > maxZ;
    }
    public boolean combat(Location at) { return !outside(at) && !safeZone(at) && at.getY() < Math.min(safeA.getY(), safeB.getY()); }
    public boolean standable(Location at) {
        if (at.getWorld() != world || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return false;
        Material feet = at.getBlock().getType(), head = at.clone().add(0, 1, 0).getBlock().getType();
        Material floor = at.clone().add(0, -1, 0).getBlock().getType();
        return floor.isSolid() && floor != Material.CACTUS && clear(feet) && clear(head);
    }
    private boolean clear(Material type) { return !type.isSolid() && type != Material.WATER && type != Material.STATIONARY_WATER
            && type != Material.LAVA && type != Material.STATIONARY_LAVA && type != Material.FIRE; }

    Location patrol(int slot, long tick, Random random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = (tick * 0.013 + slot * 2.1 + attempt * 0.61);
            double radius = 5 + random.nextDouble() * 11;
            double x = jumpCenter.getX() + Math.cos(angle) * radius, z = jumpCenter.getZ() + Math.sin(angle) * radius;
            if (!world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4)) continue;
            for (int y = (int) entryY; y >= Math.max(minY, entryY - 20); y--) {
                Location point = new Location(world, x, y, z);
                if (standable(point)) return point;
            }
        }
        return null;
    }
}
