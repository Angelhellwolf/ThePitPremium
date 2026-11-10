package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class McSwimmerEnchant extends AbstractEnchantment implements IPlayerDamaged, ITickTask {
    @Override
    public String getEnchantName() {
        return "游泳健将";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "mcswimmer_enchant";
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
        return "&7在水中或岩浆中受到的伤害 &9-" + getReducePercent(enchantLevel) + "%/s&7且不会溺水";
    }

    private int getReducePercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 25;
            case 2:
                return 40;
            default:
                return 60;
        }
    }

    private boolean inLiquid(Player player) {
        return player.getLocation().getBlock().isLiquid();
    }

    @Override
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (inLiquid(myself)) {
            boostDamage.getAndAdd(-getReducePercent(enchantLevel) * 0.01);
        }
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (player.getRemainingAir() < player.getMaximumAir()) {
            player.setRemainingAir(player.getMaximumAir());
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 10;
    }
}
