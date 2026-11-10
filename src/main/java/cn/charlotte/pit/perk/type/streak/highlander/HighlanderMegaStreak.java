package cn.charlotte.pit.perk.type.streak.highlander;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.reward.BountyRules;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.event.PitStreakKillChangeEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.*;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.MegaStreak;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.perk.type.shop.BountyHunterPerk;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.MessageType;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/20 22:28
 */
@AutoRegister
public class HighlanderMegaStreak extends AbstractPerk implements Listener, IAttackEntity, IPlayerDamaged, ITickTask, IPlayerKilledEntity, IPlayerBeKilledByEntity, MegaStreak {
    @Override
    public String getInternalPerkName() {
        return "high_lander";
    }

    @Override
    public String getDisplayName() {
        return "&6尊贵血统";
    }

    @Override
    public Material getIcon() {
        return Material.GOLD_BOOTS;
    }

    @Override
    public double requireCoins() {
        return 30000;
    }

    @Override
    public double requireRenown(int level) {
        return 0;
    }

    @Override
    public int requirePrestige() {
        return 0;
    }

    @Override
    public int requireLevel() {
        return 60;
    }

    @Override
    public PerkType getPerkType() {
        return PerkType.MEGA_STREAK;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> list = new ArrayList<>();
        list.add("&7激活要求连杀数: &c50 连杀");
        list.add(" ");
        list.add("&7激活后:");
        list.add("  &a▶ &7获得 &b速度 I &6&o持续时间无限");
        list.add("  &a▶ &7有效真人击杀额外获得 &6基础硬币的50%");
        list.add("  &7额外硬币随收益日历变化，不叠加其他收益加成");
        list.add("  &a▶ &7攻击被悬赏的玩家造成的伤害 &c+33%");
        list.add("  &c▶ &7赏金上限 &6+5000g");
        list.add("  &c▶ &7激活后每击杀1名玩家,受到来自装备天赋");
        list.add("  &6赏金猎人 &7的玩家伤害 &c+0.3% &7(最高 &c+30%&7)");
        list.add(" ");
        list.add("&7激活后死亡时:");
        list.add("  &7自身赏金清空，由符合条件的击杀者领取");
        list.add("  &7击杀Bot不增长赏金，也不触发额外硬币");
        return list;
    }

    @Override
    public int getMaxLevel() {
        return 0;
    }

    @Override
    public void onPerkActive(Player player) {

    }

    @Override
    public void onPerkInactive(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onStreak(PitStreakKillChangeEvent event) {
        Player myself = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
        if (myself == null || !myself.isOnline()) {
            return;
        }
        if (!PlayerUtil.isPlayerChosePerk(myself, getInternalPerkName())) {
            return;
        }
        int trigger = 50;
        //trigger check (get X streak)
        if (event.getFrom() < trigger && event.getTo() >= trigger) {
            cn.charlotte.pit.junxu.JunxuMessages.broadcast(event.getPlayerProfile().getPlayerUuid(), MessageType.COMBAT, "&c&l超级连杀! " + event.getPlayerProfile().getFormattedNameWithRoman() + " &7激活了 &6&l尊贵血统 &7!");
            cn.charlotte.pit.junxu.JunxuMessages.audience(event.getPlayerProfile().getPlayerUuid()).forEach(player -> player.playSound(player.getLocation(), Sound.WITHER_SPAWN, 0.8F, 1.5F));
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        // CombatListener credits a fixed share of the raw human kill reward after modifiers.
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isInArena()) {
            profile.setStreakKills(0);
        }
        boolean speedFound = false;
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            if (potionEffect.getType().getName().equalsIgnoreCase("speed")) {
                speedFound = true;
                //自身有速度 I 且时间小于<30秒时直接覆盖
                if (potionEffect.getAmplifier() == 0 && potionEffect.getDuration() < 30 * 20 && profile.getStreakKills() >= 50) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 86400 * 20, 0), true);
                    return;
                }
                //如果不满足连杀条件有时间过长的速度 I 则直接清除
                if (potionEffect.getAmplifier() == 0 && potionEffect.getDuration() > 8640 * 20 && profile.getStreakKills() < 50) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                    return;
                }
            }
        }
        //自身没有速度效果且连杀条件满足时补充速度效果
        if (profile.getStreakKills() >= 50 && !speedFound) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 86400 * 20, 0), true);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 3 * 20;
    }

    @Override
    @PlayerOnly
    public void handlePlayerBeKilledByEntity(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        // The victim never receives a second copy of the hunter's bounty.
    }


    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(myself.getUniqueId());
        if (profile.getStreakKills() > 50) {
            Player attackerPlayer = (Player) attacker;
            if (PlayerUtil.isPlayerChosePerk(attackerPlayer, new BountyHunterPerk().getInternalPerkName())) {
                boostDamage.getAndAdd(BountyRules.highlanderVulnerability(profile.getStreakKills()));
            }
        }
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Player targetPlayer = (Player) target;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        PlayerProfile targetprofile = PlayerProfile.getPlayerProfileByUuid(targetPlayer.getUniqueId());
        if (profile.getStreakKills() > 50 && targetprofile.getBounty() > 0) {
            boostDamage.getAndAdd(0.33);
        }
    }

    @Override
    public int getStreakNeed() {
        return 50;
    }
}
