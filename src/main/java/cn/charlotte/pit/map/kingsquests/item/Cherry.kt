package cn.charlotte.pit.map.kingsquests.item

import cn.charlotte.pit.item.AbstractPitItem
import cn.charlotte.pit.config.NewConfiguration
import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.map.kingsquests.CherryLuck
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.item.ItemBuilder
import cn.charlotte.pit.util.item.ItemUtil
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

object Cherry: AbstractPitItem(), Listener {
    // RIGHT_CLICK_AIR may be pre-cancelled by vanilla at full hunger; custom food still works.
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEat(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (item.type != Material.APPLE || ItemUtil.getInternalName(item) != internalName) return
        event.isCancelled = true
        val player = event.player
        val profile = PlayerProfile.getPlayerProfileByUuid(player.uniqueId)
        if (!profile.isLoaded || profile.isBot || profile.isTempInvUsing || player.gameMode == GameMode.SPECTATOR) return
        val held = player.itemInHand ?: return
        if (held.amount <= 0 || held.type != Material.APPLE || ItemUtil.getInternalName(held) != internalName) return

        PlayerUtil.takeOneItemInHand(player)
        CherryLuck.refresh(profile, System.currentTimeMillis())
        player.playSound(player.location, Sound.EAT, 1f, 1f)
        player.sendMessage(CC.translate("&d樱桃！&7幸运值变为 &a1&7，持续 &e${durationText()}&7。"))
        player.sendMessage(CC.translate("&7稀有附魔概率略微提高；再次食用刷新时间，不叠加。"))
    }

    private fun durationText(): String {
        val seconds = NewConfiguration.cherryLuckDurationSeconds
        return if (seconds % 60 == 0) "${seconds / 60} 分钟" else "$seconds 秒"
    }

    override fun getInternalName(): String {
        return "cherry"
    }

    override fun getItemDisplayName(): String {
        return "&d樱桃"
    }

    override fun getItemDisplayMaterial(): Material {
        return Material.APPLE
    }

    override fun toItemStack(): ItemStack {
        return ItemBuilder(itemDisplayMaterial)
            .internalName(internalName)
            .canDrop(true)
            .canTrade(true)
            .deathDrop(false)
            .canSaveToEnderChest(true)
            .name(itemDisplayName)
            .lore(
                "&7死亡时保留",
                "&7手持右键食用，获得 &a1 &7点幸运值",
                "&7持续 &e${durationText()}&7，稀有附魔概率略微提高",
                "&7再次食用刷新时间，效果不叠加",
                "&7也可交给烘焙大师制作迷你蛋糕"
            )
            .build()
    }

    override fun loadFromItemStack(item: ItemStack?) {

    }
}
