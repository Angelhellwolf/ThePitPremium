package cn.charlotte.pit.perk.type.streak.tothemoon

import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.event.PitAssistEvent
import cn.charlotte.pit.event.PitStreakKillChangeEvent
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity
import cn.charlotte.pit.perk.AbstractPerk
import cn.charlotte.pit.perk.PerkType
import cn.charlotte.pit.reward.RewardService
import cn.charlotte.pit.reward.RewardSource
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.reward.StreakRewardMath
import com.google.common.util.concurrent.AtomicDouble
import org.bukkit.Material
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

@AutoRegister
class SuperStreaker : AbstractPerk(), IPlayerKilledEntity, Listener {
    override fun getInternalPerkName(): String {
        return "super_streaker"
    }

    override fun getDisplayName(): String {
        return "超级连杀者"
    }

    override fun getIcon(): Material {
        return Material.FEATHER
    }

    override fun requireCoins(): Double {
        return 20000.0
    }

    override fun requireRenown(level: Int): Double {
        return .0
    }

    override fun requirePrestige(): Int {
        return 14
    }

    override fun requireLevel(): Int {
        return 80
    }

    override fun getDescription(player: Player?): MutableList<String> {
        return mutableListOf(
            "&7此天赋每 &c10 连杀 &7触发一次.",
            "",
            "&7触发时: ",
            " &a▶ &7立即获得 &b50经验&7.",
            " &a▶ &7此后击杀与助攻经验 &b+5% &7(本次连杀内累计, 最高50%).",
            "&7Bot 击杀可推进连杀，但不发放以上额外经验"
        )
    }

    override fun getMaxLevel(): Int {
        return 1
    }

    override fun getPerkType(): PerkType {
        return PerkType.KILL_STREAK
    }

    override fun onPerkActive(player: Player?) {

    }

    override fun onPerkInactive(player: Player?) {

    }

    @EventHandler
    fun assist(event: PitAssistEvent) {
        val profile = PlayerProfile.getPlayerProfileByUuid(event.assist.uniqueId) ?: return
        if (PlayerUtil.isPlayerChosePerk(event.assist, "super_streaker")) {
            event.exp *= 1.0 + StreakRewardMath.superStreakerBonus(profile.streakKills)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onStreak(event: PitStreakKillChangeEvent) {
        val player = Bukkit.getPlayer(event.playerProfile.playerUuid) ?: return
        if (!PlayerUtil.isPlayerChosePerk(player, getInternalPerkName())) return
        val milestones = StreakRewardMath.crossedMilestones(event.from, event.to, 10)
        if (milestones > 0) {
            cn.charlotte.pit.reward.EconomyV2.streak(event.playerProfile, milestones * 10.0, 0.0, milestones * 50.0)
        }
    }

    override fun handlePlayerKilled(
        enchantLevel: Int,
        myself: Player,
        target: Entity?,
        coins: AtomicDouble,
        experience: AtomicDouble
    ) {
        val profile = PlayerProfile.getPlayerProfileByUuid(myself.uniqueId) ?: return
        experience.set(experience.get() * (1.0 + StreakRewardMath.superStreakerBonus(profile.streakKills)))
    }
}
