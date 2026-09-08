package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ArmorOnly
public class RevitalizeEnchant extends AbstractEnchantment implements ITickTask, IActionDisplayEnchant {

    private static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "焕发";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "revitalize_enchant";
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
        String speed = enchantLevel == 1 ? "I" : "II";
        String regen = enchantLevel == 1 ? "II" : "III";
        int speedTime = enchantLevel >= 3 ? 7 : 5;
        int regenTime = enchantLevel >= 3 ? 5 : 3;
        return "&7生命值降至 &c3❤ &7及以下时获得 &b速度 " + speed + " &f(00:0" + speedTime + ") &7与 &c生命恢复 " + regen + " &f(00:0" + regenTime + ") &7(45秒冷却)";
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (player.getHealth() <= 6 && cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)).hasExpired()) {
            int speedAmp = enchantLevel == 1 ? 0 : 1;
            int regenAmp = enchantLevel == 1 ? 1 : 2;
            int speedTime = enchantLevel >= 3 ? 7 : 5;
            int regenTime = enchantLevel >= 3 ? 5 : 3;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * speedTime, speedAmp), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * regenTime, regenAmp), true);
            cooldown.put(player.getUniqueId(), new Cooldown(45, TimeUnit.SECONDS));
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 5;
    }

    @Override
    public String getText(int level, Player player) {
        return getCooldownActionText(cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)));
    }
}
