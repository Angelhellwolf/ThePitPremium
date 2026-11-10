package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.SpiterCompatibility
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.toMythicItem
import org.bukkit.entity.FishHook
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

@ArmorOnly
@AutoRegister
class HeadOnBlow : AbstractEnchantment(), Listener {
    override fun getEnchantName(): String {
        return "当头一棒"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "rod_back"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7使用鱼竿命中玩家时击退力度增加"
    }

    @EventHandler(ignoreCancelled = true)
    fun onHook(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is FishHook && event.entity is Player && damager.shooter is Player) {
            val shooter = damager.shooter as Player
            if (!isEffectActive(shooter, event.entity as Player)) return
            if (shooter.inventory.leggings?.toMythicItem()?.enchantments?.any { it.key.nbtName == "rod_back" } == true) {
                val victim = event.entity as Player
                SpiterCompatibility.exemptVelocity(victim, 20L)
                val direction = victim.location.toVector().subtract(shooter.location.toVector())
                if (direction.lengthSquared() > 1.0E-6) {
                    victim.velocity = direction.normalize().multiply(0.8).setY(0.35)
                }
            }
        }
    }
}
