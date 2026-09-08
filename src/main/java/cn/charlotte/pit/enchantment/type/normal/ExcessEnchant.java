package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

@ArmorOnly
public class ExcessEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "过量";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "excess_enchant";
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
        return "&7持有的治疗物品数量上限 &e+" + enchantLevel + "/s&7(在 &e过度医疗 &7之后生效)";
    }
}
