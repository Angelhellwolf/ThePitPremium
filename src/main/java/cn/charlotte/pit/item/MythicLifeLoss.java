package cn.charlotte.pit.item;

import cn.charlotte.pit.enchantment.type.op.RealManEnchant;
import cn.charlotte.pit.item.type.LuckyChestplate;
import cn.charlotte.pit.util.MythicUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Apply protection per item and report only lives that were actually removed. */
public final class MythicLifeLoss {
    private MythicLifeLoss() {}

    public static Result onDeath(ItemStack item, boolean protectionFailed, boolean cancelled) {
        IMythicItem mythic = MythicUtil.getMythicItem(item);
        if (mythic == null) return new Result(item, 0);
        boolean progressReset = mythic.getHiddenJewelKills() > 0;
        mythic.setHiddenJewelKills(0);
        boolean unprotected = mythic instanceof LuckyChestplate
                || mythic.getEnchantments().keySet().stream().anyMatch(enchant -> enchant instanceof RealManEnchant);
        if (!cancelled && (protectionFailed || unprotected) && mythic.isEnchanted() && mythic.getLive() > 0) {
            mythic.setLive(mythic.getLive() - 1);
            return new Result(mythic.getLive() == 0 ? new ItemStack(Material.AIR) : mythic.toItemStack(), 1);
        }
        return new Result(progressReset ? mythic.toItemStack() : item, 0);
    }

    public static final class Result {
        public final ItemStack item;
        public final int lostLives;
        private Result(ItemStack item, int lostLives) { this.item = item; this.lostLives = lostLives; }
    }
}
