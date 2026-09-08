package cn.charlotte.pit.enchantment.type.dark_normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/15 19:51
 */
@ArmorOnly
public class MiseryEnchant extends AbstractEnchantment implements IAttackEntity, IPlayerShootEntity, IPlayerDamaged {
    @Override
    public String getEnchantName() {
        return "苦难";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "misery_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7攻击穿着 &6皮革裤子 &7的玩家时额外造成 &c0.5❤ &7的&f真实&7伤害./s&7受到攻击时自身受到 &c0.3❤ &7的&f真实&7伤害.";
    }

    private boolean isLeatherPants(Player player) {
        return player.getInventory().getLeggings() != null
                && player.getInventory().getLeggings().getType() == Material.LEATHER_LEGGINGS;
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Player targetPlayer = (Player) target;
        if (!cancel.get() && damage > 0 && isLeatherPants(targetPlayer)) {
            // Settle with the original hit: direct nested damage can lose the hit's
            // attribution or be overwritten during the outer damage calculation.
            finalDamage.addAndGet(1D);
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        Player targetPlayer = (Player) target;
        if (!cancel.get() && damage > 0 && isLeatherPants(targetPlayer)) {
            // Settle with the original hit: direct nested damage can lose the hit's
            // attribution or be overwritten during the outer damage calculation.
            finalDamage.addAndGet(1D);
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        PlayerUtil.damage(myself, PlayerUtil.DamageType.TRUE, 0.6D, false);
    }
}
