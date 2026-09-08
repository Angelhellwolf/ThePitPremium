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

@ArmorOnly
public class EggsEnchant extends AbstractEnchantment implements IPlayerKilledEntity, IPlayerRespawn {

    private ItemBuilder eggBuilder() {
        return new ItemBuilder(Material.EGG)
                .internalName("eggs_enchant_item")
                .canDrop(false)
                .canSaveToEnderChest(false)
                .deathDrop(true)
                .canTrade(false)
                .removeOnJoin(true)
                .name("&f鸡蛋");
    }

    @Override
    public String getEnchantName() {
        return "鸡蛋";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "eggs_enchant";
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
        return "&7每次死亡后复活立刻获得 &f" + getSpawnAmount(enchantLevel) + " * 鸡蛋"
                + "/s&7击杀玩家时获得 &f" + getKillAmount(enchantLevel) + " * 鸡蛋 &7(持有上限16)";
    }

    private int getSpawnAmount(int enchantLevel) {
        return enchantLevel >= 3 ? 16 : 8;
    }

    private int getKillAmount(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 4;
            case 2:
                return 8;
            default:
                return 16;
        }
    }

    private void giveEggs(Player player, int amount) {
        int held = InventoryUtil.getAmountOfItem(player, "eggs_enchant_item");
        int give = Math.min(amount, 16 - held);
        if (give > 0) {
            player.getInventory().addItem(eggBuilder().amount(give).build());
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        giveEggs(myself, getKillAmount(enchantLevel));
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        giveEggs(myself, getSpawnAmount(enchantLevel));
    }
}
