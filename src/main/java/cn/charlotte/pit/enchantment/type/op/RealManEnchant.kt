package cn.charlotte.pit.enchantment.type.op

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.ITickTask
import cn.charlotte.pit.util.cooldown.Cooldown
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue

class RealManEnchant: AbstractEnchantment() , ITickTask {
    companion object {
        @JvmStatic
        fun hasRealMan(player: Player): Boolean {
            return (player.getMetadata("real_man").firstOrNull()?.asLong() ?: Long.MIN_VALUE) >= System.currentTimeMillis()
        }
    }

    override fun getEnchantName(): String {
        return "真男人"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "real_man"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.OP
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7周围7格内的玩家（包括自身）无法触发天赋和连杀效果/s&7离开范围3秒后恢复，已有药水效果不会立即移除"
    }

    override fun handle(enchantLevel: Int, player: Player) {
        cn.charlotte.pit.util.PlayerUtil.getNearbyPlayers(player.location, 7.0)
            .forEach {
                it.setMetadata("real_man", FixedMetadataValue(ThePit.getInstance(), System.currentTimeMillis() + 3 * 1000L))
            }
    }

    override fun loopTick(enchantLevel: Int): Int {
        return 20
    }
}
