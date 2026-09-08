package cn.charlotte.pit.util.combat;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

/** The accepted hit includes damage spent on absorption, not just red hearts. */
public final class CombatDamage {
    private CombatDamage() {}

    public static double accepted(EntityDamageEvent event) {
        if (event == null || event.isCancelled()) return 0;
        double damage = positive(event.getFinalDamage());
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) {
            damage += positive(-event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        }
        return positive(damage);
    }

    private static double positive(double amount) {
        return Double.isFinite(amount) && amount > 0 ? amount : 0;
    }

    public static double healthIncludingAbsorption(Player player) {
        double absorption = player instanceof CraftPlayer
                ? ((CraftPlayer) player).getHandle().getAbsorptionHearts() : 0;
        return positive(player.getHealth()) + positive(absorption);
    }

    public static double realized(EntityDamageEvent event) {
        if (event == null || event.isCancelled()) return 0;
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (cn.charlotte.pit.listener.CombatListener.INSTANCE != null)
                return cn.charlotte.pit.listener.CombatListener.INSTANCE.getCreditedDamage(event, victim);
            return Math.min(healthIncludingAbsorption(victim), accepted(event));
        }
        return accepted(event);
    }
}
