package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/23 19:33
 */
@WeaponOnly
public class BruiserEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "斗士";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "bruiser_enchant";
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
        return "&7格挡时降低受到的普通类型初始伤害 &c" + (0.5 * enchantLevel + (enchantLevel >= 3 ? 0.5 : 0) + "❤");
    }

    /** Called at the combat listener's existing settlement stage. */
    public static void reduceDamage(EntityDamageByEntityEvent event, Player wearer, Player attacker) {
        if (event.isCancelled() || !wearer.isBlocking() || PlayerUtil.shouldIgnoreEnchant(wearer, attacker)) return;
        int level = Utils.getEnchantLevel(wearer.getItemInHand(), "bruiser_enchant");
        if (level > 0) {
            double reduction = level >= 3 ? 4.0 : level;
            event.setDamage(Math.max(0, event.getDamage() - reduction));
        }
    }
}
