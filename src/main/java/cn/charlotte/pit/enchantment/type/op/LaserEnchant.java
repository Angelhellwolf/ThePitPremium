package cn.charlotte.pit.enchantment.type.op;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.beam.beam.Beam;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: EmptyIrony
 * @Date: 2021/4/17 22:35
 */
@ArmorOnly
public class LaserEnchant extends AbstractEnchantment implements IAttackEntity, ITickTask {
    private final Map<UUID, TargetInfo> tracker = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "镭射激光";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "laser";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.OP;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7攻击选中一名玩家并对其发射激光,持续5秒,每秒造成 &c0.5❤ 必中&7伤害（目标离开8格范围则中止）";
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(target instanceof Player) || cancel.get()) return;
        TargetInfo previous = tracker.get(attacker.getUniqueId());
        if (previous != null && previous.until <= System.currentTimeMillis()) { previous.beam.stop(); tracker.remove(attacker.getUniqueId()); }
        if (tracker.containsKey(attacker.getUniqueId())) {
            return;
        }

        final Beam beam = new Beam(attacker.getLocation(), target.getLocation(), 100, 10);
        beam.start();

        final TargetInfo targetInfo = new TargetInfo();
        targetInfo.beam = beam;
        targetInfo.player = (Player) target;
        targetInfo.number = 0;
        targetInfo.until = System.currentTimeMillis() + 5000L;
        targetInfo.life = cn.charlotte.pit.util.combat.PlayerLife.capture((Player) target);

        tracker.put(attacker.getUniqueId(), targetInfo);
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        final TargetInfo target = tracker.get(player.getUniqueId());
        if (target == null) {
            return;
        }
        if (target.player == null) {
            return;
        }

        if (cn.charlotte.pit.util.combat.PlayerLife.isCurrent(target.player, target.life) && player.getWorld().equals(target.player.getWorld())) {
            if (player.getLocation().distanceSquared(target.player.getLocation()) <= 8 * 8 && target.number < 5 && System.currentTimeMillis() < target.until) {
                target.number++;
                target.beam.setStartingPosition(player.getLocation());
                target.beam.setEndingPosition(target.player.getLocation());
                PlayerUtil.damage(player, target.player, PlayerUtil.DamageType.TRUE, 1D, false);
                return;
            }
        }
        target.beam.stop();

        tracker.remove(player.getUniqueId());
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }

    public static class TargetInfo {
        private Player player;
        private int number;
        private long until;
        private Object life;
        private Beam beam;
    }
}
