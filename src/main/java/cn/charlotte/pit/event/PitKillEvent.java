package cn.charlotte.pit.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/30 1:07
 */
public class PitKillEvent extends PitEvent {
    private final Player killer;
    private final LivingEntity target;
    private double coins;
    private double exp;
    private final boolean progressionEligible;
    private double hillMultiplier = 1.0;

    public PitKillEvent(Player killer, LivingEntity target, double coins, double exp) {
        this(killer, target, coins, exp, true);
    }

    public PitKillEvent(Player killer, LivingEntity target, double coins, double exp, boolean progressionEligible) {
        this.killer = killer;
        this.target = target;
        this.coins = coins;
        this.exp = exp;
        this.progressionEligible = progressionEligible;
    }

    public Player getKiller() {
        return killer;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public double getCoins() {
        return coins;
    }

    public void setCoins(double coins) {
        this.coins = coins;
    }

    public double getExp() {
        return exp;
    }

    public void setExp(double exp) {
        this.exp = exp;
    }

    public boolean isProgressionEligible() {
        return progressionEligible;
    }

    public boolean isHumanPvp() {
        return target instanceof Player
                && !cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(killer.getUniqueId()).isBot()
                && !cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(target.getUniqueId()).isBot();
    }

    public double getHillMultiplier() { return hillMultiplier; }
    public void setHillMultiplier(double multiplier) { this.hillMultiplier = multiplier; }
}
