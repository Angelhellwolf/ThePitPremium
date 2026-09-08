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
public class DangerCloseEnchant extends AbstractEnchantment implements ITickTask, IActionDisplayEnchant {

    private static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "危机四伏";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "danger_close_enchant";
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
        int hearts = enchantLevel == 1 ? 3 : 4;
        int duration = enchantLevel * 3;
        return "&7生命值降至 &c" + hearts + "❤ &7及以下时获得 &b速度 III &f(00:0" + duration + ") &7(10秒冷却)";
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        double threshold = (enchantLevel == 1 ? 3 : 4) * 2;
        if (player.getHealth() <= threshold && cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)).hasExpired()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * enchantLevel * 3, 2), true);
            cooldown.put(player.getUniqueId(), new Cooldown(10, TimeUnit.SECONDS));
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
