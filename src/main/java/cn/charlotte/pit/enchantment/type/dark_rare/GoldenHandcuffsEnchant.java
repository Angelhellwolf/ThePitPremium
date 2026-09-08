package cn.charlotte.pit.enchantment.type.dark_rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/15 20:43
 */
@ArmorOnly
public class GoldenHandcuffsEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "金手铐";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "golden_handcuffs_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7有效真人击杀触发赏金增长时，将当次增长的 &680% &7分给同场其他真人"
                + "/s&7自己保留 &620% &7，所有人均遵守赏金上限"
                + "/s&7没有合格玩家或他人达到上限时，剩余部分归自己"
                + "/s&7当次总增长不变；排除 Bot";
    }
}
