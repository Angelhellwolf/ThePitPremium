package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.combat.PlayerLife;
import cn.charlotte.pit.util.cooldown.Cooldown;
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

import java.util.HashMap;
import java.util.UUID;

@ArmorOnly
@AutoRegister
public class PeroxideEnchant extends AbstractEnchantment implements Listener, IActionDisplayEnchant {

    public static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "过氧化物";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "Peroxide";
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
        return "&7受到攻击时恢复自身 &c" + getHeal(enchantLevel) + "❤ &7生命值 &7(1.5秒冷却)";
    }

    private double getHeal(int enchantLevel) {
        switch (enchantLevel) {
            case 1: return 0.5;
            case 2: return 0.75;
            case 3: return 1.0;
            default: return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)
                || CombatDamage.accepted(event) <= 0) return;
        Player player = (Player) event.getEntity();
        if (!player.isOnline() || player.isDead() || player.getHealth() <= 0) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile && ((Projectile) attacker).getShooter() instanceof Entity) {
            attacker = (Entity) ((Projectile) attacker).getShooter();
        }
        // Defensive effects belong to the wearer, including the wearer's immunity bypass.
        boolean ignored = attacker instanceof Player
                ? PlayerUtil.shouldIgnoreEnchant(player, (Player) attacker)
                : PlayerUtil.shouldIgnoreEnchant(player);
        if (ignored) return;
        int level = getItemEnchantLevel(player.getInventory().getLeggings());
        double heal = getHeal(level) * 2;
        if (heal <= 0) return;
        UUID uuid = player.getUniqueId();
        Cooldown previous = cooldown.get(uuid);
        if (previous != null && !previous.hasExpired()) return;
        Cooldown activation = new Cooldown(1500);
        Object life = PlayerLife.capture(player);
        cooldown.put(uuid, activation);
        // Bukkit applies the hit after listeners finish. Heal on the next server tick.
        Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
            if (cooldown.get(uuid) != activation) return;
            if (event.isCancelled() || !PlayerLife.isCurrent(player, life)) {
                cooldown.remove(uuid, activation);
                return;
            }
            PlayerUtil.heal(player, heal);
        }, 1L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cooldown.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldown.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public String getText(int level, Player player) {
        return this.getCooldownActionText(cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)));
    }
}
