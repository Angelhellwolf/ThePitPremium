package cn.charlotte.pit.enchantment.type.rare

import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean

@ArmorOnly
class ComboPowerfulEnchant: AbstractEnchantment(), IAttackEntity {
    override fun getEnchantName(): String {
        return "强力击: 蓄力"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "combo_powerful"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RARE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        val percent = when (enchantLevel) { 2 -> 95; 3 -> 110; else -> 75 }
        return "&7脱战且15秒未受玩家攻击时，下次近战伤害 &c+$percent%/s" +
                "&7仍在战斗且25秒未受玩家攻击时，下次近战普通伤害转为真实伤害/s" +
                "&7触发后重新蓄力"
    }

    private val lastActivation = HashMap<java.util.UUID, Long>()

    override fun handleAttackEntity(
        enchantLevel: Int,
        attacker: Player,
        target: Entity,
        damage: Double,
        finalDamage: AtomicDouble,
        boostDamage: AtomicDouble,
        cancel: AtomicBoolean
    ) {
        if (cancel.get() || target !is Player || damage <= 0) return
        val profile = PlayerProfile.getPlayerProfileByUuid(attacker.uniqueId)
        val inCombat = !profile.combatTimer.hasExpired()
        val now = System.currentTimeMillis()
        val since = maxOf(profile.lastDamageAt, profile.lastLoginTime, lastActivation[attacker.uniqueId] ?: 0L)
        if (since <= 0 || now - since < (if (inCombat) 25_000L else 15_000L)) return
        if (inCombat) {
            cn.charlotte.pit.util.combat.CombatContext.convertNormalToTrue(1.0)
        } else {
            val boost = when (enchantLevel) { 2 -> 0.95; 3 -> 1.10; else -> 0.75 }
            boostDamage.addAndGet(boost)
        }
        cn.charlotte.pit.util.combat.CombatContext.afterAccepted { lastActivation[attacker.uniqueId] = now }
    }
}
