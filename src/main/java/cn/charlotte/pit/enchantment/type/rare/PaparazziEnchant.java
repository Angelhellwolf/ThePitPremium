package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.util.cooldown.Cooldown;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/26 13:43
 */
@ArmorOnly
public class PaparazziEnchant extends AbstractEnchantment {
    @Override
    public String getEnchantName() {
        return "Paparazzi";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "paparazzi_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.AUCTION_LIMITED_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7参与大型事件的奖励 &e+" + (enchantLevel * 50) + "% &7(声望数向下取整)."
                + "/s&7不放大天赋“自信”提供的额外金币"
                + "/s&7每次触发扣除此物品 &c2 &7点生命，至少剩余2点才能触发.";
    }

    public static int getUsableLevel(org.bukkit.entity.Player player) {
        org.bukkit.inventory.ItemStack item = player.getInventory().getLeggings();
        int level = new PaparazziEnchant().getItemEnchantLevel(item);
        if (level <= 0 || cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant(player)) return 0;
        cn.charlotte.pit.item.IMythicItem mythic = cn.charlotte.pit.util.MythicUtil.getMythicItem(item);
        return mythic != null && mythic.isEnchanted() && mythic.getMaxLive() > 0 && mythic.getLive() >= 2 ? level : 0;
    }
}
