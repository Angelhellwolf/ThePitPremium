package cn.charlotte.pit.event;

import org.bukkit.entity.Player;

/** Fired after death protection and item life deduction, before temporary death drops are cleared. */
public final class PitItemLivesLostEvent extends PitEvent {
    private final Player victim;
    private final Player killer;
    private final int lostLives;

    public PitItemLivesLostEvent(Player victim, Player killer, int lostLives) {
        this.victim = victim; this.killer = killer; this.lostLives = Math.max(0, lostLives);
    }
    public Player getVictim() { return victim; }
    public Player getKiller() { return killer; }
    public int getLostLives() { return lostLives; }
}
