package cn.charlotte.pit.util;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.configuration.ConfigurationSection;
import java.util.UUID;

/** Cosmetic profile values; progression, purchases and combat keep the real values. */
public final class PublicProfileDisplay {
    private PublicProfileDisplay() { }
    private static ConfigurationSection mask(UUID id) {
        ThePit plugin = ThePit.getInstance();
        return plugin == null || plugin.getConfig() == null ? null
                : plugin.getConfig().getConfigurationSection("public-profile-masks." + id);
    }
    public static boolean masked(UUID id) { return mask(id) != null; }
    public static int prestige(PlayerProfile profile) {
        ConfigurationSection mask = mask(profile.getPlayerUuid());
        return mask != null ? Math.max(0, Math.min(50, mask.getInt("prestige", 0)))
                : profile.isNicked() ? profile.getNickPrestige() : profile.getPrestige();
    }
    public static int level(PlayerProfile profile) {
        ConfigurationSection mask = mask(profile.getPlayerUuid());
        return mask != null ? Math.max(0, Math.min(120, mask.getInt("level", 1)))
                : profile.isNicked() ? profile.getNickLevel() : profile.getLevel();
    }
}
