package cn.charlotte.pit.util;

import cn.charlotte.pit.reward.RareSupply;
import cn.charlotte.pit.util.combat.CombatContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Controls enchantment side effects when the target is a managed synthetic player. */
public final class BotEnchantPolicy {
    private static final UUID SEVENTY_SEVEN_HZ = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");
    private static final double SEVENTY_SEVEN_BOT_EFFECT_CHANCE = 0.60;
    private static final String PROJECTILE_DECISION = "pit_bot_enchant_allowed";

    private BotEnchantPolicy() { }

    public static boolean effectAllowed(Player holder, Player opponent) {
        if (opponent == null || !RareSupply.syntheticTarget(opponent)) return true;
        Entity source = CombatContext.currentSource();
        if (source instanceof Projectile) {
            Boolean decision = projectileDecision((Projectile) source, opponent.getUniqueId());
            if (decision != null) return decision;
        }
        CombatContext context = CombatContext.current();
        if (context != null) return context.botEnchantAllowed(holder.getUniqueId(), opponent.getUniqueId());
        return SEVENTY_SEVEN_HZ.equals(holder.getUniqueId())
                && ThreadLocalRandom.current().nextDouble() < SEVENTY_SEVEN_BOT_EFFECT_CHANCE;
    }

    public static boolean projectileEffectAllowed(Projectile source, Player holder, Player opponent) {
        if (opponent == null || !RareSupply.syntheticTarget(opponent)) return true;
        Boolean existing = projectileDecision(source, opponent.getUniqueId());
        if (existing != null) return existing;
        boolean allowed = SEVENTY_SEVEN_HZ.equals(holder.getUniqueId())
                && ThreadLocalRandom.current().nextDouble() < SEVENTY_SEVEN_BOT_EFFECT_CHANCE;
        source.setMetadata(PROJECTILE_DECISION + ":" + opponent.getUniqueId(),
                new org.bukkit.metadata.FixedMetadataValue(cn.charlotte.pit.ThePit.getInstance(), allowed));
        return allowed;
    }

    private static Boolean projectileDecision(Projectile source, UUID target) {
        String key = PROJECTILE_DECISION + ":" + target;
        for (org.bukkit.metadata.MetadataValue value : source.getMetadata(key)) {
            if (value.getOwningPlugin() == cn.charlotte.pit.ThePit.getInstance()) return value.asBoolean();
        }
        return null;
    }

    public static boolean isSeventySeven(Player player) {
        return player != null && SEVENTY_SEVEN_HZ.equals(player.getUniqueId());
    }
}
