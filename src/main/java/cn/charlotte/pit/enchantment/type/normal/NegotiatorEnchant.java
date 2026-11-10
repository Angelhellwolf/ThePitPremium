package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

@ArmorOnly
public class NegotiatorEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "谈判专家";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "negotiator_enchant";
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
        int percent;
        switch (enchantLevel) {
            case 1:
                percent = 30;
                break;
            case 2:
                percent = 60;
                break;
            default:
                percent = 100;
                break;
        }
        return "&7完成合约时获得的硬币 &6+" + percent + "%";
    }
}
