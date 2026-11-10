package cn.charlotte.pit.enchantment.type.ragerare

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.BotEnchantPolicy
import cn.charlotte.pit.util.cooldown.Cooldown
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.metadata.FixedMetadataValue

@AutoRegister
@ArmorOnly
class Regularity : AbstractEnchantment(), Listener {
    override fun getEnchantName(): String {
        return "狂暴连击"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "regularity"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE_RARE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7当近战伤害低于&c${a(enchantLevel)}❤ &7时/s&7将在0.25秒后追加一次普通攻击./s&7追加攻击的基础伤害为本次的&c${
            b(
                enchantLevel
            )
        }%&7，不重复触发武器效果。/s&7对同一目标1秒冷却"
    }

    fun a(enchantLevel: Int): Double {
        val firewellCode = ThePit.getInstance().pitConfig.code
        return if (firewellCode == "5330151b-e2ff-42dc-04c8-4eb3e20c4c95") {
            when (enchantLevel) {
                1 -> 0.7
                2 -> 1.7
                else -> 1.9
            }
        } else {
            when (enchantLevel) {
                1 -> 0.1
                2 -> 1.3
                else -> 1.5
            }
        }
    }

    fun b(enchantLevel: Int): Int {
        return when (enchantLevel) {
            1 -> 40
            2 -> 45
            else -> 60
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)

    fun damage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager
        if (attacker !is Player) return

        val victim = event.entity
        if (victim !is Player) return
        if (cn.charlotte.pit.util.combat.SecondaryDamage.isHit(event) || event.finalDamage <= 0) return

        if (!BotEnchantPolicy.effectAllowed(attacker, victim) || PlayerUtil.shouldIgnoreEnchant(attacker, victim)) {
            return
        }

        val level = ThePit.api.getItemEnchantLevel(attacker.inventory.leggings, "regularity")
        if (level < 1) return


        if (event.finalDamage < a(level) * 2
        ) {
            val metadata = victim.getMetadata("regularity_cooldown")
            metadata.firstOrNull()?.asLong()?.let {
                if (System.currentTimeMillis() < it) {
                    return
                }
            }

            if (!victim.isDead) {
                val boost = b(level) * 0.01
                val followupDamage = event.damage * boost
                val life = cn.charlotte.pit.util.combat.PlayerLife.capture(victim)
                victim.setMetadata("regularity_cooldown", FixedMetadataValue(ThePit.getInstance(), System.currentTimeMillis() + 1000L))

                Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), {
                    if (event.isCancelled || !cn.charlotte.pit.util.combat.PlayerLife.isCurrent(victim, life)
                        || attacker.world != victim.world || !BotEnchantPolicy.effectAllowed(attacker, victim)
                        || PlayerUtil.shouldIgnoreEnchant(attacker, victim)) return@runTaskLater
                    victim.noDamageTicks = 0
                    cn.charlotte.pit.util.combat.SecondaryDamage.reflection(attacker, victim, followupDamage)
                }, 5L)
            }
        }
    }
}
