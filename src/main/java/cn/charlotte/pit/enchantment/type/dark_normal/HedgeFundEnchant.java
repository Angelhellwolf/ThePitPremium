package cn.charlotte.pit.enchantment.type.dark_normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerBeKilledByEntity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/15 20:40
 */
@ArmorOnly
public class HedgeFundEnchant extends AbstractEnchantment implements IPlayerKilledEntity, IPlayerBeKilledByEntity {
    @Override
    public String getEnchantName() {
        return "避险基金";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "hedge_fund_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return ("&7击杀获得 &6+6 硬币 &7."
                + "/s&7击杀穿着已附魔 &d神话之甲 &7的玩家改为获得 &6+66 硬币 &7."
                + "/s&7被玩家击杀时失去最多 &650 硬币&7.") + "/s" + "&7金币与经验加成排除 Bot";
    }

    private boolean isEnchantedMysticPants(Player targetPlayer) {
        if (targetPlayer.getInventory().getLeggings() == null) {
            return false;
        }
        org.bukkit.inventory.ItemStack pants = targetPlayer.getInventory().getLeggings();
        if (!pants.getType().name().contains("LEATHER")) {
            return false;
        }
        return cn.charlotte.pit.util.item.ItemUtil.getInternalName(pants) != null
                && "mythic_leggings".equals(cn.charlotte.pit.util.item.ItemUtil.getInternalName(pants))
                && cn.charlotte.pit.util.item.ItemUtil.getItemIntData(pants, "tier") != null
                && cn.charlotte.pit.util.item.ItemUtil.getItemIntData(pants, "tier") >= 1;
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        coins.getAndAdd(6);
        Player targetPlayer = (Player) target;
        if (isEnchantedMysticPants(targetPlayer)) {
            coins.getAndAdd(60);
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerBeKilledByEntity(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        cn.charlotte.pit.data.PlayerProfile profile = cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(myself.getUniqueId());
        cn.charlotte.pit.reward.CoinLedger.set(profile, Math.max(0, profile.getCoins() - 50), cn.charlotte.pit.reward.CoinLedger.Flow.SPEND, "hedge_fund_death");
    }
}
