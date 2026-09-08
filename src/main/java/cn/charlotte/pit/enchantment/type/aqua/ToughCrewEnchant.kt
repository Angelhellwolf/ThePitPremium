package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.item.MythicColor
import cn.charlotte.pit.parm.listener.IPlayerDamaged
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.toMythicItem
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean

@ArmorOnly
class ToughCrewEnchant : AbstractEnchantment(), IPlayerDamaged {
    override fun getEnchantName(): String {
        return "坚韧船员"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "tough_crew"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7周围8格内至少有 &e3 &7名穿着青色神话之甲的玩家时（包含自身），受到的伤害 &9-15%"
    }

    @PlayerOnly
    override fun handlePlayerDamaged(
        enchantLevel: Int,
        myself: Player,
        attacker: Entity?,
        damage: Double,
        finalDamage: AtomicDouble?,
        boostDamage: AtomicDouble,
        cancel: AtomicBoolean?
    ) {
        val aquaCount = PlayerUtil.getNearbyPlayers(myself.location, 8.0).count { player ->
            player.inventory.leggings?.toMythicItem()?.color == MythicColor.AQUA
        }
        if (aquaCount >= 3) {
            boostDamage.addAndGet(-0.15)
        }
    }
}
