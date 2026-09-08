package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class KnockbackEnchant extends AbstractEnchantment implements IAttackEntity {

    @Override
    public String getEnchantName() {
        return "击退";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "knockback_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7近战命中将目标额外击退约 &e" + (enchantLevel * 3) + " &7格";
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (direction.lengthSquared() < 1.0E-6) {
                return;
            }
            if (target instanceof Player) {
                SpiterCompatibility.exemptVelocity((Player) target, 20L);
            }
            target.setVelocity(direction.normalize().multiply(0.6 + enchantLevel * 0.55).setY(0.35));
        });
    }
}
