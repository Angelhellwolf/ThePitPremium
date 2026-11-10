package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.item.type.mythic.MythicSwordItem;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.random.RandomUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;
import java.util.stream.Collectors;

@WeaponOnly
public class HiddenJewelEnchant extends AbstractEnchantment implements IPlayerKilledEntity {

    @Override
    public String getEnchantName() {
        return "隐藏的宝石";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "hidden_jewel_sword";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7使用此剑在不死亡的情况下击杀 &e117 &7名玩家后,"
                + "/s&7此剑将变为带有随机 &dIII &7阶非资源附魔的 &eI 阶神话之剑"
                + "/s&7进度单独保存在此剑中，死亡重置，返回出生点不重置";
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        ItemStack hand = myself.getItemInHand();
        if (hand == null || hand.getType() != Material.GOLD_SWORD) {
            return;
        }
        IMythicItem item = MythicUtil.getMythicItem(hand);
        if (item == null || getItemEnchantLevel(hand) <= 0) {
            return;
        }
        int kills = item.getHiddenJewelKills() + 1;
        item.setHiddenJewelKills(kills);
        myself.setItemInHand(item.toItemStack());
        if (kills < 117) {
            return;
        }
        List<AbstractEnchantment> pool = ThePit.getInstance().getEnchantmentFactor().getEnchantments()
                .stream()
                .filter(enchant -> enchant.canApply(hand))
                .filter(enchant -> enchant.getRarity() == EnchantmentRarity.NORMAL || enchant.getRarity() == EnchantmentRarity.RARE)
                .filter(enchant -> !(enchant instanceof HiddenJewelEnchant))
                .filter(enchant -> !RESOURCE_ENCHANTS.contains(enchant.getNbtName()))
                .filter(enchant -> enchant.getMaxEnchantLevel() >= 3)
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            return;
        }
        AbstractEnchantment chosen = cn.charlotte.pit.reward.RareSupply.chooseEnchantmentFromKill(pool, myself, target);
        if (chosen == null) return;
        MythicSwordItem sword = new MythicSwordItem();
        sword.loadFromItemStack(hand);
        sword.setHiddenJewelKills(0);
        sword.getEnchantments().clear();
        sword.getEnchantments().put(chosen, Math.min(3, chosen.getMaxEnchantLevel()));
        sword.setTier(1);
        sword.setMaxLive(RandomUtil.hasSuccessfullyByChance(0.5) ? 5 : 3);
        sword.setLive(sword.getMaxLive());
        myself.setItemInHand(sword.toItemStack());
        myself.sendMessage(CC.translate("&d&l隐藏的宝石! &7你的神话之剑觉醒了!"));
    }

    private static final java.util.Set<String> RESOURCE_ENCHANTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "moctezuma_enchant", "hermes", "gold_explorer_enchant", "wisdom", "sweaty_enchant", "xp_bump_enchant",
            "combo_xp_enchant", "strike_gold_enchant", "critically_rich_enchant", "sierra_enchant", "pants_radar",
            "pit_pocket_enchant", "miners_enchant"));
}
