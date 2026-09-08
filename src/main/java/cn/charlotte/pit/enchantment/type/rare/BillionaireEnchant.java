package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/27 14:39
 */
@WeaponOnly
public class BillionaireEnchant extends AbstractEnchantment implements IAttackEntity {

    @Override
    public String getEnchantName() {
        return "亿万富翁";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "billionaire";
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
        return "&7近战造成的伤害 &c+" + getBoostPercent(enchantLevel) + "% /s&7但每次使用扣除自身 &6" + getCost(enchantLevel) + " 硬币";
    }

    private int getCost(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 100;
            case 2:
                return 200;
            default:
                return 350;
        }
    }

    private double getBoost(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 1.0 / 3.0;
            case 2:
                return 2.0 / 3.0;
            default:
                return 1.0;
        }
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 33;
            case 2:
                return 67;
            default:
                return 100;
        }
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (cancel.get() || damage <= 0) return;
        if (target instanceof Player && cn.charlotte.pit.util.Utils.getEnchantLevel(((Player) target).getInventory().getLeggings(), "new_deal") > 0
                && !cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant((Player) target, attacker)) return;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        int cost = getCost(enchantLevel);
        if (profile.getCoins() >= cost) {
            boostDamage.getAndAdd(getBoost(enchantLevel));
            cn.charlotte.pit.util.combat.CombatContext.afterAccepted(() -> profile.setCoins(Math.max(0, profile.getCoins() - cost)));
        }
    }
}
