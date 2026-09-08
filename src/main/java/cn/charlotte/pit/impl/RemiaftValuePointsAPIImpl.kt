package cn.charlotte.pit.impl

import cn.charlotte.pit.ThePit
import cn.charlotte.pit.api.PurchasePointsAPI
import cn.charlotte.pit.event.PitProfileLoadedEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * RemiaftValue-backed point provider.
 *
 * Balance reads use RemiaftValue's asynchronous API and a short cache so menu
 * rendering never performs database I/O on the Bukkit thread. Purchases use
 * RemiaftValue's atomic real-time deduction and must be initiated asynchronously.
 */
object RemiaftValuePointsAPIImpl : PurchasePointsAPI, Listener {

    private const val API_CLASS = "com.remiaft.remiaftvalue.api.RemiaftValueAPI"
    private const val VALUE_TYPE = "Points"
    private const val UNKNOWN_BALANCE = Int.MIN_VALUE
    private const val CACHE_MILLIS = 10_000L
    private const val WARNING_INTERVAL_MILLIS = 30_000L

    private data class CachedBalance(val value: Int, val refreshedAt: Long = System.currentTimeMillis()) {
        fun isFresh(): Boolean = System.currentTimeMillis() - refreshedAt < CACHE_MILLIS
    }

    private val balances = ConcurrentHashMap<UUID, CachedBalance>()
    private val refreshing = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile
    private var ready = false

    @Volatile
    private var lastWarningAt = 0L

    override fun isAvailable(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin("RemiaftValue")
        if (plugin == null || !plugin.isEnabled) {
            ready = false
            return false
        }
        if (ready) {
            return true
        }

        return try {
            apiClass().getMethod("getDatabaseManager").invoke(null)
            ready = true
            true
        } catch (throwable: Throwable) {
            warn("RemiaftValue 尚未就绪，会员购买暂不可用", unwrap(throwable))
            false
        }
    }

    override fun hasPoints(player: Player, points: Int): Boolean {
        return points <= 0 || getPoints(player) >= points
    }

    override fun getPoints(player: Player): Int {
        val cached = balances[player.uniqueId]
        if (cached == null || !cached.isFresh()) {
            refreshPoints(player)
        }
        return cached?.value ?: UNKNOWN_BALANCE
    }

    override fun costPoints(player: Player, points: Int) {
        if (!tryCostPoints(player, points, "天坑赞助者购买")) {
            throw IllegalStateException("RemiaftValue 点券扣除失败")
        }
    }

    override fun tryCostPoints(player: Player, points: Int, reason: String): Boolean {
        require(points > 0) { "points must be positive" }
        check(!Bukkit.isPrimaryThread()) { "RemiaftValue deduction must not run on the Bukkit thread" }
        if (!isAvailable()) {
            return false
        }

        return try {
            val manager = apiClass().getMethod("getDatabaseManager").invoke(null)
            val connection = manager.javaClass.getMethod("getConnection").invoke(manager) as java.sql.Connection
            val success = try {
                check(connection.autoCommit) { "Purchase connection must use auto-commit" }
                AffordablePointsDebit.debit(connection, player.uniqueId, points)
            } finally {
                try {
                    manager.javaClass.getMethod("releaseConnection", java.sql.Connection::class.java)
                        .invoke(manager, connection)
                } catch (throwable: Throwable) {
                    warn("释放点券数据库连接失败", unwrap(throwable))
                }
            }
            if (success) {
                // The debit is committed. A cache refresh failure must never turn it
                // into a failed purchase and charge the player without delivery.
                synchronizeCommittedBalance(player)
                ThePit.getInstance().logger.info("POINTS_PURCHASE uuid=${player.uniqueId} player=${player.name} amount=$points reason=$reason")
            }
            success
        } catch (throwable: Throwable) {
            warn("RemiaftValue 点券扣除失败", unwrap(throwable))
            false
        }
    }

    override fun refundPoints(player: Player, points: Int, reason: String): Boolean {
        require(points > 0) { "points must be positive" }
        check(!Bukkit.isPrimaryThread()) { "RemiaftValue refund must not run on the Bukkit thread" }
        if (!isAvailable()) {
            return false
        }

        return try {
            apiClass()
                .getMethod(
                    "addPointsRealTime",
                    Player::class.java,
                    Integer::class.java,
                    String::class.java
                )
                .invoke(null, player, points, reason)

            balances.computeIfPresent(player.uniqueId) { _, cached ->
                CachedBalance(cached.value + points)
            }
            refreshPoints(player)
            true
        } catch (throwable: Throwable) {
            warn("RemiaftValue 点券退款失败", unwrap(throwable))
            false
        }
    }

    /** Also used after a supporter transaction atomically commits its points and entitlements. */
    fun synchronizeCommittedBalance(player: Player) {
        try {
            val committed = apiClass().getMethod("getPointsRealTime", Player::class.java)
                .invoke(null, player) as Number
            balances[player.uniqueId] = CachedBalance(committed.toInt())
            val plugin = Bukkit.getPluginManager().getPlugin("RemiaftValue")!!
            val valueManager = plugin.javaClass.getMethod("getValueSystemManager").invoke(null)
            valueManager.javaClass.getMethod("synchronizeCommittedPlayerValue",
                UUID::class.java, String::class.java, String::class.java, Any::class.java)
                .invoke(valueManager, player.uniqueId, player.name, VALUE_TYPE, committed)
        } catch (throwable: Throwable) {
            balances.remove(player.uniqueId)
            warn("点券已扣除，但刷新余额缓存失败", unwrap(throwable))
        }
    }

    override fun refreshPoints(player: Player) {
        if (!player.isOnline || !isAvailable() || !refreshing.add(player.uniqueId)) {
            return
        }

        try {
            val result = apiClass()
                .getMethod("getValueAsync", Player::class.java, String::class.java)
                .invoke(null, player, VALUE_TYPE)

            if (result !is CompletableFuture<*>) {
                refreshing.remove(player.uniqueId)
                warn("RemiaftValue 返回了无效的异步余额结果")
                return
            }

            result.whenComplete { value, error ->
                try {
                    if (error == null && value is Number) {
                        balances[player.uniqueId] = CachedBalance(value.toInt())
                    } else if (error != null) {
                        warn("RemiaftValue 点券余额读取失败", unwrap(error))
                    }
                } finally {
                    refreshing.remove(player.uniqueId)
                }
            }
        } catch (throwable: Throwable) {
            refreshing.remove(player.uniqueId)
            warn("RemiaftValue 点券余额读取失败", unwrap(throwable))
        }
    }

    @EventHandler
    fun onProfileLoaded(event: PitProfileLoadedEvent) {
        Bukkit.getPlayer(event.playerProfile.playerUuid)?.let(::refreshPoints)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        balances.remove(event.player.uniqueId)
        refreshing.remove(event.player.uniqueId)
    }

    private fun apiClass(): Class<*> {
        val plugin = Bukkit.getPluginManager().getPlugin("RemiaftValue")
            ?: throw IllegalStateException("RemiaftValue 未安装")
        return Class.forName(API_CLASS, true, plugin.javaClass.classLoader)
    }

    private fun unwrap(throwable: Throwable): Throwable {
        return if (throwable is InvocationTargetException && throwable.cause != null) {
            throwable.cause!!
        } else {
            throwable
        }
    }

    private fun warn(message: String, throwable: Throwable? = null) {
        val now = System.currentTimeMillis()
        if (now - lastWarningAt < WARNING_INTERVAL_MILLIS) {
            return
        }
        lastWarningAt = now
        val detail = throwable?.message?.takeIf { it.isNotBlank() }
        ThePit.getInstance().logger.warning(if (detail == null) message else "$message: $detail")
    }
}
