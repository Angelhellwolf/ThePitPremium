package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hit income follows the same human-target restriction as the fixed Bot kill rewards. */
public final class HitRewardRules {
    private HitRewardRules() {}

    public static boolean eligible(Player attacker, Entity target, double damage, AtomicBoolean cancelled) {
        if (!(target instanceof Player) || attacker == null || attacker.getUniqueId().equals(target.getUniqueId())
                || !Double.isFinite(damage) || damage <= 0 || cancelled.get()) return false;
        PlayerProfile victim = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        return victim != null && victim.isLoaded() && !victim.isBot();
    }
}
