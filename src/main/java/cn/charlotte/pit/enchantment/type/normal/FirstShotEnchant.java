package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
@AutoRegister
public class FirstShotEnchant extends AbstractEnchantment implements IPlayerShootEntity, Listener {

    private static final Set<String> hitPairs = new HashSet<>();

    @Override
    public String getEnchantName() {
        return "第一箭";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "first_shot_enchant";
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
        return "&7对同一名玩家射出的第一支箭造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 10;
            case 2:
                return 16;
            default:
                return 25;
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        String key = attacker.getUniqueId() + ":" + target.getUniqueId();
        if (!hitPairs.contains(key)) {
            hitPairs.add(key);
            boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
        }
    }

    public static void clearFor(UUID player) {
        hitPairs.removeIf(key -> key.startsWith(player.toString()) || key.endsWith(player.toString()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearFor(event.getEntity().getUniqueId());
    }
}
