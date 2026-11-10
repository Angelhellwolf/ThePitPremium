package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

/** Shared immunity and reflection rules for both attack effects and direct true-damage abilities. */
public final class TrueDamageRules {
    private TrueDamageRules() {}

    public static boolean hasResistance(Player victim) {
        long now = System.currentTimeMillis();
        for (MetadataValue value : victim.getMetadata("true_damage_immune")) {
            if (value.getOwningPlugin() == ThePit.getInstance() && value.asLong() > now) return true;
        }
        return false;
    }

    public static boolean hasResistance(Player victim, Player attacker) {
        if (hasResistance(victim)) return true;
        return attacker != null && !PlayerUtil.shouldIgnoreEnchant(victim, attacker)
                && Utils.getEnchantLevel(victim.getInventory().getLeggings(), "Echo_Of_Snowl and_Pants") > 0
                && attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS)
                && attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
    }

    public static double damageMultiplier(Player victim, Player attacker) {
        if (attacker == null ? PlayerUtil.shouldIgnoreEnchant(victim) : PlayerUtil.shouldIgnoreEnchant(victim, attacker)) return 1;
        return Utils.getEnchantLevel(victim.getInventory().getLeggings(), "OPDamageEnchant") > 0 ? 0.1 : 1;
    }

    public static int mirrorLevel(Player victim, Player attacker) {
        if (attacker == null || victim.getUniqueId().equals(attacker.getUniqueId())
                || PlayerUtil.shouldIgnoreEnchant(victim, attacker)) return 0;
        return Math.max(0, Math.min(3, Utils.getEnchantLevel(victim.getInventory().getLeggings(), "Mirror")));
    }

    public static double reflectable(Player victim, int level, double damage) {
        if (level < 2 || !Double.isFinite(damage) || damage <= 0) return 0;
        long now = System.currentTimeMillis();
        for (MetadataValue value : victim.getMetadata("mirror_latest_active")) {
            if (value.getOwningPlugin() == ThePit.getInstance() && now - value.asLong() < 500) return 0;
        }
        victim.setMetadata("mirror_latest_active", new FixedMetadataValue(ThePit.getInstance(), now));
        return damage * (level - 1) * 0.25;
    }
}
