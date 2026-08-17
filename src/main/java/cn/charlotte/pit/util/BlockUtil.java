package cn.charlotte.pit.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author Araykal
 * @since 2025/1/22
 */
public class BlockUtil {
    public static Set<Material> blockType = EnumSet.of(Material.CACTUS, Material.ENDER_PORTAL);

    public static boolean isBlockNearby(Location location, int range) {
        World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        int minY = Math.max(0, baseY - range);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + range);

        for (int x = -range; x <= range; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -range; z <= range; z++) {
                    Material material = Material.getMaterial(world.getBlockTypeIdAt(baseX + x, y, baseZ + z));
                    if (material != null && blockType.contains(material)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
