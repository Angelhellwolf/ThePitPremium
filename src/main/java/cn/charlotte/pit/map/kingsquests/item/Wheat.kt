package cn.charlotte.pit.map.kingsquests.item

import cn.charlotte.pit.item.AbstractPitItem
import cn.charlotte.pit.util.countItem
import cn.charlotte.pit.util.item.ItemBuilder
import cn.charlotte.pit.util.takeItem
import cn.charlotte.pit.parm.AutoRegister
import org.bukkit.CropState
import org.bukkit.Effect
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.material.Crops
import kotlin.random.Random

@AutoRegister
object Wheat: AbstractPitItem(), Listener {

    private const val EXTRA_GROWTH_STAGES = 1

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGrow(event: BlockGrowEvent) {
        if (event.block.type != Material.CROPS || event.newState.type != Material.CROPS) {
            return
        }

        // A vanilla growth tick advances wheat by one stage. Advance the event's
        // resulting state once more so wheat grows at roughly twice the normal
        // speed without changing the world's randomTickSpeed gamerule.
        val crops = event.newState.data as? Crops ?: return
        val growthStates = CropState.values()
        val acceleratedStage = minOf(
            CropState.RIPE.ordinal,
            crops.state.ordinal + EXTRA_GROWTH_STAGES
        )
        crops.state = growthStates[acceleratedStage]
        event.newState.data = crops
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        if (block.type == Material.CROPS) {
            event.isCancelled = true
            val state = block.state

            val crops = state.data as Crops
            if (crops.state != CropState.RIPE) return

            crops.state = CropState.SEEDED
            state.update()

            player.inventory.addItem(
                toItemStack()
            )
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val player = event.player

        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        if (block.type != Material.WORKBENCH) {
            return
        }

        val count = player.inventory.countItem {
            it.type == Material.WHEAT
        }

        if (count >= 64) {
            val result = count / 64
            val cost = result * 64
            player.inventory.takeItem(amount = cost) {
                it.type == Material.WHEAT
            }

            repeat(10) {
                val particlePlayLoc = block.location.clone()
                    .add(Random.nextDouble(-0.5, 0.5), Random.nextDouble(-0.5, 0.5), Random.nextDouble(-0.5, 0.5))
                particlePlayLoc.world.playEffect(particlePlayLoc, Effect.HAPPY_VILLAGER, 1, 1)
            }

            player.inventory.addItem(PackagedBale.toItemStack().apply {
                amount = result
            })

            player.playSound(block.location, Sound.ORB_PICKUP, 1f, 1f)
        }

    }

    override fun getInternalName(): String {
        return "wheat"
    }

    override fun getItemDisplayName(): String {
        return "&a小麦"
    }

    override fun getItemDisplayMaterial(): Material {
        return Material.WHEAT
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
                "&7采收成熟小麦获得；每 64 个右键工作台",
                "&7合成为一个干草块，或右键熔炉烘焙面包"
            )
            .build()
    }

    override fun loadFromItemStack(item: ItemStack?) {

    }




}
