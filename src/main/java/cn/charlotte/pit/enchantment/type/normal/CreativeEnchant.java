package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/31 21:19
 */
@ArmorOnly
public class CreativeEnchant extends AbstractEnchantment implements IPlayerKilledEntity, IPlayerRespawn {
    @Override
    public String getEnchantName() {
        return "手艺人";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "creative_enchant";
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
        return "&7每次死亡后复活立刻获得 &f" + (16 * enchantLevel) + " * &6木板"
                + "/s&7击杀玩家时获得 &f" + (6 * enchantLevel) + " * &6木板"
                + "/s&7木板放置后存在时间降低至 &e30 秒 &7且无法被延长."
                + "/s&7持有上限 &e64";
    }


    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        givePlanks(myself, 6 * enchantLevel);
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        givePlanks(myself, 16 * enchantLevel);
    }

    private void givePlanks(Player player, int amount) {
        ItemBuilder woodBuilder = new ItemBuilder(Material.WOOD).canDrop(false).canSaveToEnderChest(false).deathDrop(true).canTrade(false).removeOnJoin(true).internalName("creative_enchantment_item");
        player.getInventory().addItem(woodBuilder.amount(amount).build());
        int held = InventoryUtil.getAmountOfItem(player, "creative_enchantment_item");
        if (held > 64) {
            InventoryUtil.removeItem(player, "creative_enchantment_item", held - 64);
        }
    }
}
