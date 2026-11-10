package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
public class JumpspammerEnchant extends AbstractEnchantment implements IPlayerShootEntity, IPlayerDamaged {
    @Override
    public String getEnchantName() {
        return "跳跃射手";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "jumpspammer_enchant";
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
        String lore = "&7滞空时弓箭造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%";
        if (enchantLevel >= 2) {
            lore += "/s&7滞空时受到的伤害 &9-" + (enchantLevel >= 3 ? 20 : 10) + "%";
        }
        return lore;
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 10;
            case 2:
                return 16;
            default:
                return 24;
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!attacker.isOnGround()) {
            boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
        }
    }

    @Override
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (enchantLevel >= 2 && !myself.isOnGround()) {
            boostDamage.getAndAdd(enchantLevel >= 3 ? -0.20 : -0.10);
        }
    }
}
