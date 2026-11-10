package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.cooldown.Cooldown
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

@AutoRegister
@ArmorOnly
class SingularityEnchant: AbstractEnchantment(), Listener {
    override fun getEnchantName(): String {
        return "奇异"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "singularity"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RARE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7单次最多受到&c ${
            when(enchantLevel) {
                1 -> 2.5
                2 -> 2
                else -> 1.5
            }
        }❤ &7普通伤害（真实与必中伤害不受此上限影响）"
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity

        if (victim !is Player) return
        if (cn.charlotte.pit.util.combat.SecondaryDamage.isLethalTrueDamage(event)) return
        if (cn.charlotte.pit.listener.CombatListener.INSTANCE?.hasLethalExtraDamage(event) == true) return
        val source = cn.charlotte.pit.util.combat.CombatContext.playerSource(event.damager)
        if (if (source != null) cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant(victim, source)
            else cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant(victim)) return


        val level = ThePit.api.getItemEnchantLevel(victim.inventory.leggings, "singularity")
        if (level > 0 && event.finalDamage > 0) {
            val cap = when (level) {
                1 -> 5.0
                2 -> 4.0
                else -> 3.0
            }
            if (event.finalDamage > cap) {
                event.damage = event.damage * (cap / event.finalDamage)
            }
        }
    }
}
