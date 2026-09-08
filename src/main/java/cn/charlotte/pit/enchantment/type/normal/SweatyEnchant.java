package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

@WeaponOnly
@BowOnly
@ArmorOnly
public class SweatyEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "挥汗如雨";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "sweaty_enchant";
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
        return ("&7连杀经验加成 &b+" + (enchantLevel * 20) + "%"
                + (enchantLevel >= 2 ? "/s&7击杀经验上限 &b+" + (enchantLevel >= 3 ? 100 : 50) : "")) + "/s" + "&7以上金币或经验加成排除 Bot";
    }
}
