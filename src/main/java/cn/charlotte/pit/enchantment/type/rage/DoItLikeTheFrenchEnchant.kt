package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean

@ArmorOnly
class DoItLikeTheFrenchEnchant : AbstractEnchantment(), IAttackEntity {
    override fun getEnchantName(): String {
        return "法式作风"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "do_it_like_the_french"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7对穿着 &6金头盔 &7的玩家额外造成 &c${0.5 * enchantLevel}❤ &f真实&7伤害"
    }

    @PlayerOnly
    override fun handleAttackEntity(
        enchantLevel: Int,
        attacker: Player?,
        target: Entity,
        damage: Double,
        finalDamage: AtomicDouble,
        boostDamage: AtomicDouble?,
        cancel: AtomicBoolean?
    ) {
        val victim = target as Player
        val helmet = victim.inventory.helmet
        if (helmet != null && (helmet.type == Material.GOLD_HELMET || helmet.type.name.contains("GOLD"))) {
            finalDamage.addAndGet(enchantLevel.toDouble())
        }
    }
}
