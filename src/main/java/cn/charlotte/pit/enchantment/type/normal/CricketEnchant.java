package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class CricketEnchant extends AbstractEnchantment implements IPlayerDamaged, ITickTask {
    @Override
    public String getEnchantName() {
        return "蟋蟀";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "cricket_enchant";
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
        return "&7你或攻击者站在草方块上时受到的伤害 &9-" + getReducePercent(enchantLevel) + "%"
                + "/s&7站在草方块上时获得永久 &c生命恢复 I";
    }

    private int getReducePercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 7;
            default:
                return 15;
        }
    }

    private boolean isOnGrass(Player player) {
        Block block = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        return block.getType() == Material.GRASS;
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        boolean selfGrass = isOnGrass(myself);
        boolean attackerGrass = attacker instanceof Player && isOnGrass((Player) attacker);
        if (selfGrass || attackerGrass) {
            boostDamage.getAndAdd(-getReducePercent(enchantLevel) * 0.01);
        }
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (isOnGrass(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0), true);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }
}
