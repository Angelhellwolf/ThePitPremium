package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

@ArmorOnly
public class LodbrokEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "洛德布洛克";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "lodbrok_enchant";
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
        int chance;
        switch (enchantLevel) {
            case 1:
                chance = 40;
                break;
            case 2:
                chance = 55;
                break;
            default:
                chance = 75;
                break;
        }
        return ("&7击杀掉落护甲的几率由 &e30% &7提升至 &e" + chance + "%") + "/s" + "&7仅影响真人死亡时的护甲掉落（排除 Bot）";
    }
}
