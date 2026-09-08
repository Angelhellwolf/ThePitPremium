package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerBeKilledByEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Effect;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class RevengeanceEnchant extends AbstractEnchantment implements IAttackEntity, IPlayerBeKilledByEntity {
    @Override
    public String getEnchantName() {
        return "复仇";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "revengeance_enchant";
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
        return "&7对上次击杀你的玩家造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 8;
            case 2:
                return 15;
            default:
                return 25;
        }
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(target instanceof Player)) {
            return;
        }
        if (attacker.hasMetadata("revengeance_target")
                && attacker.getMetadata("revengeance_target").get(0).asString().equals(target.getUniqueId().toString())) {
            boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
            ((Player) target).getWorld().playEffect(target.getLocation().add(0, 1, 0), Effect.VILLAGER_THUNDERCLOUD, 1);
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerBeKilledByEntity(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        if (target instanceof Player) {
            myself.setMetadata("revengeance_target", new FixedMetadataValue(ThePit.getInstance(), target.getUniqueId().toString()));
        }
    }
}
