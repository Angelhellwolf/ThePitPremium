package cn.charlotte.pit.enchantment.type.aqua

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.enchantment.AbstractEnchantment
import cn.charlotte.pit.enchantment.param.item.ArmorOnly
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity
import cn.charlotte.pit.parm.listener.ITickTask
import cn.charlotte.pit.util.cooldown.Cooldown
import cn.charlotte.pit.util.music.NBSDecoder
import cn.charlotte.pit.util.music.PositionSongPlayer
import cn.charlotte.pit.util.music.Song
import cn.charlotte.pit.util.random.RandomUtil
import org.bukkit.Bukkit
import org.bukkit.Effect
import org.bukkit.entity.Player
import java.util.UUID

@ArmorOnly
class StereoEnchant : AbstractEnchantment(), ITickTask {

    private val playerMap = HashMap<UUID, PositionSongPlayer>()
    init {
        Bukkit.getScheduler().runTaskTimer(ThePit.getInstance(), Runnable {
            playerMap.entries.removeIf { entry ->
                val player = Bukkit.getPlayer(entry.key)
                if (player == null || !player.isOnline || getItemEnchantLevel(player.inventory.leggings) <= 0 || !isEffectActive(player, null)) {
                    entry.value.destroy()
                    true
                } else false
            }
        }, 20L, 20L)
    }
    private val songs: List<Song> = listOfNotNull(
        loadSong("rapid_as_wildfire.nbs"),
        loadSong("pvz2.nbs"),
        loadSong("pvz3.nbs"),
        loadSong("God.nbs")
    )

    private fun loadSong(name: String): Song? {
        return try {
            NBSDecoder.parse(ThePit.getInstance().javaClass.classLoader.getResourceAsStream(name))
        } catch (ignore: Exception) {
            null
        }
    }

    override fun getEnchantName(): String {
        return "立体声"
    }

    override fun getMaxEnchantLevel(): Int {
        return 1
    }

    override fun getNbtName(): String {
        return "stereo"
    }

    override fun getRarity(): EnchantmentRarity {
        return EnchantmentRarity.FISH_RARE
    }

    override fun getCooldown(): Cooldown? {
        return null
    }

    override fun getUsefulnessLore(enchantLevel: Int): String {
        return "&7装备时向附近玩家播放随机音符盒音乐"
    }

    override fun handle(enchantLevel: Int, target: Player) {
        val songPlayer = playerMap[target.uniqueId]
        if (songPlayer == null) {
            if (songs.isEmpty()) {
                return
            }
            val song = songs[RandomUtil.random.nextInt(songs.size)]
            val player = PositionSongPlayer(song)
            player.targetLocation = target.location
            player.setAutoDestroy(false)
            player.setLoop(true)
            player.setPlaying(true)
            player.volume = 5.toByte()
            playerMap[target.uniqueId] = player
        } else {
            songPlayer.targetLocation = target.location
            target.world.playEffect(target.location.clone().add(0.0, 3.0, 0.0), Effect.NOTE, 1)
        }
        playerMap.entries.removeIf { entry ->
            val player = Bukkit.getPlayer(entry.key)
            if (player == null || !player.isOnline || getItemEnchantLevel(player.inventory.leggings) <= 0) {
                entry.value.destroy()
                true
            } else {
                false
            }
        }
    }

    override fun loopTick(enchantLevel: Int): Int {
        return 20
    }
}
