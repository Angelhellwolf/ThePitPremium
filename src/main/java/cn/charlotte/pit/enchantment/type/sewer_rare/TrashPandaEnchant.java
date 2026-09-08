package cn.charlotte.pit.enchantment.type.sewer_rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

@ArmorOnly
@AutoRegister
public class TrashPandaEnchant extends AbstractEnchantment implements Listener {

    @Override
    public String getEnchantName() {
        return "浣熊";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "trash_panda_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.SEWER_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7死亡后在下水道重生"
                + "/s&7手持并右键可在任意位置打开出生点菜单"
                + "/s&7(装备超杀连杀、大型活动或 &cMagnum Opus &7时无法使用)";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (getItemEnchantLevel(player.getInventory().getLeggings()) <= 0) {
            return;
        }
        if (PlayerUtil.isPlayerChosePerk(player, "over_drive")
                || PlayerUtil.isPlayerChosePerk(player, "high_lander")
                || PlayerUtil.isPlayerChosePerk(player, "beast_mode_mega_streak")
                || PlayerUtil.isPlayerChosePerk(player, "hermit")
                || PlayerUtil.isPlayerChosePerk(player, "uber_streak")
                || PlayerUtil.isPlayerChosePerk(player, "to_the_moon")) {
            return;
        }
        if (ThePit.getInstance().getEventFactory().getActiveEpicEvent() != null) {
            return;
        }
        PitConfig config = ThePit.getInstance().getPitConfig();
        if (config.getSewersChestsLocations() != null && !config.getSewersChestsLocations().isEmpty()) {
            Location loc = config.getSewersChestsLocations().get(0).clone().add(0.5, 1, 0.5);
            event.setRespawnLocation(loc);
        }
    }
}
