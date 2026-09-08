package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class PrickEnchant extends AbstractEnchantment implements IPlayerDamaged {
    @Override
    public String getEnchantName() {
        return "倒刺";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "prick_enchant";
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
        return "&7受到攻击时对攻击者造成 &c" + getTrueHearts(enchantLevel) + "❤ &7的&f真实&7伤害";
    }

    private double getTrueHearts(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 0.25;
            case 2:
                return 0.375;
            default:
                return 0.5;
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (attacker instanceof Player) {
            PlayerUtil.damage(myself, (Player) attacker, PlayerUtil.DamageType.TRUE, getTrueHearts(enchantLevel) * 2, true);
        }
    }
}
