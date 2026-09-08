package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/8 20:30
 */
@ArmorOnly
public class FractionalReserveEnchant extends AbstractEnchantment implements IPlayerDamaged {
    @Override
    public String getEnchantName() {
        return "天坑储备金";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "fractional_reserve_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.AUCTION_LIMITED;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7你每持有 &610,000 硬币 &7,受到的伤害 &9-1% &7(可叠加,最高" + getMaxPercent(enchantLevel) + "%)";
    }

    private int getMaxPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 15;
            case 2:
                return 21;
            default:
                return 30;
        }
    }

    @Override
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(myself.getUniqueId());
        if (profile.getCoins() >= 10000) {
            double reduce = Math.min(getMaxPercent(enchantLevel) * 0.01, 0.01 * (profile.getCoins() / 10000.0));
            boostDamage.getAndAdd(-reduce);
        }
    }
}
