package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.entity.Player;

/**
 * Gotta go fast / 疾走: wearing the pants permanently increases walk speed.
 */
@ArmorOnly
public class SwiftSprintEnchant extends AbstractEnchantment implements ITickTask {

    private static final float BASE_WALK_SPEED = 0.2F;

    @Override
    public String getEnchantName() {
        return "疾走";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "swift_sprint_enchant";
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
        return "&7装备时移速 &b+" + getBoostPercent(enchantLevel) + "%";
    }

    private static int getBoostPercent(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 10;
            default:
                return 20;
        }
    }

    public static boolean isSwiftSprintSpeed(float speed) {
        for (int level = 1; level <= 3; level++) {
            float target = BASE_WALK_SPEED * (1.0F + 0.01F * getBoostPercent(level));
            if (Math.abs(speed - target) < 0.002F) {
                return true;
            }
        }
        return false;
    }

    public static void restoreDefaultWalkSpeed(Player player) {
        if (isSwiftSprintSpeed(player.getWalkSpeed())) {
            player.setWalkSpeed(BASE_WALK_SPEED);
        }
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        float target = BASE_WALK_SPEED * (1.0F + 0.01F * getBoostPercent(Math.max(1, enchantLevel)));
        if (Math.abs(player.getWalkSpeed() - target) > 0.001F) {
            player.setWalkSpeed(target);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }
}
