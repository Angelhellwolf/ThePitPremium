package cn.charlotte.pit.util.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import cn.charlotte.pit.listener.CombatListener;

import java.util.UUID;

/** Fixed normal-damage abilities keep native armor/protection without becoming weapon swings. */
public final class SecondaryDamage {
    private static final ThreadLocal<Hit> current = new ThreadLocal<>();
    private SecondaryDamage() {}

    public static void grimReaper(Player attacker, Player victim) {
        Hit previous = current.get();
        current.set(new Hit(attacker.getUniqueId(), victim.getUniqueId(), 0, true));
        try {
            victim.damage(4.0, attacker);
        } finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }

    public static boolean isHit(EntityDamageByEntityEvent event) {
        return matches(event.getDamager(), event.getEntity());
    }

    public static boolean isGrimReaperKill(Player killer, Entity victim) {
        return matches(killer, victim) && current.get().grimReaper;
    }

    public static boolean isFromSecondary(Entity attacker, Entity victim) {
        return matches(attacker, victim);
    }

    public static void reflection(Player attacker, Player victim, double damage) {
        Hit previous = current.get();
        current.set(new Hit(attacker.getUniqueId(), victim.getUniqueId(), 0, false));
        try {
            victim.damage(damage, attacker);
        } finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }

    public static void lethalTrueDamage(Player attacker, Player victim, double damage) {
        Hit previous = current.get();
        current.set(new Hit(attacker == null ? victim.getUniqueId() : attacker.getUniqueId(), victim.getUniqueId(), damage, false));
        try {
            victim.damage(victim.getHealth() + 10000, attacker == null ? victim : attacker);
        } finally {
            if (previous == null) current.remove(); else current.set(previous);
        }
    }

    /** Preserve native death attribution without running another weapon swing or applying armor twice. */
    public static boolean applyTrueDamage(EntityDamageByEntityEvent event) {
        if (!isHit(event) || current.get().trueDamage <= 0 || !(event.getEntity() instanceof Player)) return false;
        Player victim = (Player) event.getEntity();
        if (CombatListener.INSTANCE != null) {
            CombatListener.INSTANCE.recordExtraDamage(event, Math.min(victim.getHealth(), current.get().trueDamage), victim.getHealth(), true);
        }
        event.setDamage(victim.getHealth() + 10000);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) event.setDamage(modifier, 0);
        }
        victim.setLastDamageCause(event);
        return true;
    }

    public static boolean isLethalTrueDamage(EntityDamageByEntityEvent event) {
        return isHit(event) && current.get().trueDamage > 0;
    }

    private static boolean matches(Entity attacker, Entity victim) {
        Hit hit = current.get();
        return hit != null && attacker != null && victim != null
                && hit.attacker.equals(attacker.getUniqueId()) && hit.victim.equals(victim.getUniqueId());
    }

    private static final class Hit {
        final UUID attacker, victim;
        final double trueDamage;
        final boolean grimReaper;
        Hit(UUID attacker, UUID victim, double trueDamage, boolean grimReaper) {
            this.attacker = attacker; this.victim = victim; this.trueDamage = trueDamage; this.grimReaper = grimReaper;
        }
    }
}
