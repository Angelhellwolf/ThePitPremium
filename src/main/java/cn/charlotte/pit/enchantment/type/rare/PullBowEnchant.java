package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.time.TimeUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/6 22:22
 */
@cn.charlotte.pit.enchantment.param.item.BowOnly
public class PullBowEnchant extends AbstractEnchantment implements IPlayerShootEntity, IActionDisplayEnchant {

    private static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "吸力";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "pullbow_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7箭矢命中可以将敌人" + (enchantLevel >= 2 ? "及其周围玩家" : "") + "&7向你所在的位置拖拽"
                + "/s&7此附魔对每名玩家每 &f" + (enchantLevel == 1 ? 8 : 5) + " &7秒只能触发一次.";
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Player targetEntity = (Player) target;
        long cooldownSeconds = enchantLevel == 1 ? 8L : 5L;
        Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
            for (Player targetPlayer : PlayerUtil.getNearbyPlayers(target.getLocation(), enchantLevel >= 2 ? 4.0D : 0.5D)) {
                if (targetPlayer == attacker) continue;

                if (enchantLevel <= 1 && !targetPlayer.getUniqueId().equals(targetEntity.getUniqueId())) {
                    continue;
                }
                if (!cooldown.getOrDefault(targetPlayer.getUniqueId(), new Cooldown(0)).hasExpired()) {
                    continue;
                }
                if (targetPlayer.getLocation().distanceSquared(attacker.getLocation()) < 0.25D) {
                    continue;
                }
                Vector direction = attacker.getLocation().toVector().subtract(targetPlayer.getLocation().toVector());
                if (direction.lengthSquared() < 1.0E-6D) {
                    continue;
                }
                direction.normalize();
                if (enchantLevel >= 3) {
                    direction.multiply(1.8);
                } else if (enchantLevel == 1) {
                    direction.multiply(0.6);
                }
                if (Double.isNaN(direction.getX()) || Double.isNaN(direction.getY()) || Double.isNaN(direction.getZ())) {
                    continue;
                }
                SpiterCompatibility.exemptVelocity(targetPlayer, 30L);
                targetPlayer.setVelocity(direction);
                cooldown.put(targetPlayer.getUniqueId(), new Cooldown(cooldownSeconds, TimeUnit.SECONDS));
            }
        }, 1L);
    }

    @Override
    public String getText(int level, Player player) {
        return cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)).hasExpired() ? "&a&l✔" : "&c&l" + TimeUtil.millisToRoundedTime(cooldown.get(player.getUniqueId()).getRemaining()).replace(" ", "");
    }
}
