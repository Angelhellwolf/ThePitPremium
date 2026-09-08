package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
@AutoRegister
public class WhatDoesntKillYouEnchant extends AbstractEnchantment implements Listener, IActionDisplayEnchant {

    private static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "杀不死你的";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "what_doesnt_kill_you_enchant";
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
        return "&7用弓箭射中自己时恢复 &c" + (0.5 + enchantLevel) + "❤ &7(3秒冷却)";
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelfShot(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (!(event.getDamager() instanceof org.bukkit.entity.Arrow) || !(event.getEntity() instanceof Player)) {
            return;
        }
        Projectile projectile = (Projectile) event.getDamager();
        if (!(projectile.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) projectile.getShooter();
        if (!shooter.getUniqueId().equals(event.getEntity().getUniqueId())) {
            return;
        }
        if (!isEffectActive(shooter, null)) return;
        int level = cn.charlotte.pit.util.combat.ProjectileEnchantments.level(projectile, getNbtName());
        if (level <= 0) {
            return;
        }
        if (!cooldown.getOrDefault(shooter.getUniqueId(), new Cooldown(0)).hasExpired()) {
            return;
        }
        cooldown.put(shooter.getUniqueId(), new Cooldown(3, TimeUnit.SECONDS));
        event.setCancelled(true);
        PlayerUtil.heal(shooter, (0.5 + level) * 2);
    }

    @Override
    public String getText(int level, Player player) {
        return getCooldownActionText(cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)));
    }
}
