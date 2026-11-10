package cn.charlotte.pit.perk.type.streak.uber


import cn.charlotte.pit.config.NewConfiguration
import cn.charlotte.pit.data.PlayerProfile
import cn.charlotte.pit.event.*
import cn.charlotte.pit.getPitProfile
import cn.charlotte.pit.item.type.GlobalAttentionGem
import cn.charlotte.pit.item.type.UberDrop
import cn.charlotte.pit.parm.AutoRegister
import cn.charlotte.pit.perk.AbstractPerk
import cn.charlotte.pit.perk.MegaStreak
import cn.charlotte.pit.perk.PerkType
import cn.charlotte.pit.reward.CombatEconomy
import cn.charlotte.pit.util.PlayerUtil
import cn.charlotte.pit.util.chat.CC
import cn.charlotte.pit.util.chat.MessageType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.random.Random


/*
 * @ Created with IntelliJ IDEA
 * @ Author EmptyIrony
 * @ Date 10/6/2021
 * @ Time 1:32 PM
 */
@AutoRegister
class UberStreak : AbstractPerk(), Listener, MegaStreak {

    override fun getInternalPerkName(): String {
        return "uber_streak"
    }

    override fun getDisplayName(): String {
        return "&d登峰造极"
    }

    override fun getIcon(): Material {
        return Material.GOLD_SWORD
    }

    override fun requireCoins(): Double {
        return 50000.0
    }

    override fun requireRenown(level: Int): Double {
        return 50.0
    }

    override fun requirePrestige(): Int {
        return 20
    }

    override fun requireLevel(): Int {
        return 90
    }

    override fun getPerkType(): PerkType {
        return PerkType.MEGA_STREAK
    }

    override fun getDescription(player: Player): List<String> {
        val list: MutableList<String> = ArrayList()
        list.add("&7激活要求连杀数: &c100 连杀")
        list.add(" ")
        list.add("&7激活后:")
        list.add("  &a▶ &7有 &e${NewConfiguration.uberHelmetChance * 100}% &7几率获得钻石头盔")
        list.add("  &a▶ &7宝石与完成奖励受有效战斗积累限制")
        list.add("  &a▶ &7获得神话物品的几率提升至原来的 &d150%")
        list.add("  &c▶ &7承伤随连杀持续增加，每100连杀累计 &c+10%")
        list.add("  &c▶ &7100连杀后,攻击未精通玩家造成的伤害 &c-40%")
        list.add("  &c▶ &7200连杀后,生命上限 &c-2❤")
        list.add("  &c▶ &7300连杀后,所有药水效果持续时间 &c-50%")
        list.add("  &c▶ &7400连杀后,&c无法恢复生命值")
        list.add(" ")
        list.add("&7激活后死亡时:")
        list.add("  &a▶ &7若连杀数达到400,获得一件 &d登峰造极掉落物")
        list.add("  &7每日最多4次，依次消耗 &e0/5/10/15 &7声望")
        list.add(" ")
        list.add("&c注意:400连杀前，连杀助理与晋升不能提供神话生命保护!")
        return list
    }

    override fun getMaxLevel(): Int {
        return 0
    }

    override fun onPerkActive(player: Player?) {}

    override fun onPerkInactive(player: Player?) {}

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRegen(event: EntityRegainHealthEvent) {
        val player = event.entity
        if (player is Player) {
            val profile = PlayerProfile.getPlayerProfileByUuid(player.uniqueId)
            if (!profile.isLoaded) {
                return
            }

            if (!PlayerUtil.isPlayerChosePerk(player, "uber_streak")) {
                return
            }

            if (profile.streakKills < 400) {
                return
            }

            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRegen(event: PitRegainHealthEvent) {
        val player = event.player
        val profile = PlayerProfile.getPlayerProfileByUuid(player.uniqueId)
        if (!profile.isLoaded) {
            return
        }

        if (!PlayerUtil.isPlayerChosePerk(player, "uber_streak")) {
            return
        }

        if (profile.streakKills < 400) {
            return
        }

        event.isCancelled = true
    }

    /**
     * 100 连杀负面效果 和 激活后每击杀100名玩家的负面效果
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        val damager = when (val source = event.damager) {
            is Player -> source
            is Projectile -> source.shooter as? Player
            else -> null
        }

        if (damager is Player && victim is Player) {
            val hasUber = this.hasUber(damager)
            if (hasUber) {
                val profile = damager.getPitProfile()
                if (profile.streakKills >= 100) {
                    val victimProfile = victim.getPitProfile()
                    if (victimProfile.prestige < 1) {
                        event.damage = event.damage * 0.6
                    }
                }
            }

            val victimHasUber = this.hasUber(victim)
            if (victimHasUber) {
                val victimProfile = victim.getPitProfile()
                if (victimProfile.streakKills >= 100) {
                    event.damage *= 1 + victimProfile.streakKills / 1000
                }
            }
        }
    }

    /**
     * 100 连杀播报 以及 200连杀负面效果
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onStreak(event: PitStreakKillChangeEvent) {
        val player = Bukkit.getPlayer(event.playerProfile.playerUuid) ?: return
        if (!hasUber(player)) return

        if (event.from < 100 && event.to >= 100) {
            cn.charlotte.pit.junxu.JunxuMessages.broadcast(event.playerProfile.playerUuid,
                MessageType.COMBAT,
                "&c&l超级连杀! " + event.playerProfile.formattedNameWithRoman + " &7激活了 &c&l登峰造极 &7!"
            )
            cn.charlotte.pit.junxu.JunxuMessages.audience(event.playerProfile.playerUuid).forEach { online: Player ->
                online.playSound(
                    online.location,
                    Sound.WITHER_SPAWN,
                    0.8f,
                    1.5f
                )
            }

            if (Random.nextDouble() < NewConfiguration.uberHelmetChance) {
                val helmet = ItemStack(Material.DIAMOND_HELMET)
                if (player.inventory.helmet == null || player.inventory.helmet.type == Material.AIR) {
                    player.inventory.helmet = helmet
                } else {
                    giveOrDrop(player, helmet)
                }
                player.sendMessage(CC.translate("&d登峰造极! &7你抽中了钻石头盔!"))
            }
            if (CombatEconomy.claimRecent(event.playerProfile, "uber_activation_gem", 100.0)) {
                giveOrDrop(player, GlobalAttentionGem().toItemStack())
                player.sendMessage(CC.translate("&d登峰造极! &7你获得了 &b举世瞩目的宝石&7!"))
            } else player.sendMessage(CC.translate("&d登峰造极! &7宝石奖励计入训练积累。"))
        }

        if (event.from < 200 && event.to >= 200) {
            player.maxHealth = (player.maxHealth - 4).coerceAtLeast(1.0)
        }
    }

    /**
     * 死亡掉落以及恢复数据
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerBeKilled(event: PitKillEvent) {
        val victim = event.target
        if (victim !is Player) return

        if (!victim.isOnline) {
            return
        }

        if (!hasUber(victim)) return

        val profile = victim.getPitProfile()
        // Restore the 200-kill penalty even when settlement cannot award an item.
        if (profile.streakKills >= 200) victim.maxHealth = profile.maxHealth
        if (profile.streakKills < 400) return

        val today = Calendar.getInstance()
        val last = Calendar.getInstance().apply { timeInMillis = profile.todayCompletedUberLastRefreshed }
        if (today.get(Calendar.YEAR) != last.get(Calendar.YEAR) ||
            today.get(Calendar.DAY_OF_YEAR) != last.get(Calendar.DAY_OF_YEAR)) {
            profile.todayCompletedUberLastRefreshed = today.timeInMillis
            profile.todayCompletedUber = 0
        }

        val completed = profile.todayCompletedUber.coerceAtLeast(0)
        if (completed >= 4) {
            victim.sendMessage(CC.translate("&c登峰造极! &c你今日完成次数已达上限!"))
            return
        }
        val cost = completed * 5
        if (profile.renown < cost) {
            victim.sendMessage(CC.translate("&c登峰造极! &c声望不足，需要 ${cost} 声望；未消耗今日奖励次数。"))
            return
        }
        // Do not consume accumulated reward credit before checking the daily cap and price.
        if (!CombatEconomy.claimRecent(profile, "uber_completion", 400.0)) {
            victim.sendMessage(CC.translate("&d登峰造极! &7本次掉落计入训练积累，不扣声望与今日奖励次数。"))
            return
        }

        giveOrDrop(victim, UberDrop().toItemStack())
        profile.renown -= cost
        profile.todayCompletedUber = completed + 1
    }

    private fun giveOrDrop(player: Player, item: ItemStack) {
        val leftovers = player.inventory.addItem(item)
        if (leftovers.isNotEmpty()) {
            leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
            player.sendMessage(CC.translate("&c背包空间不足，奖励已掉落在脚下。"))
        }
    }


    /**
     * 300连杀 负面效果
     *     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPotionAdd(event: PotionAddEvent) {
        val player = event.player

        if (!hasUber(player)) return
        val profile = player.getPitProfile()
        if (profile.streakKills < 300) return

        val effect = event.effect
        event.effect = PotionEffect(effect.type, (effect.duration / 2).coerceAtLeast(1),
            effect.amplifier, effect.isAmbient, effect.hasParticles())
    }


    @EventHandler
    private fun onSpawn(event: PitPlayerSpawnEvent) {
        event.player.getPitProfile().streakKills = 0.0
    }

    private fun hasUber(player: Player): Boolean {
        return PlayerUtil.isPlayerChosePerk(player, "uber_streak")
    }

    override fun getStreakNeed(): Int {
        return 100
    }


}
