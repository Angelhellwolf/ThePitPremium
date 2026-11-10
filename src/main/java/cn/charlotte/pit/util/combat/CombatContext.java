package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.data.PlayerProfile;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** The current hit is visible to effects before the accepted-hit statistics are committed. */
public final class CombatContext implements AutoCloseable {
    private static final ThreadLocal<CombatContext> CURRENT = new ThreadLocal<>();
    private static final Map<EntityDamageByEntityEvent, List<Runnable>> PENDING = new WeakHashMap<>();
    private final CombatContext previous;
    private final EntityDamageByEntityEvent event;
    private double conversion;
    private double extraNormalDamage;
    private double armorRetention = 1;
    private List<Runnable> acceptedEffects;
    private List<Finisher> finishers;
    private Map<String, Boolean> botEnchantDecisions;

    private CombatContext(EntityDamageByEntityEvent event) {
        this.previous = CURRENT.get();
        this.event = event;
        CURRENT.set(this);
    }

    public static CombatContext open(EntityDamageByEntityEvent event) {
        return new CombatContext(event);
    }

    public static Entity currentSource() {
        CombatContext context = CURRENT.get();
        return context == null ? null : context.event.getDamager();
    }

    public static CombatContext current() { return CURRENT.get(); }

    public boolean botEnchantAllowed(UUID holder, UUID target) {
        if (botEnchantDecisions == null) botEnchantDecisions = new HashMap<>();
        String key = holder + ":" + target;
        Boolean decision = botEnchantDecisions.get(key);
        if (decision != null) return decision;
        decision = cn.charlotte.pit.util.BotEnchantPolicy.isSeventySeven(
                org.bukkit.Bukkit.getPlayer(holder))
                && ThreadLocalRandom.current().nextDouble() < 0.60;
        botEnchantDecisions.put(key, decision);
        return decision;
    }

    public static boolean targetIsBot() {
        CombatContext context = CURRENT.get();
        if (context == null || !(context.event.getEntity() instanceof Player)) return false;
        cn.charlotte.pit.data.PlayerProfile profile = cn.charlotte.pit.data.PlayerProfile
                .getPlayerProfileByUuid(context.event.getEntity().getUniqueId());
        return profile != null && profile.isLoaded() && profile.isBot();
    }

    public static boolean shotSneaking(Player player) {
        Entity source = currentSource();
        if (source != null) {
            for (org.bukkit.metadata.MetadataValue value : source.getMetadata("pit_shot_sneaking")) {
                if (value.getOwningPlugin() == cn.charlotte.pit.ThePit.getInstance()) return value.asBoolean();
            }
        }
        return player.isSneaking();
    }

    public static Player playerSource(Entity source) {
        if (source instanceof Player) return (Player) source;
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
            return (Player) ((Projectile) source).getShooter();
        }
        return null;
    }

    public static int meleeHits(Player player) {
        return hits(player, false);
    }

    public static int bowHits(Player player) {
        return hits(player, true);
    }

    private static int hits(Player player, boolean bow) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        int accepted = bow ? profile.getBowHit() : profile.getMeleeHit();
        CombatContext context = CURRENT.get();
        if (context == null) return accepted;
        Entity source = context.event.getDamager();
        Player attacker = playerSource(source);
        if (attacker == null || !attacker.getUniqueId().equals(player.getUniqueId())) return accepted;
        if (!(context.event.getEntity() instanceof Player) || player.getUniqueId().equals(context.event.getEntity().getUniqueId()) || SecondaryDamage.isHit(context.event)
                || CombatDamage.accepted(context.event) <= 0 || (bow ? !(source instanceof Arrow) : !(source instanceof Player))) {
            return -1;
        }
        return accepted == Integer.MAX_VALUE ? 1 : accepted + 1;
    }

    /** Null means that this is not the player's projectile hit; use the normal melee critical test. */
    public static Boolean projectileCritical(Player player) {
        CombatContext context = CURRENT.get();
        if (context == null || !(context.event.getDamager() instanceof Projectile)) return null;
        Player attacker = playerSource(context.event.getDamager());
        if (attacker == null || !attacker.getUniqueId().equals(player.getUniqueId())) return null;
        return context.event.getDamager() instanceof Arrow && ((Arrow) context.event.getDamager()).isCritical();
    }

    public static void convertNormalToTrue(double proportion) {
        CombatContext context = CURRENT.get();
        if (context != null && Double.isFinite(proportion)) {
            context.conversion = Math.min(1, Math.max(context.conversion, proportion));
        }
    }

    public double convertedDamage(double normalDamage) {
        return Math.max(0, normalDamage) * conversion;
    }

    public static void addNormalDamage(double damage) {
        CombatContext context = CURRENT.get();
        if (context != null && Double.isFinite(damage) && damage > 0) context.extraNormalDamage += damage;
    }

    public double getExtraNormalDamage() { return extraNormalDamage; }

    public static void weakenArmor(double fraction) {
        CombatContext context = CURRENT.get();
        if (context != null) context.armorRetention = Math.max(0, Math.min(1, 1 - fraction));
    }

    public void applyArmorPenalty() {
        org.bukkit.event.entity.EntityDamageEvent.DamageModifier armor = org.bukkit.event.entity.EntityDamageEvent.DamageModifier.ARMOR;
        if (armorRetention < 1 && event.isApplicable(armor) && event.getDamage(armor) < 0)
            event.setDamage(armor, event.getDamage(armor) * armorRetention);
    }

    public static boolean queueFinisher(double threshold, double trueDamage, Runnable effect) {
        CombatContext context = CURRENT.get();
        if (context == null) return false;
        if (context.finishers == null) context.finishers = new ArrayList<>();
        context.finishers.add(new Finisher(threshold, trueDamage, effect));
        return true;
    }

    public boolean hasFinishers() { return finishers != null; }

    public void applyFinishers(Player target, double normalDamage, AtomicDouble trueDamage, boolean trueImmune, double outgoingScale) {
        if (finishers == null) return;
        for (Finisher finisher : finishers) {
            double effectiveTrue = trueImmune && trueDamage.get() < 1000 ? 0 : Math.max(0, trueDamage.get());
            if (target.getHealth() - Math.max(0, normalDamage) - effectiveTrue < finisher.threshold) {
                trueDamage.addAndGet(finisher.trueDamage >= 1000 ? finisher.trueDamage : finisher.trueDamage * outgoingScale);
                finisher.effect.run();
            }
        }
    }

    private static final class Finisher {
        final double threshold, trueDamage;
        final Runnable effect;
        Finisher(double threshold, double trueDamage, Runnable effect) {
            this.threshold = threshold; this.trueDamage = trueDamage; this.effect = effect;
        }
    }

    public static boolean isFullyChargedArrow() {
        CombatContext context = CURRENT.get();
        if (context == null || !(context.event.getDamager() instanceof Arrow)) return false;
        for (org.bukkit.metadata.MetadataValue value : context.event.getDamager().getMetadata("pin_down")) {
            if (value.getOwningPlugin() == cn.charlotte.pit.ThePit.getInstance()) return value.asBoolean();
        }
        return ((Arrow) context.event.getDamager()).isCritical();
    }

    public static void afterAccepted(Runnable effect) {
        CombatContext context = CURRENT.get();
        if (context == null) effect.run();
        else {
            if (context.acceptedEffects == null) context.acceptedEffects = new ArrayList<>();
            context.acceptedEffects.add(effect);
        }
    }

    public static void complete(EntityDamageByEntityEvent event) {
        List<Runnable> effects = PENDING.remove(event);
        if (effects == null || event.isCancelled() || CombatDamage.realized(event) <= 0) return;
        for (Runnable effect : effects) {
            try { effect.run(); }
            catch (RuntimeException failure) {
                cn.charlotte.pit.ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "Accepted hit effect failed; remaining effects continue", failure);
            }
        }
    }

    @Override
    public void close() {
        if (acceptedEffects != null) PENDING.computeIfAbsent(event, ignored -> new ArrayList<>()).addAll(acceptedEffects);
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
