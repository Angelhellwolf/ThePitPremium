package cn.charlotte.pit.map.kingsquests

import cn.charlotte.pit.ThePit
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Chicken
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

object KingsQuestChickenSpawner : BukkitRunnable() {

    const val METADATA_KEY = "kings_quest_chicken"

    private const val TARGET_COUNT = 10
    private const val SCAN_RADIUS = 20
    private const val MAX_Y_ABOVE_CENTER = 6
    private const val MAX_Y_BELOW_CENTER = 8
    private const val STAY_RADIUS_SQUARED = 28 * 28

    private var spawnPoints: List<Location> = emptyList()
    private var spawnPointRefreshAt = 0L

    override fun run() {
        val world = Bukkit.getWorlds().firstOrNull() ?: return
        val center = resolveCenter(world) ?: return

        if (spawnPoints.isEmpty() || System.currentTimeMillis() >= spawnPointRefreshAt) {
            spawnPoints = collectSpawnPoints(world, center)
            spawnPointRefreshAt = System.currentTimeMillis() + 60_000L
        }
        if (spawnPoints.isEmpty()) {
            return
        }

        val chickens = world.getEntitiesByClass(Chicken::class.java)
        val kept = ArrayList<Chicken>(TARGET_COUNT)
        for (chicken in chickens) {
            if (!chicken.isValid || !chicken.isAdult) {
                continue
            }
            if (!isQuestChicken(chicken) && !isInBottomArea(chicken.location, center)) {
                continue
            }
            if (!isInBottomArea(chicken.location, center)) {
                chicken.remove()
                continue
            }
            kept.add(chicken)
        }

        val missing = TARGET_COUNT - kept.size
        if (missing <= 0) {
            return
        }

        var spawned = 0
        var attempts = 0
        val maxAttempts = missing * 8
        while (spawned < missing && attempts < maxAttempts) {
            attempts++
            val point = spawnPoints[Random.nextInt(spawnPoints.size)]
            if (point.world == null) {
                continue
            }
            val ground = point.clone().add(0.0, -1.0, 0.0).block
            val feet = point.block
            val head = point.clone().add(0.0, 1.0, 0.0).block
            if (!isSafeSpawnGround(ground.type) || feet.type != Material.AIR || head.type != Material.AIR) {
                continue
            }
            if (spawnChicken(point)) {
                spawned++
            }
        }
    }

    private fun spawnChicken(location: Location): Boolean {
        return try {
            val chicken = location.world.spawn(location, Chicken::class.java)
            chicken.setAdult()
            chicken.ageLock = true
            chicken.removeWhenFarAway = false
            chicken.setMetadata(METADATA_KEY, FixedMetadataValue(ThePit.getInstance(), true))
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private fun isSafeSpawnGround(type: Material): Boolean {
        return type.isSolid
                && type != Material.LAVA
                && type != Material.STATIONARY_LAVA
                && type != Material.CACTUS
                && type != Material.FIRE
    }

    private fun isQuestChicken(chicken: Chicken): Boolean {
        return chicken.hasMetadata(METADATA_KEY)
    }

    private fun isInBottomArea(location: Location, center: Location): Boolean {
        if (location.world != center.world) {
            return false
        }
        if (location.blockY >= ThePit.getInstance().pitConfig.arenaHighestY - 5) {
            return false
        }
        val dx = location.x - center.x
        val dz = location.z - center.z
        return dx * dx + dz * dz <= STAY_RADIUS_SQUARED
    }

    private fun resolveCenter(world: World): Location? {
        val configured = ThePit.getInstance().pitConfig.ragePitMiddle
        if (configured != null && configured.world != null) {
            return configured
        }
        return Location(world, 0.5, 43.0, 0.5)
    }

    private fun collectSpawnPoints(world: World, center: Location): List<Location> {
        val points = ArrayList<Location>()
        val arenaHighestY = ThePit.getInstance().pitConfig.arenaHighestY
        val minY = (center.blockY - MAX_Y_BELOW_CENTER).coerceAtLeast(1)
        val maxY = (center.blockY + MAX_Y_ABOVE_CENTER).coerceAtMost(arenaHighestY - 6)

        for (dx in -SCAN_RADIUS..SCAN_RADIUS step 2) {
            for (dz in -SCAN_RADIUS..SCAN_RADIUS step 2) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue
                }
                val x = center.blockX + dx
                val z = center.blockZ + dz
                for (y in maxY downTo minY) {
                    val ground = world.getBlockAt(x, y, z)
                    val feet = world.getBlockAt(x, y + 1, z)
                    val head = world.getBlockAt(x, y + 2, z)
                    if (isSafeSpawnGround(ground.type) && feet.type == Material.AIR && head.type == Material.AIR) {
                        points.add(Location(world, x + 0.5, y + 1.0, z + 0.5))
                        break
                    }
                }
            }
        }
        return points
    }
}
