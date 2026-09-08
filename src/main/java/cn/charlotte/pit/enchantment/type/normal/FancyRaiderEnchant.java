package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class FancyRaiderEnchant extends AbstractEnchantment implements IAttackEntity {
    @Override
    public String getEnchantName() {
        return "华服劫匪";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "fancy_raider_enchant";
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
        return "&7攻击穿着 &6皮革装备 &7的玩家造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 9;
            default:
                return 16;
        }
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Player victim = (Player) target;
        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (armor != null && armor.getType().name().contains("LEATHER")) {
                boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
                return;
            }
        }
    }
}
