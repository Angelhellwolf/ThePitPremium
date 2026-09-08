package cn.charlotte.pit.events

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.data.EventQueue
import cn.charlotte.pit.util.random.RandomUtil
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bukkit.Bukkit
import java.util.*
import kotlin.random.Random

object EventsHandler {
    val epicQueue: Queue<String> = LinkedList()
    val normalQueue: Queue<String> = LinkedList()

    fun refreshEvents() {
        val factory = ThePit.getInstance().eventFactory

        val epicNames = factory.epicEvents.map { (it as IEvent).eventInternalName }.toSet()
        val normalNames = factory.normalEvents.map { (it as IEvent).eventInternalName }.toSet()
        epicQueue.removeIf { it !in epicNames }
        normalQueue.removeIf { it !in normalNames }

        var count = epicQueue.size
        if (count < 50 && factory.epicEvents.isNotEmpty()) {
            val need = 50 - count
            repeat(need) {
                val event = factory.epicEvents[Random.nextInt(factory.epicEvents.size)] as IEvent
                epicQueue.add(event.eventInternalName)
            }
        }

        count = normalQueue.size

        if (count < 100 && factory.normalEvents.isNotEmpty()) {
            val need = 100 - count
            repeat(need) {
                val event = factory.normalEvents[Random.nextInt(factory.normalEvents.size)] as IEvent
                if (event.eventInternalName.equals("auction") && RandomUtil.hasSuccessfullyByChance(0.75)) {
                    val anotherEvent = factory.normalEvents[Random.nextInt(factory.normalEvents.size)] as IEvent
                    normalQueue.add(anotherEvent.eventInternalName)
                } else {
                    normalQueue.add(event.eventInternalName)
                }
            }
        }

        val eventQueue = EventQueue().apply {
            this.normalEvents.addAll(normalQueue)
            this.epicEvents.addAll(epicQueue)
        }

        Bukkit.getScheduler().runTaskAsynchronously(ThePit.getInstance()) {
            ThePit.getInstance().mongoDB.eventQueueCollection.replaceOne(Filters.eq("id", "1"), eventQueue, ReplaceOptions().upsert(true))
        }
    }

    fun loadFromDatabase() {
        this.epicQueue.clear()
        this.normalQueue.clear()

        val queue = ThePit.getInstance().mongoDB.eventQueueCollection.findOne()
        if (queue == null) {
            refreshEvents()
            return
        }

        normalQueue += queue.normalEvents
        epicQueue += queue.epicEvents

        try {
            if (this.epicQueue.size < 45 || this.normalQueue.size < 90) {
                this.refreshEvents()
            }
        }catch (e: Exception) {

        }
    }

    fun nextEvent(major: Boolean): String {
        val factory = ThePit.getInstance().eventFactory
        val queue = if (major) epicQueue else normalQueue
        if (queue.isEmpty()) refreshEvents()
        val registered = if (major) factory.epicEvents.map { it as IEvent }
                         else factory.normalEvents.map { it as IEvent }
        val event = EventQueueSelector.pollEligible(queue, registered, Bukkit.getOnlinePlayers().size)
        this.refreshEvents()

        return event ?: "No event available"
    }

}