package cn.charlotte.pit.runnable

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.util.chat.ActionBarUtil
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.toMythicItem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

object ActionBarDisplayRunnable {

    private const val DISPLAY_INTERVAL_TICKS = 10L
    private const val KEEPALIVE_MS = 2000L

    private data class CachedMessage(val text: String, val sentAt: Long)

    private val lastMessages = HashMap<UUID, CachedMessage>()

    private val enchants by lazy {
        ThePit.getInstance().enchantmentFactor.actionDisplayEnchants
    }

    fun start() {
        // Use Bukkit scheduler instead of Java ExecutorService to ensure
        // packets are sent on the main thread, avoiding ViaVersion/PacketEvents
        // race conditions that kick 1.21 clients with "Internal Error".
        Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(), Runnable {
            val currentTime = System.currentTimeMillis()
            val online = HashSet<UUID>()

            Bukkit.getOnlinePlayers().forEach { player ->
                online.add(player.uniqueId)
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

                        if (message.isNotBlank()) {
                            val translated = CC.translate(message)
                            val previous = lastMessages[player.uniqueId]
                            if (previous == null || previous.text != translated ||
                                currentTime - previous.sentAt >= KEEPALIVE_MS
                            ) {
                                ActionBarUtil.sendActionBar(player, translated)
                                lastMessages[player.uniqueId] = CachedMessage(translated, currentTime)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silently ignore per-player errors to avoid kicking
                }
            }
            lastMessages.keys.retainAll(online)
        }, 100L, DISPLAY_INTERVAL_TICKS)
    }

    private fun Player.createActionDisplay(itemStack: ItemStack?): String {
        val item = itemStack?.toMythicItem() ?: return ""
        return item.enchantments.entries.joinToString(" ") { (key, value) ->
            val enchantmentDisplay = enchants[key.nbtName] ?: return@joinToString ""
            "&b&l${key.enchantName} ${enchantmentDisplay.getText(value, this)}"
        }
    }
}
