package cn.charlotte.pit.enchantment.type.sewer_normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@ArmorOnly
public class WormEnchant extends AbstractEnchantment implements ITickTask, IPlayerKilledEntity {

    @Override
    public String getEnchantName() {
        return "蠕虫";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "worm_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.SEWER_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return ("&7身处下水道时获得永久 &b速度 I &7,击杀经验 &b+20%") + "/s" + "&7击杀 Bot 不享受经验加成，速度效果正常";
    }

    public static boolean inSewers(Player player) {
        PitConfig config = ThePit.getInstance().getPitConfig();
        if (config.getSewersChestsLocations() == null || config.getSewersChestsLocations().isEmpty()) {
            return player.getLocation().getY() < 40;
        }
        for (Location loc : config.getSewersChestsLocations()) {
            if (loc.getWorld() != null && loc.getWorld().equals(player.getWorld()) && loc.distanceSquared(player.getLocation()) <= 40 * 40) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (inSewers(player)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0), true);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        if (inSewers(myself)) {
            experience.getAndAdd(experience.get() * 0.20);
        }
    }
}
