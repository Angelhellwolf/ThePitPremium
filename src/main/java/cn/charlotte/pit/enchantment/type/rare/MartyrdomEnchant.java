package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerBeKilledByEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

@ArmorOnly
public class MartyrdomEnchant extends AbstractEnchantment implements IPlayerBeKilledByEntity {

    @Override
    public String getEnchantName() {
        return "殉道";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "martyrdom_enchant";
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
        int amount;
        switch (enchantLevel) {
            case 1:
                amount = 10;
                break;
            case 2:
                amount = 20;
                break;
            default:
                amount = 40;
                break;
        }
        return "&7被玩家击杀时在原地生成 &e" + amount + " &7只苦力怕";
    }

    @Override
    @PlayerOnly
    public void handlePlayerBeKilledByEntity(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        int amount;
        switch (enchantLevel) {
            case 1:
                amount = 10;
                break;
            case 2:
                amount = 20;
                break;
            default:
                amount = 40;
                break;
        }
        for (int i = 0; i < amount; i++) {
            Creeper creeper = (Creeper) myself.getWorld().spawnEntity(myself.getLocation(), EntityType.CREEPER);
            creeper.setMetadata("martyrdom", new FixedMetadataValue(ThePit.getInstance(), myself.getUniqueId().toString()));
            creeper.setPowered(false);
        }
    }
}
