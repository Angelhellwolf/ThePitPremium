package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.Passive;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.combat.PlayerLife;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Passive
@AutoRegister
public class BattleFuryPerk extends AbstractPerk implements IAttackEntity, IPlayerShootEntity, Listener {

    private static final Map<UUID, Window> windows = new HashMap<>();
    private static final long DURATION_MILLIS = 3000;
    private static final long COOLDOWN_MILLIS = 8000;

    @Override
    public String getInternalPerkName() {
        return "battle_fury_perk";
    }

    @Override
    public String getDisplayName() {
        return "愈战愈勇";
    }

    @Override
    public Material getIcon() {
        return Material.BLAZE_POWDER;
    }

    @Override
    public double requireCoins() {
        return 0;
    }

    @Override
    public double requireRenown(int level) {
        return 10 + (level - 1) * 5;
    }

    @Override
    public int requirePrestige() {
        return 1;
    }

    @Override
    public int requireLevel() {
        return 0;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("&7受到玩家有效攻击后,若剩余总生命");
        lines.add("&7(含吸收心)低于 &c6❤&7,获得反击加成:");
        lines.add("&7I / II / III: 普通伤害 &c+5% / +10% / +20%");
        lines.add("&7持续 &e3秒&7,从触发时起冷却 &e8秒");
        lines.add("&7不增加真实或必中伤害,环境与自伤不触发");
        return lines;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public PerkType getPerkType() {
        return PerkType.PERK;
    }

    @Override
    public void onPerkActive(Player player) {

    }

    @Override
    public void onPerkInactive(Player player) {
        windows.remove(player.getUniqueId());
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cancel.get()) boostDamage.addAndGet(activeBonus(attacker, enchantLevel, System.currentTimeMillis()));
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cancel.get()) boostDamage.addAndGet(activeBonus(attacker, enchantLevel, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || CombatDamage.accepted(event) <= 0) return;
        Player player = (Player) event.getEntity();
        Entity source = event.getDamager();
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Entity) {
            source = (Entity) ((Projectile) source).getShooter();
        }
        if (!(source instanceof Player) || source.getUniqueId().equals(player.getUniqueId()) || getPlayerLevel(player) <= 0) return;
        Object life = PlayerLife.capture(player);
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (event.isCancelled() || !PlayerLife.isCurrent(player, life) || getPlayerLevel(player) <= 0) return;
            activate(player, life, CombatDamage.healthIncludingAbsorption(player), System.currentTimeMillis());
        });
    }

    boolean activate(Player player, Object life, double remainingHealth, long now) {
        if (!Double.isFinite(remainingHealth) || remainingHealth <= 0 || remainingHealth >= 12) return false;
        Window previous = windows.get(player.getUniqueId());
        if (previous != null && previous.life == life && now < previous.readyAt) return false;
        windows.put(player.getUniqueId(), new Window(life, now + DURATION_MILLIS, now + COOLDOWN_MILLIS));
        return true;
    }

    double activeBonus(Player player, int level, long now) {
        Window window = windows.get(player.getUniqueId());
        if (window == null || !PlayerLife.isCurrent(player, window.life)) {
            windows.remove(player.getUniqueId());
            return 0;
        }
        if (level <= 0 || now >= window.activeUntil) return 0;
        return level == 1 ? 0.05 : level == 2 ? 0.10 : 0.20;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { windows.remove(event.getEntity().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { windows.remove(event.getPlayer().getUniqueId()); }

    private static final class Window {
        final Object life;
        final long activeUntil;
        final long readyAt;
        Window(Object life, long activeUntil, long readyAt) {
            this.life = life; this.activeUntil = activeUntil; this.readyAt = readyAt;
        }
    }
}
