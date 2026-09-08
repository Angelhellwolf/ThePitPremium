package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.buff.impl.HealPoisonDeBuff
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.parm.listener.IPlayerShootEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import java.util.concurrent.atomic.AtomicBoolean


/**
 * @Creator Misoryan
 * @Date 2021/5/8 19:39
 */
@ArmorOnly
@AutoRegister
class ReallyToxicEnchant : AbstractEnchantment(), IAttackEntity, IPlayerShootEntity, Listener {
    override fun getEnchantName(): String {
        return "不愈"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "really_toxic"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return ("&7每次命中施加2层 &a不愈之毒 &f(00:30) &7,最高叠加" + (getMaxStacks(enchantLevel) * 2) + "层."
                + "/s&7每层降低恢复量 &a1% &7，各层独立到期")
    }

    @PlayerOnly
    override fun handleAttackEntity(
        enchantLevel: Int,
        attacker: Player,
        target: Entity,
        damage: Double,
        finalDamage: AtomicDouble,
        boostDamage: AtomicDouble,
        cancel: AtomicBoolean
    ) {
        applyToxic(enchantLevel, target as Player)
    }

    @PlayerOnly
    override fun handleShootEntity(
        enchantLevel: Int,
        attacker: Player,
        target: Entity,
        damage: Double,
        finalDamage: AtomicDouble,
        boostDamage: AtomicDouble,
        cancel: AtomicBoolean
    ) {
        applyToxic(enchantLevel, target as Player)
    }

    private fun applyToxic(enchantLevel: Int, targetPlayer: Player) {
        val maxStacks = getMaxStacks(enchantLevel) * 2
        val current = buff.getPlayerBuffData(targetPlayer).tier
        if (current >= maxStacks) {
            return
        }
        buff.stackBuff(targetPlayer, 30 * 1000L, minOf(2, maxStacks - current))
    }

    private fun getMaxStacks(enchantLevel: Int): Int {
        return when (enchantLevel) {
            1 -> 10
            2 -> 15
            else -> 20
        }
    }

    companion object {
        val buff = HealPoisonDeBuff()
    }
}
