package cn.charlotte.pit.runnable

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.util.chat.ActionBarUtil
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.toMythicItem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object ActionBarDisplayRunnable {

    private val enchants by lazy {
        ThePit.getInstance().enchantmentFactor.actionDisplayEnchants
    }

    fun start() {
        // Use Bukkit scheduler instead of Java ExecutorService to ensure
        // packets are sent on the main thread, avoiding ViaVersion/PacketEvents
        // race conditions that kick 1.21 clients with "Internal Error".
        Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(), Runnable {
            val currentTime = System.currentTimeMillis()

            Bukkit.getOnlinePlayers().forEach { player ->
                try {
                    val metadata = player.getMetadata("showing_damage_data")
                    val lastShownTime = metadata.firstOrNull()?.asLong() ?: 0L

                    if (currentTime - lastShownTime > 1000L) {
                        val message = buildString {
                            player.itemInHand?.let { item ->
                                append(player.createActionDisplay(item))
                            }
                            player.inventory.armorContents.forEach { armorItem ->
                                append(player.createActionDisplay(armorItem))
                            }
                        }

                        if (message.isNotBlank() || message.isNotEmpty()) {
                            ActionBarUtil.sendActionBar(player, CC.translate(message))
                        }
                    }
                } catch (e: Exception) {
                    // Silently ignore per-player errors to avoid kicking
                }
            }
        }, 100L, 4L) // 100 tick delay, 4 tick interval (200ms)
    }

    private fun Player.createActionDisplay(itemStack: ItemStack?): String {
        val item = itemStack?.toMythicItem() ?: return ""
        return item.enchantments.entries.joinToString(" ") { (key, value) ->
            val enchantmentDisplay = enchants[key.nbtName] ?: return@joinToString ""
            "&b&l${key.enchantName} ${enchantmentDisplay.getText(value, this)}"
        }
    }
}
