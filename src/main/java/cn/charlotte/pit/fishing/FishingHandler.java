package cn.charlotte.pit.fishing;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.random.RandomUtil;
import net.minecraft.server.v1_8_R3.EntityItem;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * @Author: EmptyIrony
 * @Date: 2021/2/6 20:12
 */
@AutoRegister
public class FishingHandler implements Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onFishing(PlayerFishEvent event) {
        if (!PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId()).isLoaded()) return;
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (event.getCaught() == null || event.getCaught().hasMetadata("pit_fishing_processed")) return;
            event.getCaught().setMetadata("pit_fishing_processed", new FixedMetadataValue(ThePit.getInstance(), true));
            if (cn.charlotte.pit.reward.EconomyV2.active() || cn.charlotte.pit.reward.EconomyV2.observing()) {
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
                double side = cn.charlotte.pit.reward.ProgressionV2.sideScale(profile.getPrestige());
                cn.charlotte.pit.reward.EconomyV2.grant(profile, cn.charlotte.pit.reward.RewardDetail.FISH_CATCH,
                        0, 0, 10 * side, 40 * side);
            }
            event.setExpToDrop(0);
            event.getCaught().remove();

            Location location = event.getCaught().getLocation();

            ItemStack itemStack = this.generateItem(event.getPlayer(), location);
            EntityItem entityItem = this.generateEntity(event.getPlayer(), location, itemStack);
            CraftEntity item = entityItem.getBukkitEntity();
            if (itemStack.getType() == Material.GOLD_INGOT) {
                int gold = RandomUtil.random.nextInt(5) + 5;
                item.setMetadata("gold", new FixedMetadataValue(ThePit.getInstance(), gold));
                Player player = event.getPlayer();
                if (!cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant(player)
                        && cn.charlotte.pit.util.Utils.getEnchantLevel(player.getInventory().getLeggings(), "trophy") > 0) {
                    for (int i = 0; i < 2; i++) {
                        generateEntity(player, location, new ItemStack(Material.GOLD_INGOT)).getBukkitEntity()
                                .setMetadata("gold", new FixedMetadataValue(ThePit.getInstance(), gold));
                    }
                }
            }
        } else if (event.getState() == PlayerFishEvent.State.FISHING) {
            ItemStack item = event.getPlayer().getItemInHand();
            if (!cn.charlotte.pit.util.PlayerUtil.shouldIgnoreEnchant(event.getPlayer())) {
                int lure = cn.charlotte.pit.util.Utils.getEnchantLevel(item, "lure");
                if (lure > 0) event.getHook().setBiteChance(Math.min(1, event.getHook().getBiteChance() * (1 + Math.min(3, lure) * 0.5)));
            }
        }
    }


    private EntityItem generateEntity(Player player, Location dropLocation, ItemStack item) {
        WorldServer worldServer = ((CraftWorld) dropLocation.getWorld()).getHandle();
        EntityItem entityItem = new EntityItem(worldServer, dropLocation.getX(), dropLocation.getY(), dropLocation.getZ(), CraftItemStack.asNMSCopy(item));
        worldServer.addEntity(entityItem);

        double d5 = player.getLocation().getX() - dropLocation.getX();
        double d6 = player.getLocation().getY() - dropLocation.getY();
        double d7 = player.getLocation().getZ() - dropLocation.getZ();
        double d8 = MathHelper.sqrt(d5 * d5 + d6 * d6 + d7 * d7);
        double d9 = 0.1D;

        entityItem.motX = d5 * d9;
        entityItem.motY = d6 * d9 + (double) MathHelper.sqrt(d8) * 0.08D;
        entityItem.motZ = d7 * d9;

        return entityItem;
    }

    private ItemStack generateItem(Player player, Location location) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (RandomUtil.hasSuccessfullyByChance(0.008)) {
            return new ItemBuilder(Material.DIAMOND_SWORD).canSaveToEnderChest(false).canTrade(false)
                    .canDrop(false).deathDrop(true).removeOnJoin(false).name("&6钻石剑")
                    .internalName("fishing_dia_armor").buildWithUnbreakable();
        }
        return new ItemBuilder(Material.GOLD_INGOT)
                .build();
    }

}
