package cn.charlotte.pit.event;

import java.util.UUID;

/** Actual credited damage, after cancellation, mitigation, absorption and overkill limits. */
public final class PitDamageAppliedEvent extends PitEvent {
    private final UUID attacker;
    private final UUID victim;
    private final double amount;

    public PitDamageAppliedEvent(UUID attacker, UUID victim, double amount) {
        this.attacker = attacker;
        this.victim = victim;
        this.amount = amount;
    }

    public UUID getAttacker() { return attacker; }
    public UUID getVictim() { return victim; }
    public double getAmount() { return amount; }
}
