package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.item.MythicColor
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.toMythicItem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Vector

@ArmorOnly
@AutoRegister
class UniteEnchant : AbstractEnchantment(), Listener {
    override fun getEnchantName(): String {
        return "团结"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "unite"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7移动时让身旁穿着青色神话之甲的玩家向外让开"
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!isEffectActive(player, null)) return
        if (player.inventory.leggings?.toMythicItem()?.enchantments?.keys?.any { it.nbtName == "unite" } != true) {
            return
        }
        for (nearby in player.getNearbyEntities(0.6, 1.0, 0.6)) {
            if (nearby is Player && nearby.inventory.leggings?.toMythicItem()?.color == MythicColor.AQUA) {
                val from = player.location.toVector()
                val to = nearby.location.toVector()
                val push = to.subtract(from)
                if (push.lengthSquared() < 0.0001) {
                    continue
                }
                push.normalize().multiply(0.35)
                push.y = 0.0
                nearby.velocity = Vector(push.x, nearby.velocity.y, push.z)
            }
        }
    }
}
