package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class GrasshopperEnchant extends AbstractEnchantment implements IAttackEntity {
    @Override
    public String getEnchantName() {
        return "蚱蜢";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "grasshopper_enchant";
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
        return "&7站在草方块上时近战伤害 &c+" + getBoostPercent(enchantLevel) + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 9;
            default:
                return 15;
        }
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Block block = attacker.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (block.getType() == Material.GRASS) {
            boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
        }
    }
}
