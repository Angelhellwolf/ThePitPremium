package cn.charlotte.pit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Notification after player-sourced, nonlethal true damage has been committed.
 * Not cancellable: this is not another attack and must not apply armor or weapon effects again.
 */
public final class PitDirectDamageEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player attacker;
    private final Player victim;
    private final double damage;

    public PitDirectDamageEvent(Player attacker, Player victim, double damage) {
        this.attacker = attacker;
        this.victim = victim;
        this.damage = damage;
    }

    public Player getAttacker() {
        return attacker;
    }

    public Player getVictim() {
        return victim;
    }

    public double getDamage() {
        return damage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
