package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

/** A projectile keeps the bow's enchantments even after the shooter changes the held item. */
public final class ProjectileEnchantments {
    private ProjectileEnchantments() {}

    public static void capture(Entity projectile, ItemStack bow) {
        if (projectile == null || bow == null) return;
        net.minecraft.server.v1_8_R3.ItemStack nms = UtilKt.reflectGetNmsItem(bow);
        if (nms == null || nms.getTag() == null) return;
        NBTTagCompound extra = nms.getTag().getCompound("extra");
        NBTTagList entries = extra.getList("ench", 8);
        StringBuilder encoded = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            String[] parts = entries.getString(i).split(":", 2);
            if (parts.length != 2) continue;
            try {
                int level = Integer.parseInt(parts[1].trim());
                if (level <= 0) continue;
                String name = parts[0];
                if ("bow_combo".equals(name)) name = "bow_combo_enchant";
                if ("sprint_drain".equals(name)) name = "sprint_drain_enchant";
                if (!seen.add(name)) continue;
                if (encoded.length() > 0) encoded.append(';');
                encoded.append(name).append(':').append(level);
            } catch (NumberFormatException ignored) { }
        }
        if (encoded.length() > 0) projectile.setMetadata("enchant", new FixedMetadataValue(ThePit.getInstance(), encoded.toString()));
    }

    public static int level(Entity projectile, String name) {
        if (projectile == null) return 0;
        for (MetadataValue value : projectile.getMetadata("enchant")) {
            if (value.getOwningPlugin() != ThePit.getInstance()) continue;
            for (String encoded : value.asString().split(";")) {
                String[] parts = encoded.split(":", 2);
                if (parts.length != 2 || !parts[0].equals(name)) continue;
                try {
                    int level = Integer.parseInt(parts[1]);
                    if (level > 0) return level;
                } catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }
}
