package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.PlayerUtil
import org.bukkit.entity.FishHook
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

@ArmorOnly
@AutoRegister
class GrandmasterEnchant: AbstractEnchantment(), Listener{
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (event.isCancelled || damager !is FishHook || event.entity !is Player) return
        val shooter = damager.shooter as? Player ?: return
        val target = event.entity as Player
        if (shooter.uniqueId == target.uniqueId || PlayerUtil.shouldIgnoreEnchant(shooter, target)) return
        if (getItemEnchantLevel(shooter.inventory.leggings) <= 0) return
        event.damage = event.damage.coerceAtLeast(0.0) + 1.0
    }

    override fun getEnchantName(): String {
        return "特级渔夫"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "grandmaster"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7使用鱼竿命中玩家时额外造成 &c0.5❤ &7普通伤害/s&7此伤害受护甲与抗性效果减免"
    }
}
