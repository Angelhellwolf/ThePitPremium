package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/17 18:26
 */
@WeaponOnly
@BowOnly
public class DiamondBreakerEnchant extends AbstractEnchantment implements IAttackEntity, IPlayerShootEntity {
    private static final int MAX_BOOST_PERCENT = 25;

    @Override
    public String getEnchantName() {
        return "钻石破坏者";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "diamond_breaker";
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
        return "&7目标每穿着一件 &b钻石护甲&7，造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%"
                + "/s&7总伤害加成最多 &c+" + MAX_BOOST_PERCENT + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        return Math.max(0, Math.min(3, enchantLevel)) * 5;
    }

    private int countDiamondArmor(Player victim) {
        int pieces = 0;
        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor == null) continue;
            switch (armor.getType()) {
                case DIAMOND_HELMET:
                case DIAMOND_CHESTPLATE:
                case DIAMOND_LEGGINGS:
                case DIAMOND_BOOTS:
                    pieces++;
                    break;
                default:
                    break;
            }
        }
        return pieces;
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        boostDamage.getAndAdd(Math.min(MAX_BOOST_PERCENT, countDiamondArmor((Player) target) * getBoostPercent(enchantLevel)) * 0.01);
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        boostDamage.getAndAdd(Math.min(MAX_BOOST_PERCENT, countDiamondArmor((Player) target) * getBoostPercent(enchantLevel)) * 0.01);
    }
}
