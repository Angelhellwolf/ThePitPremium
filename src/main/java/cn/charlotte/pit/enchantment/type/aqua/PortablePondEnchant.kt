package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.parm.listener.IPlayerRespawn
import cn.charlotte.pit.parm.listener.ITickTask
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.inventory.InventoryUtil
import cn.charlotte.pit.util.item.ItemBuilder
import cn.charlotte.pit.util.item.ItemUtil
import cn.charlotte.pit.runnable.ClearRunnable
import java.util.concurrent.TimeUnit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.inventory.ItemStack

@ArmorOnly
@AutoRegister
class PortablePondEnchant : AbstractEnchantment(), ITickTask, IPlayerRespawn, Listener {

    override fun getEnchantName(): String {
        return "便携水池"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "portable_pond"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_NORMAL
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7装备时获得可使用 &e4 &7次的水桶，死亡复活后补满/s&7仅可在允许放置的战斗区域使用，水持续120秒/s&7耗尽后变为空桶，不能重新装水"
    }

    private fun bucket(uses: Int): ItemStack {
        val material = if (uses > 0) Material.WATER_BUCKET else Material.BUCKET
        return ItemBuilder(material)
            .name("&b便携水桶")
            .internalName("portable_pond_bucket")
            .canDrop(false)
            .canTrade(false)
            .canSaveToEnderChest(false)
            .deathDrop(false)
            .removeOnJoin(true)
            .changeNbt("pond_uses", uses.toString())
            .lore(listOf("&7剩余使用次数: &e$uses"))
            .build()
    }

    override fun handle(enchantLevel: Int, player: Player) {
        if (InventoryUtil.getAmountOfItem(player, "portable_pond_bucket") == 0) {
            player.inventory.addItem(bucket(4))
        }
    }

    override fun loopTick(enchantLevel: Int): Int {
        return 40
    }

    override fun handleRespawn(enchantLevel: Int, myself: Player) {
        InventoryUtil.removeItem(myself, "portable_pond_bucket", InventoryUtil.getAmountOfItem(myself, "portable_pond_bucket"))
        myself.inventory.addItem(bucket(4))
    }

    @EventHandler(ignoreCancelled = true)
    fun onEmpty(event: PlayerBucketEmptyEvent) {
        if (ItemUtil.getInternalName(event.player.itemInHand) != "portable_pond_bucket") {
            return
        }
        val uses = ItemUtil.getItemStringData(event.player.itemInHand, "pond_uses")?.toIntOrNull() ?: 0
        event.isCancelled = true
        if (getItemEnchantLevel(event.player.inventory.leggings) <= 0 || !isEffectActive(event.player, null)) return
        val target = event.blockClicked.getRelative(event.blockFace)
        if (uses <= 0 || target.type != Material.AIR) return
        target.setType(Material.WATER, false)
        ClearRunnable.getClearRunnable().placeBlock(target.location, Cooldown(120, TimeUnit.SECONDS))
        event.player.itemInHand = bucket(uses - 1)
    }

    @EventHandler
    fun onFill(event: PlayerBucketFillEvent) {
        if (ItemUtil.getInternalName(event.player.itemInHand) == "portable_pond_bucket") {
            event.isCancelled = true
        }
    }
}
