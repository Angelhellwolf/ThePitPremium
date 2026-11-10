package cn.charlotte.pit.enchantment.type.dark_normal

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.combat.PlayerLife
import cn.charlotte.pit.util.combat.SecondaryDamage
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID
import net.minecraft.server.v1_8_R3.EnumParticle

@ArmorOnly
class GrimReaperEnchant : AbstractEnchantment(), IPlayerKilledEntity {
    private val pendingWaves = HashMap<UUID, Any>()
    override fun getEnchantName(): String {
        return "死神"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "grim_reaper_enchant"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.DARK_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return ("&7击杀玩家时释放冲击波,对周围8格内所有玩家造成 &c2❤ &7普通伤害"
                + "/s&7同刻多杀合并为一次冲击波,冲击波击杀不再连锁触发"
                + "/s&7遵循护甲、抗性与伤害保护判定"
                + "/s&c击杀获得的金币与经验 -80%"
                + "/s&7Bot 基础收益不受此降幅影响，冲击波正常触发")
    }

    @PlayerOnly
    override fun handlePlayerKilled(
        enchantLevel: Int,
        myself: Player,
        target: Entity,
        coins: AtomicDouble,
        experience: AtomicDouble
    ) {
        coins.set(0.2 * coins.get())
        experience.set(0.2 * experience.get())
        if (SecondaryDamage.isGrimReaperKill(myself, target)) return
        val life = PlayerLife.capture(myself)
        if (pendingWaves.putIfAbsent(myself.uniqueId, life) != null) return
        val origin = myself.location.clone()
        Bukkit.getScheduler().runTask(ThePit.getInstance()) {
            if (!pendingWaves.remove(myself.uniqueId, life) || !PlayerLife.isCurrent(myself, life)) return@runTask
            if (myself.world != origin.world || getItemEnchantLevel(myself.inventory.leggings) <= 0) return@runTask
            PlayerUtil.sendParticle(origin.clone().add(0.0, 1.0, 0.0), EnumParticle.SMOKE_LARGE, 12)
            for (entity in origin.world.getNearbyEntities(origin, 8.0, 8.0, 8.0)) {
                val player = entity as? Player ?: continue
                if (player.uniqueId == myself.uniqueId || !player.isOnline || player.isDead) continue
                if (player.location.distanceSquared(origin) > 64.0) continue
                SecondaryDamage.grimReaper(myself, player)
            }
        }
    }
}
