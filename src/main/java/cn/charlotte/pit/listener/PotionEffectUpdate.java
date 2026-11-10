package cn.charlotte.pit.listener;

import org.bukkit.potion.PotionEffect;

/** Distinguishes a live effect's periodic packet refresh from a new application. */
final class PotionEffectUpdate {
    private PotionEffectUpdate() { }

    static boolean isCountdown(PotionEffect previous, PotionEffect current, int elapsedTicks) {
        return previous.getType().equals(current.getType())
                // Scheduler callbacks run before entity ticking; allow that one-tick phase offset.
                && current.getDuration() <= Math.max(0, previous.getDuration() - Math.max(0, elapsedTicks - 1))
                && current.getAmplifier() == previous.getAmplifier()
                && current.isAmbient() == previous.isAmbient()
                && current.hasParticles() == previous.hasParticles();
    }
}
