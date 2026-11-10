package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class DuelistEnchant extends AbstractEnchantment implements IPlayerDamaged, IAttackEntity {
    @Override
    public String getEnchantName() {
        return "决斗者";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "duelist_enchant";
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
        return "&7格挡同一名玩家的两次攻击后,下次对其的近战攻击恢复自身 &c" + (0.5 * enchantLevel) + "❤ &7并造成的伤害 &c+" + getBoostPercent(enchantLevel) + "%";
    }

    private int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 20;
            case 2:
                return 40;
            default:
                return 75;
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(attacker instanceof Player) || !myself.isBlocking()) {
            return;
        }
        String key = "duelist_" + attacker.getUniqueId();
        int count = myself.hasMetadata(key) ? myself.getMetadata(key).get(0).asInt() : 0;
        count++;
        if (count >= 2) {
            myself.setMetadata("duelist_ready_" + attacker.getUniqueId(), new FixedMetadataValue(ThePit.getInstance(), true));
            count = 0;
        }
        myself.setMetadata(key, new FixedMetadataValue(ThePit.getInstance(), count));
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        String ready = "duelist_ready_" + target.getUniqueId();
        if (attacker.hasMetadata(ready)) {
            attacker.removeMetadata(ready, ThePit.getInstance());
            PlayerUtil.heal(attacker, enchantLevel);
            boostDamage.getAndAdd(getBoostPercent(enchantLevel) * 0.01);
        }
    }
}
