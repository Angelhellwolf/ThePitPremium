package cn.charlotte.pit.enchantment.type.dark_rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.random.RandomUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@ArmorOnly
@cn.charlotte.pit.parm.AutoRegister
public class HeartripperEnchant extends AbstractEnchantment implements org.bukkit.event.Listener {

    @Override
    public String getEnchantName() {
        return "裂心者";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "heartripper_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7击杀时每消耗目标神话物品1点生命,此裤子获得1枚血晶"
                + "/s&7集齐 &c27 &7枚血晶后生成一条随机颜色的新鲜神话之甲,并消耗此裤子2点生命"
                + "/s&7仅统计真人实际损失的生命（排除 Bot 和保护成功的物品）"
                + "/s&7血晶保存在此裤子中；至少剩余2点生命才能转换";
    }

    @org.bukkit.event.EventHandler
    public void onLivesLost(cn.charlotte.pit.event.PitItemLivesLostEvent event) {
        Player myself = event.getKiller();
        if (myself == null || event.getLostLives() <= 0 || myself.getUniqueId().equals(event.getVictim().getUniqueId())) return;
        cn.charlotte.pit.data.PlayerProfile victim = cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(event.getVictim().getUniqueId());
        if (!victim.isLoaded() || victim.isBot() || !isEffectActive(myself, event.getVictim())) return;
        ItemStack leggings = myself.getInventory().getLeggings();
        if (getItemEnchantLevel(leggings) <= 0) return;
        IMythicItem own = MythicUtil.getMythicItem(leggings);
        if (own == null || own.getLive() <= 0) return;
        int current = (int) Math.min(Integer.MAX_VALUE, (long) own.getHeartripperShards() + event.getLostLives());
        int created = 0;
        while (current >= 27 && own.getLive() >= 2) {
            current -= 27;
            own.setLive(own.getLive() - 2);
            MythicLeggingsItem fresh = new MythicLeggingsItem();
            fresh.setColor((MythicColor) RandomUtil.helpMeToChooseOne(MythicColor.RED, MythicColor.ORANGE, MythicColor.BLUE, MythicColor.GREEN, MythicColor.YELLOW));
            fresh.setTier(0);
            fresh.setMaxLive(RandomUtil.random.nextInt(7) + 3);
            fresh.setLive(fresh.getMaxLive());
            for (ItemStack overflow : myself.getInventory().addItem(fresh.toItemStack()).values()) {
                myself.getWorld().dropItemNaturally(myself.getLocation(), overflow);
            }
            created++;
        }
        own.setHeartripperShards(current);
        myself.getInventory().setLeggings(own.getLive() > 0 ? own.toItemStack() : new ItemStack(org.bukkit.Material.AIR));
        if (created > 0) myself.sendMessage(CC.translate("&5&l裂心者! &7你获得了 &f" + created + " &7条新鲜神话之甲。"));
    }
}
