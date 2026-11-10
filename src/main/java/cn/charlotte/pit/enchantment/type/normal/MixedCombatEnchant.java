package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.BotEnchantPolicy;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.combat.SecondaryDamage;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.rank.RankUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/23 13:40
 */
@BowOnly
@AutoRegister
public class MixedCombatEnchant extends AbstractEnchantment implements IAttackEntity, IPlayerShootEntity, IActionDisplayEnchant, Listener {

    private static final long MARK_MILLIS = 15_000;
    private static final HashMap<UUID, Mark> aim = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "灵活战术";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "mixed_combat_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7弓箭命中玩家时锁定该玩家 &e15秒&7,"
                + "/s&7切换任意近战武器命中锁定目标时,"
                + "/s&7该次普通伤害 &c+" + (enchantLevel * 10) + "% &7并解除锁定";
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Mark mark = currentMark(attacker.getUniqueId());
        if (!cancel.get() && mark != null && target instanceof Player && mark.target.equals(target.getUniqueId())
                && BotEnchantPolicy.effectAllowed(attacker, (Player) target)) {
            boostDamage.getAndAdd(0.1 * mark.level);
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        // Keep the projectile snapshot registration, but only commit a mark once
        // the original hit has survived every cancelling damage listener.
    }

    public int getMarkedMeleeLevel(Player attacker, Entity target) {
        Mark mark = currentMark(attacker.getUniqueId());
        return mark != null && target instanceof Player && mark.target.equals(target.getUniqueId()) ? mark.level : -1;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcceptedHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || SecondaryDamage.isHit(event) || CombatDamage.accepted(event) <= 0) return;
        Player target = (Player) event.getEntity();
        if (event.getDamager() instanceof Arrow && ((Arrow) event.getDamager()).getShooter() instanceof Player) {
            Player shooter = (Player) ((Arrow) event.getDamager()).getShooter();
            if (shooter.getUniqueId().equals(target.getUniqueId())
                    || !BotEnchantPolicy.effectAllowed(shooter, target)
                    || PlayerUtil.shouldIgnoreEnchant(shooter, target)) return;
            int level = projectileLevel((Arrow) event.getDamager());
            if (level > 0) aim.put(shooter.getUniqueId(), new Mark(target.getUniqueId(), Math.min(level, 3), System.currentTimeMillis()));
        } else if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Mark mark = currentMark(attacker.getUniqueId());
            if (mark != null && mark.target.equals(target.getUniqueId())
                    && BotEnchantPolicy.effectAllowed(attacker, target)
                    && !PlayerUtil.shouldIgnoreEnchant(attacker, target)) {
                aim.remove(attacker.getUniqueId(), mark);
            }
        }
    }

    private int projectileLevel(Arrow arrow) {
        for (MetadataValue metadata : arrow.getMetadata("enchant")) {
            if (metadata.getOwningPlugin() != ThePit.getInstance()) continue;
            for (String entry : metadata.asString().split(";")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2 || !getNbtName().equals(parts[0])) continue;
                try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }

    private static Mark currentMark(UUID player) {
        Mark mark = aim.get(player);
        if (mark != null && System.currentTimeMillis() - mark.createdAt >= MARK_MILLIS) {
            aim.remove(player, mark);
            return null;
        }
        return mark;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { clear(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer().getUniqueId()); }

    private static void clear(UUID player) {
        aim.remove(player);
        aim.values().removeIf(mark -> mark.target.equals(player));
    }

    private static final class Mark {
        final UUID target;
        final int level;
        final long createdAt;
        Mark(UUID target, int level, long createdAt) { this.target = target; this.level = level; this.createdAt = createdAt; }
    }

    @Override
    public String getText(int level, Player player) {
        Mark mark = currentMark(player.getUniqueId());
        return mark == null ? "&a&l✔" : "&e&l" + CC.stripColor(RankUtil.getPlayerColoredName(mark.target));
    }
}
