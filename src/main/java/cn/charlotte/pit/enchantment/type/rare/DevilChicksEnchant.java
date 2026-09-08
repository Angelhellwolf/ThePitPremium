package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
public class DevilChicksEnchant extends AbstractEnchantment implements IPlayerShootEntity {

    @Override
    public String getEnchantName() {
        return "恶魔小鸡!";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "devil_chicks_enchant";
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
        return "&7箭矢命中时生成 &e" + enchantLevel + " &7只爆炸小鸡"
                + "/s&7爆炸对附近玩家造成大幅击退与 &c0.6❤ &7的&f真实&7伤害";
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            for (int i = 0; i < enchantLevel; i++) {
                Chicken chicken = (Chicken) target.getWorld().spawnEntity(target.getLocation(), EntityType.CHICKEN);
                chicken.setBaby();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!chicken.isValid()) {
                            return;
                        }
                        chicken.getWorld().createExplosion(chicken.getLocation().getX(), chicken.getLocation().getY(), chicken.getLocation().getZ(), 0F, false, false);
                        for (Player nearby : PlayerUtil.getNearbyPlayers(chicken.getLocation(), 3)) {
                            if (nearby.equals(attacker)) {
                                continue;
                            }
                            PlayerUtil.damage(attacker, nearby, PlayerUtil.DamageType.TRUE, 1.2, true);
                            SpiterCompatibility.exemptVelocity(nearby, 20L);
                            Vector direction = nearby.getLocation().toVector().subtract(chicken.getLocation().toVector());
                            if (direction.lengthSquared() > 1.0E-6) {
                                nearby.setVelocity(direction.normalize().multiply(1.4).setY(0.55));
                            }
                        }
                        chicken.remove();
                    }
                }.runTaskLater(ThePit.getInstance(), 15L);
            }
        });
    }
}
