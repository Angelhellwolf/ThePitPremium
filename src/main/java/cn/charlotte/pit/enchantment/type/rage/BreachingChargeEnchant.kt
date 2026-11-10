package cn.charlotte.pit.enchantment.type.rage

import cn.charlotte.pit.util.combat.CombatContext

import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.IActionDisplayEnchant
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.parm.listener.IPlayerShootEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.atomic.AtomicBoolean


/**
 * @Creator Misoryan
 * @Date 2021/5/8 19:13
 */
@ArmorOnly
class BreachingChargeEnchant : AbstractEnchantment(), IAttackEntity, IPlayerShootEntity,
    IActionDisplayEnchant {
    override fun getEnchantName(): String {
        return "强力击: 违约金"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "breaching_charge"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RAGE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7每 &e" + getRequiredHits(enchantLevel) + " &7次攻击清空命中玩家的 &3抗性提升 &7效果"
    }

    private fun getRequiredHits(enchantLevel: Int): Int {
        return when (enchantLevel) {
            1 -> 4
            2 -> 3
            else -> 2
        }
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
        val targetPlayer = target as Player
        if (CombatContext.meleeHits(attacker) % getRequiredHits(enchantLevel) == 0) {
            targetPlayer.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE)
        }
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
        val targetPlayer = target as Player
        if (CombatContext.bowHits(attacker) % getRequiredHits(enchantLevel) == 0) {
            targetPlayer.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE)
        }
    }

    override fun getText(level: Int, player: Player): String {
        val hit = if (player.itemInHand != null && player.itemInHand.type == Material.BOW) {
            CombatContext.bowHits(player)
        } else {
            CombatContext.meleeHits(player)
        }
        val require = getRequiredHits(level)
        return if (hit % require == 0) "&a&l✔" else "&e&l" + (require - hit % require)
    }
}