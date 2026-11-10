package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.util.PlayerUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Beneficial refreshes do not shorten an equal effect or downgrade a stronger one. */
public final class CombatPotions {
    private CombatPotions() {}

    public static PotionEffect find(Player player, PotionEffectType type) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(type)) return effect;
        }
        return null;
    }

    public static void refresh(Player player, PotionEffect effect) {
        PotionEffect current = find(player, effect.getType());
        if (current != null && (current.getAmplifier() > effect.getAmplifier()
                || (current.getAmplifier() == effect.getAmplifier() && current.getDuration() >= effect.getDuration()))) return;
        PlayerUtil.addPotionEffect(player, effect, true);
    }

    public static void extendSpeed(Player player, int enchantLevel) {
        if (enchantLevel <= 0) return;
        PotionEffect current = find(player, PotionEffectType.SPEED);
        if (current == null || current.getDuration() <= 0) return;
        int extra = Math.min(enchantLevel, 3) * (current.getAmplifier() >= 1 ? 20 : 40);
        int duration = (int) Math.min(Integer.MAX_VALUE, (long) current.getDuration() + extra);
        PlayerUtil.addPotionEffect(player, new PotionEffect(current.getType(), duration, current.getAmplifier(),
                current.isAmbient(), current.hasParticles()), true);
    }
}
