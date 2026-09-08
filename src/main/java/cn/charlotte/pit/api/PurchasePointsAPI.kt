package cn.charlotte.pit.api

import org.bukkit.entity.Player

/**
 * Points providers that can atomically validate and charge a purchase.
 */
interface PurchasePointsAPI : PointsAPI {

    fun isAvailable(): Boolean

    fun tryCostPoints(player: Player, points: Int, reason: String): Boolean

    fun refundPoints(player: Player, points: Int, reason: String): Boolean

    fun refreshPoints(player: Player)
}
