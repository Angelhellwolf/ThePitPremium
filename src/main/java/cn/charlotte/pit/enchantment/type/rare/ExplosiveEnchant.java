package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
public class ExplosiveEnchant extends AbstractEnchantment implements IPlayerShootEntity, IActionDisplayEnchant {

    private static final Map<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "爆炸";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "explosive_enchant";
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
        return "&7箭矢命中时产生爆炸,击退附近玩家并造成 &c" + (0.5 * enchantLevel) + "❤ &7的&f真实&7伤害"
                + "/s&7(" + (enchantLevel == 2 ? 3 : 5) + "秒冷却)";
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (cancel.get() || !isEffectActive(attacker, target instanceof Player ? (Player) target : null)) return;
        if (!cooldown.getOrDefault(attacker.getUniqueId(), new Cooldown(0)).hasExpired()) {
            return;
        }
        cooldown.put(attacker.getUniqueId(), new Cooldown(enchantLevel == 2 ? 3 : 5, TimeUnit.SECONDS));
        float power = 0F;
        target.getWorld().createExplosion(target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ(), power, false, false);
        double radius = 2 + enchantLevel;
        for (Player nearby : PlayerUtil.getNearbyPlayers(target.getLocation(), radius)) {
            if (nearby.equals(attacker) || !isEffectActive(attacker, nearby)) {
                continue;
            }
            PlayerUtil.damage(attacker, nearby, PlayerUtil.DamageType.TRUE, enchantLevel * 1.0, true);
            SpiterCompatibility.exemptVelocity(nearby, 20L);
            Vector direction = nearby.getLocation().toVector().subtract(target.getLocation().toVector());
            if (direction.lengthSquared() > 1.0E-6) {
                nearby.setVelocity(direction.normalize().multiply(0.7 + enchantLevel * 0.25).setY(0.4));
            }
        }
    }

    @Override
    public String getText(int level, Player player) {
        return getCooldownActionText(cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)));
    }
}
