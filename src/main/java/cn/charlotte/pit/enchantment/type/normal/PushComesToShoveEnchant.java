package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.util.combat.CombatContext;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
public class PushComesToShoveEnchant extends AbstractEnchantment implements IPlayerShootEntity, IActionDisplayEnchant {
    @Override
    public String getEnchantName() {
        return "推波助澜";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "push_comes_to_shove_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        String extra = "";
        if (enchantLevel >= 2) {
            extra = "/s&7并使当次攻击额外造成 &c" + (enchantLevel >= 3 ? 1.0 : 0.5) + "❤ &f真实&7伤害";
        }
        int punch = enchantLevel;
        return "&7每 &e3 &7次弓箭命中附加 &f冲击 " + punch + extra;
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (CombatContext.bowHits(attacker) % 3 != 0) {
            return;
        }
        double punch = 0.6 * enchantLevel;
        if (enchantLevel >= 2) {
            finalDamage.getAndAdd(enchantLevel >= 3 ? 2.0 : 1.0);
        }
        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (direction.lengthSquared() <= 1.0E-6) {
            return;
        }
        Vector velocity = direction.normalize().multiply(punch).setY(0.25 + 0.05 * enchantLevel);
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (!target.isValid()) {
                return;
            }
            if (target instanceof Player) {
                Player player = (Player) target;
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                SpiterCompatibility.exemptVelocity(player, 20L);
            }
            target.setVelocity(velocity);
        });
    }

    @Override
    public String getText(int level, Player player) {
        return getHitActionText(player, 3);
    }
}
