package cn.charlotte.pit.enchantment.type.dark_normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerAssist;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@ArmorOnly
public class LycanthropyEnchant extends AbstractEnchantment implements ITickTask, IPlayerKilledEntity, IPlayerAssist {

    @Override
    public String getEnchantName() {
        return "狼人";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "lycanthropy_enchant";
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
        return ("&7夜晚时获得永久 &b速度 I &7与 &c生命恢复 II"
                + "/s&7并获得 &6+10% 硬币 &7与 &b+10% 经验"
                + "/s&7白天不提供上述自动药水与收益加成.") + "/s" + "&7金币与经验加成排除 Bot";
    }

    private boolean isNight(Player player) {
        World world = player.getWorld();
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (isNight(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1), true);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        if (isNight(myself)) {
            coins.getAndAdd(coins.get() * 0.10);
            experience.getAndAdd(experience.get() * 0.10);
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerAssist(int enchantLevel, Player myself, Entity target, double damage, double finalDamage, AtomicDouble coins, AtomicDouble experience) {
        if (isNight(myself)) {
            coins.getAndAdd(coins.get() * 0.10);
            experience.getAndAdd(experience.get() * 0.10);
        }
    }
}
