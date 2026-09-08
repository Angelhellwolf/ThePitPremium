package cn.charlotte.pit.enchantment.type.sewer_normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.random.RandomUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;
import java.util.stream.Collectors;

@ArmorOnly
public class SewerHiddenJewelEnchant extends AbstractEnchantment implements IPlayerKilledEntity {

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
        return "hidden_jewel_pants";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.SEWER_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7穿着此裤子在不死亡的情况下击杀 &e117 &7名玩家后,"
                + "/s&7此裤子将变为带有随机 &dIII &7阶附魔的标准颜色 &eI 阶神话之甲"
                + "/s&7进度单独保存在此裤子中，死亡重置";
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        ItemStack leggings = myself.getInventory().getLeggings();
        if (getItemEnchantLevel(leggings) <= 0) {
            return;
        }
        cn.charlotte.pit.item.IMythicItem current = cn.charlotte.pit.util.MythicUtil.getMythicItem(leggings);
        if (current == null) return;
        int kills = current.getHiddenJewelKills() + 1;
        current.setHiddenJewelKills(kills);
        myself.getInventory().setLeggings(current.toItemStack());
        if (kills < 117) {
            return;
        }
        List<AbstractEnchantment> pool = ThePit.getInstance().getEnchantmentFactor().getEnchantments()
                .stream()
                .filter(enchant -> enchant.getRarity() == EnchantmentRarity.NORMAL || enchant.getRarity() == EnchantmentRarity.RARE)
                .filter(enchant -> enchant.getClass().isAnnotationPresent(ArmorOnly.class))
                .filter(enchant -> enchant.getMaxEnchantLevel() >= 3)
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            return;
        }
        AbstractEnchantment chosen = (AbstractEnchantment) RandomUtil.helpMeToChooseOne(pool.toArray());
        MythicLeggingsItem pants = new MythicLeggingsItem();
        pants.setColor((MythicColor) RandomUtil.helpMeToChooseOne(MythicColor.RED, MythicColor.ORANGE, MythicColor.BLUE, MythicColor.GREEN, MythicColor.YELLOW));
        pants.setTier(1);
        pants.setMaxLive(RandomUtil.random.nextInt(3) + 2);
        pants.setLive(pants.getMaxLive());
        pants.getEnchantments().put(chosen, Math.min(3, chosen.getMaxEnchantLevel()));
        myself.getInventory().setLeggings(pants.toItemStack());
        myself.sendMessage(CC.translate("&d&l隐藏的宝石! &7你的下水道之甲觉醒了!"));
    }
}
