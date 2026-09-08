package cn.charlotte.pit.enchantment.type.rare

import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.BowOnly
import cn.charlotte.pit.enchantment.param.item.WeaponOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.enchantment.type.ragerare.ThinkOfThePeopleEnchant
import cn.charlotte.pit.parm.listener.IAttackEntity
import cn.charlotte.pit.parm.listener.IPlayerShootEntity
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.cooldown.Cooldown
import com.google.common.util.concurrent.AtomicDouble
import net.minecraft.server.v1_8_R3.BlockPosition
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent
import org.bukkit.Sound
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/29 21:23
 */
@WeaponOnly
@BowOnly
class ExecutionerEnchant : AbstractEnchantment(), IAttackEntity, IPlayerShootEntity {
    override fun getEnchantName(): String {
        return "处决"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "executioner"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.RARE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        val hearts = 0.5 * enchantLevel + 0.5
        return ("&7攻击玩家时若当次攻击使玩家的生命值低于 &c" + hearts + "❤ &7,"
                + "/s&7则额外造成 &c" + hearts + "❤ &7的&f真实&7伤害")
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
        tryExecute(enchantLevel, attacker, target as Player, damage, finalDamage, boostDamage)
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
        tryExecute(enchantLevel, attacker, target as Player, damage, finalDamage, boostDamage)
    }

    private fun tryExecute(
        enchantLevel: Int,
        attacker: Player,
        targetPlayer: Player,
        damage: Double,
        finalDamage: AtomicDouble,
        boostDamage: AtomicDouble
    ) {
        for (nearbyPlayers in PlayerUtil.getNearbyPlayers(targetPlayer.location, 10.0)) {
            if (nearbyPlayers.inventory.leggings != null && thinkOfThePeople.isItemHasEnchant(nearbyPlayers.inventory.leggings)) {
                val level = thinkOfThePeople.getItemEnchantLevel(nearbyPlayers.inventory.leggings)
                if (level > 1) {
                    boostDamage.getAndAdd(level * -0.1 + 0.1)
                }
                return
            }
        }
        val executeHealth = enchantLevel + 1.0
        val effect = Runnable {
            attacker.playSound(attacker.location, Sound.VILLAGER_DEATH, 1f, 0.5f)
            val deathLoc = targetPlayer.location
            val packetA = PacketPlayOutWorldEvent(
                2001,
                BlockPosition(deathLoc.blockX, deathLoc.blockY, deathLoc.blockZ),
                152,
                false
            )
            val packetB = PacketPlayOutWorldEvent(
                2001,
                BlockPosition(deathLoc.blockX, deathLoc.blockY - 1, deathLoc.blockZ),
                152,
                false
            )
            val connection = (attacker as CraftPlayer).handle.playerConnection
            connection.sendPacket(packetA)
            connection.sendPacket(packetB)
        }
        if (cn.charlotte.pit.util.combat.CombatContext.queueFinisher(executeHealth, executeHealth, effect)) return
        val incoming = Math.max(damage, 0.0) * Math.max(0.2, boostDamage.get()) + finalDamage.get()
        if (targetPlayer.health - incoming < executeHealth) {
            finalDamage.getAndAdd(executeHealth)
            effect.run()
        }
    }

    companion object {
        private val thinkOfThePeople = ThinkOfThePeopleEnchant()
    }
}
