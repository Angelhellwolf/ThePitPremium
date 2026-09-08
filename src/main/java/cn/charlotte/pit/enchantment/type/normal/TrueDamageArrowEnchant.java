package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.text.DecimalFormat;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/30 15:33
 */
@BowOnly
public class TrueDamageArrowEnchant extends AbstractEnchantment implements cn.charlotte.pit.parm.listener.IPlayerShootEntity {
    final DecimalFormat format = new DecimalFormat("0");

    public static double getTrueDamageRate(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 0.25;
            case 2:
                return 0.35;
            default:
                return 0.45;
        }
    }

    public static double getBonusTrueDamage(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 0;
            case 2:
                return 0.5;
            default:
                return 1.0;
        }
    }

    @Override
    public String getEnchantName() {
        return "真实一击: 箭矢";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "true_damage_arrow";
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

        return "&7弓箭命中将伤害量的 &c" + format.format(100 * getTrueDamageRate(enchantLevel)) + "% &7转化为&f真实&7伤害"
                + (enchantLevel >= 2 ? "/s&7并额外造成 &c" + (enchantLevel >= 3 ? 0.5 : 0.25) + "❤ &7的&f真实&7伤害" : "");
    }

    @Override
    @cn.charlotte.pit.enchantment.param.event.PlayerOnly
    @cn.charlotte.pit.parm.type.BowOnly
    public void handleShootEntity(int level, Player attacker, org.bukkit.entity.Entity target, double damage,
            com.google.common.util.concurrent.AtomicDouble trueDamage,
            com.google.common.util.concurrent.AtomicDouble boost, java.util.concurrent.atomic.AtomicBoolean cancel) {
        if (cancel.get() || !(target instanceof Player)) return;
        cn.charlotte.pit.util.combat.CombatContext.convertNormalToTrue(getTrueDamageRate(level));
        trueDamage.addAndGet(getBonusTrueDamage(level));
    }
}
