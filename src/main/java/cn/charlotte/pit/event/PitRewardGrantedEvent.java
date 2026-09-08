package cn.charlotte.pit.event;

import cn.charlotte.pit.reward.RewardSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Immutable post-credit notification. Reward modifiers belong in PitKill/PitAssistEvent. */
public final class PitRewardGrantedEvent extends PitEvent {
    private final Player player;
    private final LivingEntity target;
    private final RewardSource source;
    private final double coins;
    private final double experience;
    private final boolean progressionEligible;
    public PitRewardGrantedEvent(Player player, LivingEntity target, RewardSource source,
            double coins, double experience, boolean progressionEligible) {
        this.player = player; this.target = target; this.source = source;
        this.coins = coins; this.experience = experience; this.progressionEligible = progressionEligible;
    }
    public Player getPlayer() { return player; }
    public LivingEntity getTarget() { return target; }
    public RewardSource getSource() { return source; }
    public double getCoins() { return coins; }
    public double getExperience() { return experience; }
    public boolean isProgressionEligible() { return progressionEligible; }
}

