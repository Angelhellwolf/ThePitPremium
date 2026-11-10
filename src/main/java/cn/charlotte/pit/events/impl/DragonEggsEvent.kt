package cn.charlotte.pit.events.impl

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.config.NewConfiguration.eventOnlineRequired
import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.reward.RewardService
import cn.charlotte.pit.reward.RewardSource
import cn.charlotte.pit.events.IEvent
import cn.charlotte.pit.events.INormalEvent
import cn.charlotte.pit.events.EventBlockTracker
import org.bukkit.scheduler.BukkitTask
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.hologram.Hologram
import cn.charlotte.pit.util.hologram.HologramAPI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.*

/**
 * @author Araykal
 * @since 2025/1/17
 */
class DragonEggsEvent : IEvent, INormalEvent, Listener {

    private var eggLocation: Location? = null
    private val placedEggLocations = HashSet<Location>()
    private var clicks = 0
    private var firstHologram: Hologram? = null
    private var secondHologram: Hologram? = null
    private var isActive = false
    private var isClickable = false
    private var endTask: BukkitTask? = null
    private val lastEconomicClick = HashMap<UUID, Long>()

    companion object {
        private const val MAX_CLICKS = 230
        private const val CLICK_THRESHOLD = 50
        private const val SEARCH_RADIUS = 10
        private const val MAX_ATTEMPTS = 20
    }

    override fun getEventInternalName(): String = "dragon_egg"

    override fun getEventName(): String = "&5龙蛋"

    override fun requireOnline(): Int = eventOnlineRequired[getEventInternalName()]!!

    private fun registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, ThePit.getInstance())
    }

    private fun unregisterEvents() {
        HandlerList.unregisterAll(this)
    }

    override fun onActive() {
        val configuredLocation = ThePit.getInstance().pitConfig.eggLoc ?: run {
            broadcastMessage("&5&l龙蛋！ &7活动区域未设置，请联系管理员设置！")
            deactivateEvent()
            return
        }
        val firstEggLocation = findRandomLocation(configuredLocation, true) ?: run {
            broadcastMessage("&5&l龙蛋！ &c配置附近没有可用空气方块, 为保护地形本次活动已取消！")
            deactivateEvent()
            return
        }
        isActive = true
        isClickable = true
        registerEvents()
        broadcastMessage("&5&l龙蛋！ &d龙蛋已在中心点位刷新,请前往点击！")
        setEggLocation(firstEggLocation)
        playSoundToPlayers(Sound.ENDERDRAGON_GROWL, 1.5f, 1.5f)
        scheduleEventDeactivation()
    }

    private fun scheduleEventDeactivation() {
        endTask?.cancel()
        endTask = Bukkit.getScheduler().runTaskLater(
            ThePit.getInstance(),
            {
                deactivateEvent()
            },
            8 * 20 * 60
        )
    }

    private fun deactivateEvent() {
        ThePit.getInstance().getEventFactory().inactiveEvent(this)
        isActive = false
        cleanup()
    }

    private fun setEggLocation(location: Location) {
        prepareLocation()
        val blockLocation = location.block.location
        if (blockLocation.block.type != Material.AIR) {
            return
        }
        eggLocation = blockLocation
        EventBlockTracker.get().captureBlock(EventBlockTracker.DRAGON_EGG, blockLocation)
        eggLocation?.block?.type = Material.DRAGON_EGG
        placedEggLocations.add(blockLocation)
        createHolograms(blockLocation)
    }

    private fun prepareLocation() {
        despawnHolograms()
        eggLocation?.let { clearEggIfPlaced(it) }
    }

    private fun clearEggIfPlaced(location: Location) {
        val blockLocation = location.block.location
        if (!placedEggLocations.remove(blockLocation)) {
            return
        }
        if (blockLocation.block.type == Material.DRAGON_EGG) {
            blockLocation.block.type = Material.AIR
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDragonEggTeleport(event: BlockFromToEvent) {
        if (!isActive) return
        if (event.block.type == Material.DRAGON_EGG) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!isActive || event.clickedBlock?.type != Material.DRAGON_EGG) return

        event.isCancelled = true

        if (!isClickable) {
            despawnHolograms()
            return
        }

        val player = event.player
        val profile = PlayerProfile.getPlayerProfileByUuid(player.uniqueId)
        handleClick(player, profile)
        updateClickState()
    }

    private fun handleClick(player: Player, profile: PlayerProfile) {
        val random = Random()
        val randomMultiplier = random.nextInt(3) + 1

        val (coins, exp) = if (clicks == 0) {
            Pair(3 * randomMultiplier, 3 * (random.nextInt(5) + 1))
        } else {
            Pair(clicks * 0.5 * randomMultiplier, clicks * 0.5 * (random.nextInt(5) + 1))
        }

        if (clicks >= MAX_CLICKS) return
        val now = System.currentTimeMillis()
        val eligible = now - (lastEconomicClick[player.uniqueId] ?: 0L) >= 250
        if (eligible) lastEconomicClick[player.uniqueId] = now
        val v2Gold = if (eligible) (8 + random.nextInt(5)) * cn.charlotte.pit.reward.ProgressionV2.sideScale(profile.prestige) else 0.0
        val reward = cn.charlotte.pit.reward.EconomyV2.grant(profile, cn.charlotte.pit.reward.RewardDetail.DRAGON_EGG,
            coins.toDouble(), exp.toDouble(), v2Gold, v2Gold * 4)

        player.playSound(player.location, Sound.CLICK, 1.5f, 1.5f)
        player.sendMessage(CC.translate(String.format(Locale.ROOT, "&5&l龙蛋！ &7点击龙蛋 获得 &e%.2f &6金币 &e%.2f &b经验&7", reward.coins, reward.experience)))
        clicks++
        updateHolograms()
    }

    private fun updateClickState() {
        if (clicks >= MAX_CLICKS) {
            deactivateEvent()
        } else if (clicks % CLICK_THRESHOLD == 0) {
            moveEggToNewLocation()
        }
    }

    private fun moveEggToNewLocation() {
        prepareLocation()
        val currentLocation = eggLocation ?: return
        val newLocation = findRandomLocation(currentLocation, false) ?: run {
            deactivateEvent()
            return
        }
        setEggLocation(newLocation)
        broadcastMessage("&5&l龙蛋！ &7龙蛋已被移动到了新的位置！")
        playSoundToPlayers(Sound.ENDERDRAGON_HIT, 1.5f, 1.5f)
        isClickable = true
    }

    private fun findRandomLocation(origin: Location, includeOrigin: Boolean): Location? {
        val random = Random()
        if (includeOrigin && origin.block.type == Material.AIR) {
            return origin.block.location
        }
        repeat(MAX_ATTEMPTS) {
            val x = origin.x + randomOffset(random)
            val z = origin.z + randomOffset(random)
            val newLocation = Location(origin.world, x, origin.y, z)
            if (newLocation.block.type == Material.AIR) return newLocation.block.location
        }
        return null
    }

    private fun randomOffset(random: Random) = random.nextInt(31) - SEARCH_RADIUS

    private fun broadcastMessage(message: String) {
        Bukkit.broadcastMessage(CC.translate(message))
    }

    private fun playSoundToPlayers(sound: Sound, volume: Float, pitch: Float) {
        Bukkit.getOnlinePlayers().forEach {
            it.playSound(it.location, sound, volume, pitch)
        }
    }

    private fun createHolograms(location: Location) {
        firstHologram = HologramAPI.createHologram(location.clone().add(0.5, 2.4, 0.5), "§a$clicks")
        secondHologram = HologramAPI.createHologram(location.clone().add(0.5, 2.0, 0.5), "§e§l点击")
        firstHologram?.spawn()
        secondHologram?.spawn()
    }

    private fun updateHolograms() {
        firstHologram?.text = "§a$clicks"
    }

    private fun despawnHolograms() {
        firstHologram?.deSpawn()
        secondHologram?.deSpawn()
        firstHologram = null
        secondHologram = null
    }

    override fun onInactive() {
        unregisterEvents()
        cleanup()
        playSoundToPlayers(Sound.ENDERDRAGON_DEATH, 1.5f, 1.5f)
        broadcastMessage("&5&l龙蛋！ &7活动已结束！")
    }

    private fun cleanup() {
        endTask?.cancel()
        endTask = null
        // Clear the current tracked egg
        eggLocation?.let { clearEggIfPlaced(it) }
        // Clear any remaining tracked locations
        placedEggLocations.toList().forEach { loc ->
            if (loc.block.type == Material.DRAGON_EGG) {
                loc.block.type = Material.AIR
            }
        }
        placedEggLocations.clear()
        EventBlockTracker.get().restoreImmediately(EventBlockTracker.DRAGON_EGG)
        despawnHolograms()
        isActive = false
        isClickable = false
        eggLocation = null
        clicks = 0
    }
}
