package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;

@ArmorOnly
public class SteaksEnchant extends AbstractEnchantment implements IPlayerKilledEntity {

    public static ItemBuilder steakBuilder() {
        return new ItemBuilder(Material.COOKED_BEEF)
                .internalName("tasty_steak")
                .canDrop(false)
                .canSaveToEnderChest(false)
                .deathDrop(true)
                .canTrade(false)
                .removeOnJoin(true)
                .isHealingItem(true)
                .name("&6美味牛排")
                .lore(Arrays.asList("&7食用后获得:", "&c+1❤ &6生命吸收", "&c生命恢复 II &f(00:03)", "&c+4% &7伤害 (最多5层,持续50秒)", "&7再次食用刷新全部层数的持续时间"));
    }

    @Override
    public String getEnchantName() {
        return "牛排";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "steaks_enchant";
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
        return "&7击杀玩家时获得 &6美味牛排 &7而非金苹果"
                + "/s&7食用后获得 &c1❤ &6生命吸收&7、&c生命恢复 II &f(00:03)"
                + "/s&7与 &c+4% &7伤害(最多5层,持续50秒)"
                + "/s&7再次食用刷新全部层数的持续时间"
                + "/s&7持有上限 &e" + getMaxHeld(enchantLevel);
    }

    public static int getMaxHeld(int enchantLevel) {
        return enchantLevel * 2;
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        int held = InventoryUtil.getAmountOfItem(myself, "tasty_steak");
        if (held < getMaxHeld(enchantLevel)) {
            myself.getInventory().addItem(steakBuilder().amount(1).build());
        }
    }

    public static void applySteakBoost(Player attacker, AtomicDouble boostDamage) {
        if (attacker == null || !attacker.hasMetadata("tasty_steak_until") || !attacker.hasMetadata("tasty_steak_stacks")) {
            return;
        }
        long until = attacker.getMetadata("tasty_steak_until").get(0).asLong();
        if (System.currentTimeMillis() > until) {
            attacker.removeMetadata("tasty_steak_until", cn.charlotte.pit.ThePit.getInstance());
            attacker.removeMetadata("tasty_steak_stacks", cn.charlotte.pit.ThePit.getInstance());
            return;
        }
        int stacks = Math.max(1, Math.min(5, attacker.getMetadata("tasty_steak_stacks").get(0).asInt()));
        boostDamage.getAndAdd(0.04 * stacks);
    }
}
