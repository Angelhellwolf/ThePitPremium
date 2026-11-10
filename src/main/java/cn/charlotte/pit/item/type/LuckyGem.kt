package cn.charlotte.pit.item.type

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.item.AbstractPitItem
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.util.ParticleBuilder
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.item.ItemBuilder
import cn.charlotte.pit.util.item.ItemUtil
import net.minecraft.server.v1_8_R3.EnumParticle
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitTask
import java.util.UUID


/**
 * @author Araykal
 * @since 2025/2/6
 */
@AutoRegister
class LuckyGem : AbstractPitItem(), Listener {
    companion object {
        private const val EFFECT_DURATION_MS = 60_000L
        private const val PARTICLE_PERIOD_TICKS = 5L
    }

    private val luckyUntil = HashMap<UUID, Long>()
    private var effectTask: BukkitTask? = null

    override fun getInternalName(): String {
        return "lucky_gem"
    }

    override fun getItemDisplayName(): String {
        return "§e幸运宝石"
    }

    override fun getItemDisplayMaterial(): Material {
        return Material.NETHER_STAR
    }

    override fun toItemStack(): ItemStack {
        return ItemBuilder(itemDisplayMaterial).name(itemDisplayName).lore(
            mutableListOf(
                "&7死亡时保留",
                "",
                "&7为自身添加&e幸运,",
                "&7持续60秒,",
                "&e&l幸运！&7大幅度提升附魔稀有概率",
                "",
                "&e右键使用"
            )
        ).internalName(
            internalName
        ).canSaveToEnderChest(true).canTrade(false).deathDrop(false).shiny().build()
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        if ("lucky_gem" == ItemUtil.getInternalName(event.item)) {
            event.isCancelled = true
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            event.player.sendMessage(CC.translate("&e&l幸运! &7自身运气已大幅度提升。"))
            PlayerUtil.takeOneItemInHand(event.player)
            event.player.setMetadata("lucky", FixedMetadataValue(ThePit.getInstance(), true))
            luckyUntil[event.player.uniqueId] = System.currentTimeMillis() + EFFECT_DURATION_MS
            ensureEffectTask()

        }
    }

    private fun ensureEffectTask() {
        if (effectTask != null) {
            return
        }

        effectTask = Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(), Runnable {
            val now = System.currentTimeMillis()
            val iterator = luckyUntil.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val player = Bukkit.getPlayer(entry.key)
                if (player == null || !player.isOnline) {
                    iterator.remove()
                    continue
                }
                if (now >= entry.value) {
                    iterator.remove()
                    player.sendMessage(CC.translate("&e&l幸运! &7自身运气已降低。"))
                    player.removeMetadata("lucky", ThePit.getInstance())
                    continue
                }

                ParticleBuilder(
                    player.location.add(0.0, 2.0, 0.0),
                    EnumParticle.VILLAGER_HAPPY
                ).setVelocity(0.5f).setCount(5).play()
            }

            if (luckyUntil.isEmpty()) {
                effectTask?.cancel()
                effectTask = null
            }
        }, 0L, PARTICLE_PERIOD_TICKS)
    }

    override fun loadFromItemStack(item: ItemStack) {
    }
}
