package cn.charlotte.pit.bot;

import cn.charlotte.pit.data.PlayerProfile;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.Collection;
import java.util.Random;

/** Uses the existing Citizens/NMS player combat path, with Frost-style bounded aim and native knockback. */
final class PublicPlayerCombat {
    private final CombatArena arena;
    private final Random random;

    PublicPlayerCombat(CombatArena arena, Random random) { this.arena = arena; this.random = random; }

    boolean targetable(Player bot, Player target) {
        if (target == null || target == bot || !target.isOnline() || target.isDead() || target.hasMetadata("NPC")
                || target.getGameMode() != GameMode.SURVIVAL || target.getWorld() != bot.getWorld()
                || !arena.combat(target.getLocation())) return false;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        return profile.isLoaded() && !profile.isBot() && profile.isInArena();
    }

    Player findTarget(PublicPlayerState state, Collection<? extends Player> players) {
        Player bot = state.entity;
        Location here = bot.getLocation();
        Vector facing = here.getDirection().setY(0);
        Player closest = null;
        double best = 32 * 32;
        for (Player player : players) {
            if (!targetable(bot, player)) continue;
            Location at = player.getLocation();
            double distance = here.distanceSquared(at);
            if (distance > best || !bot.hasLineOfSight(player)) continue;
            Vector toward = at.toVector().subtract(here.toVector()).setY(0);
            // Nearby movement is noticeable; distant acquisition requires a normal forward view cone.
            if (distance > 64 && toward.lengthSquared() > 0.001 && facing.dot(toward.normalize()) < 0.25) continue;
            closest = player;
            best = distance;
        }
        return closest;
    }

    void navigate(PublicPlayerState state, Location destination, long tick) {
        if (destination == null) return;
        if (tick < state.nextPathTick && state.npc.getNavigator().isNavigating()) return;
        if (state.navGoal != null && state.navGoal.getWorld() == destination.getWorld()
                && state.navGoal.distanceSquared(destination) < 2.25 && state.npc.getNavigator().isNavigating()) return;
        state.nextPathTick = tick + 20 + state.identity.slot * 2L;
        state.navGoal = destination.clone();
        NavigatorParameters params = state.npc.getNavigator().getDefaultParameters();
        params.range(40).distanceMargin(0.55).pathDistanceMargin(0.55).stationaryTicks(60)
                .updatePathRate(20).avoidWater(true).stuckAction(null).destinationTeleportMargin(-1).speedModifier(1.0F);
        state.npc.getNavigator().setTarget(destination);
    }

    void fight(PublicPlayerState state, Player target, long tick) {
        fight(state,target,tick,null);
    }
    void fight(PublicPlayerState state, Player target, long tick, PublicCombatDifficulty.Style style) {
        Player bot = state.entity;
        Location here = bot.getLocation(), there = target.getLocation();
        double distance = here.distanceSquared(there);
        boolean aimed = look(bot, target.getEyeLocation(), style==null?18.0F:style.turn);
        if (recoveringKnockback(state,tick)) {
            cancel(state);
        } else if (distance > 16 || Math.abs(here.getY() - there.getY()) > 1.2 || !bot.hasLineOfSight(target)) {
            bot.setSprinting(true);
            navigate(state, there, tick);
        } else {
            cancel(state);
            Vector toward = there.toVector().subtract(here.toVector()).setY(0);
            double horizontal = toward.length();
            if (horizontal > 0.05) {
                toward.multiply(1.0 / horizontal);
                if ((tick + state.identity.slot * 17) % 43 == 0) state.strafe = random.nextBoolean() ? 1 : -1;
                double forward = horizontal < 1.45 ? -0.4 : horizontal > 2.6 ? 0.85 : 0.18;
                double sideways = ((tick + state.identity.slot * 11) % 39 < (style==null?9:39*style.strafeChance)) ? state.strafe * 0.3 : 0;
                Vector direction = toward.clone().multiply(forward).add(new Vector(-toward.getZ(), 0, toward.getX()).multiply(sideways));
                if(style!=null)direction.multiply(style.moveScale);
                move(bot, direction, true, false);
            }
        }
        // Native player attack: weapon, armor, enchantments and the server's damage pipeline decide the result.
        if (tick >= state.nextAttackTick && distance <= 9.0 && aimed && bot.hasLineOfSight(target)
                && arena.combat(here) && targetable(bot, target)) {
            state.nextAttackTick = tick + (style==null?5+state.identity.slot+random.nextInt(3):style.minimumAttack+random.nextInt(style.attackJitter));
            if(style!=null&&random.nextDouble()<style.hesitateChance)return;
            PlayerAnimation.ARM_SWING.play(bot);
            NMS.attack(bot, target);
        }
    }

    static boolean recoveringKnockback(PublicPlayerState state,long tick){
        if(tick<state.knockbackUntil)return true;
        return state.knockbackUntil>0&&tick<state.knockbackUntil+12&&state.entity!=null&&!state.entity.isOnGround();
    }

    void move(Player bot, Vector direction, boolean sprint, boolean deliberateDrop) {
        if (direction.lengthSquared() < 0.001) return;
        if (direction.lengthSquared() > 1) direction.normalize();
        Location ahead = bot.getLocation().add(direction.clone().multiply(0.7));
        Vector velocity = bot.getVelocity();
        if (bot.isOnGround() && !deliberateDrop && !arena.standable(ahead)) {
            if (arena.standable(ahead.clone().add(0, 1, 0))) velocity.setY(0.42);
            else {
                velocity.setX(0); velocity.setZ(0); bot.setVelocity(velocity); return;
            }
        }
        double speed = speed(bot, sprint) * (bot.isOnGround() || deliberateDrop ? 1 : 0.35);
        velocity.setX(direction.getX() * speed);
        velocity.setZ(direction.getZ() * speed);
        bot.setSprinting(sprint);
        bot.setVelocity(velocity);
    }

    static double speed(Player player, boolean sprint) {
        double factor = 1;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) factor *= 1 + 0.2 * (effect.getAmplifier() + 1);
            if (effect.getType().equals(PotionEffectType.SLOW)) factor *= Math.max(0, 1 - 0.15 * (effect.getAmplifier() + 1));
        }
        return Math.max(0, Math.min(0.6, player.getWalkSpeed() * (sprint ? 1.4 : 1.0) * factor));
    }

    static boolean look(Player player, Location at, float maxTurn) {
        Vector delta = at.toVector().subtract(player.getEyeLocation().toVector());
        double horizontal = Math.hypot(delta.getX(), delta.getZ());
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
        float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.getY(), horizontal));
        Location here = player.getLocation();
        float difference = (wantedYaw - here.getYaw()) % 360;
        if (difference > 180) difference -= 360;
        if (difference < -180) difference += 360;
        float yaw = here.getYaw() + Math.max(-maxTurn, Math.min(maxTurn, difference));
        float pitch = here.getPitch() + Math.max(-maxTurn, Math.min(maxTurn, wantedPitch - here.getPitch()));
        NMS.look(player, yaw, pitch);
        return Math.abs(difference) <= 30 && Math.abs(wantedPitch - pitch) <= 25;
    }

    static void cancel(PublicPlayerState state) {
        if (state.npc != null && state.npc.getNavigator().isNavigating()) state.npc.getNavigator().cancelNavigation();
        state.navGoal = null;
    }
}
