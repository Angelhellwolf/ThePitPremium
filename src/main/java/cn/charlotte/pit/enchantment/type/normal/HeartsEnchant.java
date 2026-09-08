package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.entity.Player;

@ArmorOnly
public class HeartsEnchant extends AbstractEnchantment implements ITickTask {
    @Override
    public String getEnchantName() {
        return "心之加护";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "hearts_enchant";
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
        return "&7最大生命值 &c+" + getHearts(enchantLevel) + "❤";
    }

    private double getHearts(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 0.25;
            case 2:
                return 0.5;
            default:
                return 1.0;
        }
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        double extra = getHearts(enchantLevel) * 2;
        Double current = profile.getExtraMaxHealth().get("hearts_enchant");
        if (current == null || Math.abs(current - extra) > 0.01) {
            profile.getExtraMaxHealth().put("hearts_enchant", extra);
            player.setMaxHealth(profile.getMaxHealth());
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 40;
    }
}
