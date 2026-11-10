package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.util.cooldown.Cooldown

@ArmorOnly
class SybilEnchant : AbstractEnchantment() {
    override fun getEnchantName(): String {
        return "西比尔"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "sybil"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7你自己的附近人数判定额外计入 &e${enchantLevel + 1} &7名玩家/s&7仅影响角斗士、'不是'角斗士、独处和天坑MBA/s&7不影响鲸鲨或其他玩家的人数判定"
    }
}
