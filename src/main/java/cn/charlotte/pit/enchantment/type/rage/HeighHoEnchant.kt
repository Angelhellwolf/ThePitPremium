package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.util.Utils
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean

@ArmorOnly
class HeighHoEnchant : AbstractEnchantment(), IAttackEntity {
    override fun getEnchantName(): String {
        return "嘿嗬"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "heigh_ho"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7对穿着带有 &b平面镜 &7附魔裤子的玩家额外造成 &c${0.5 * enchantLevel}❤ &7普通伤害"
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
        if (Utils.getEnchantLevel(victim.inventory.leggings, "Mirror") > 0) {
            cn.charlotte.pit.util.combat.CombatContext.addNormalDamage(enchantLevel.toDouble())
        }
    }
}
