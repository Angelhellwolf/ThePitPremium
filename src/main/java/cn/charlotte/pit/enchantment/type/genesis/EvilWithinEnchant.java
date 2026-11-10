package cn.charlotte.pit.enchantment.type.genesis;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/16 19:47
 */
public class EvilWithinEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "恶灵附身";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "evil_within_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.GENESIS;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7穿戴世界末日之靴时，你的附魔可对抗目标的无尽黑暗"
                + "/s&7无法抵御自身无尽黑暗、毒液沉默或沉沦任务";
    }
}
