package cn.charlotte.pit.enchantment.type.normal

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.event.PlayerOnly
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.combat.CombatPotions
import cn.charlotte.pit.util.combat.PlayerLife
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/25 15:11
 */
@ArmorOnly
class ElectrolytesEnchant : AbstractEnchantment(), IPlayerKilledEntity {
    override fun getEnchantName(): String {
        return "电解质"
    }

    override fun getMaxEnchantLevel(): Int {
        return 3
    }

    override fun getNbtName(): String {
        return "electrolytes_enchant"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return ("&7击杀玩家后延长已有 &b速度 I &7效果 &e" + (enchantLevel * 2) + " 秒"
                + "/s&7速度 II 及以上延长时间减半"
                + "/s&7仅命中而未击杀时不会延长速度")
    }

    @PlayerOnly
    override fun handlePlayerKilled(
        enchantLevel: Int,
        myself: Player,
        target: Entity?,
        coins: AtomicDouble?,
        experience: AtomicDouble?
    ) {
        if (enchantLevel <= 0 || target !is Player) return
        val life = PlayerLife.capture(myself)
        // Read after the entire kill callback chain, including newly granted speed.
        Bukkit.getScheduler().runTask(ThePit.getInstance()) {
            if (!PlayerLife.isCurrent(myself, life) || PlayerUtil.shouldIgnoreEnchant(myself)) return@runTask
            if (getItemEnchantLevel(myself.inventory.leggings) <= 0) return@runTask
            CombatPotions.extendSpeed(myself, enchantLevel)
        }
    }
}
