package cn.charlotte.pit.menu.shop.button.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.menu.shop.button.AbstractShopButton;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/** One consumed device records one safe public-arena respawn, including the Pit's soft deaths. */
@AutoRegister
public final class TacticalDeploymentShopButton extends AbstractShopButton implements Listener {
    private static final String UNLOCK = "tactical_insertions_perk";
    private static final String ITEM = "tactical_deployment";

    @Override public String getInternalName() { return ITEM; }
    @Override public int getPrice(Player player) { return 100; }
    @Override public ItemStack getDisplayButtonItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.getUnlockedPerkMap().containsKey(UNLOCK)) return new ItemStack(Material.AIR);
        return new ItemBuilder(Material.BLAZE_ROD).name("&a战术重部署装置")
                .lore("&7右键记录当前战区位置", "&7下次复活返回此处，使用时消耗", "&7适用于公共战区的安全落脚点",
                        "&7大型活动和军需处使用各自复活点", " ",
                        "&7价格: &6" + getDiscountPrice(player, getPrice(player)) + " 硬币", "&e点击购买")
                .build();
    }
    @Override public ItemStack[] getResultItem(Player player) {
        return new ItemStack[]{new ItemBuilder(Material.BLAZE_ROD).name("&a战术重部署装置")
                .lore("&7右键记录当前战区位置", "&7下次复活返回此处，使用时消耗", "&7落点失效时返回安全区")
                .internalName(ITEM).deathDrop(true).canDrop(true).canTrade(false).build()};
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getItemInHand();
        if (held == null || !ITEM.equals(ItemUtil.getInternalName(held))) return;
        event.setCancelled(true);
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded() || profile.isBot() || !profile.getUnlockedPerkMap().containsKey(UNLOCK)) return;
        if (profile.getEconomyData().deployment != null) {
            player.sendMessage(CC.translate("&e你已经设置了下一次复活的重部署点。")); return;
        }
        if (!profile.isInArena() || !validDestination(player.getLocation())) {
            player.sendMessage(CC.translate("&c请站在公共战区的安全地面上设置重部署点。")); return;
        }
        if (ThePit.getInstance().getEventFactory().getActiveEpicEvent() != null) {
            player.sendMessage(CC.translate("&c大型活动期间无法设置重部署点。")); return;
        }
        EconomyData.Deployment point = new EconomyData.Deployment();
        Location at = player.getLocation();
        point.world = at.getWorld().getUID().toString();
        point.x = at.getX(); point.y = at.getY(); point.z = at.getZ(); point.yaw = at.getYaw(); point.pitch = at.getPitch();
        profile.getEconomyData().deployment = point;
        if (held.getAmount() <= 1) player.setItemInHand(null);
        else { ItemStack remainder = held.clone(); remainder.setAmount(held.getAmount() - 1); player.setItemInHand(remainder); }
        player.sendMessage(CC.translate("&a重部署点已设置，下次复活将返回这里。"));
    }

    public static boolean validDestination(Location at) {
        if (at == null || at.getWorld() == null || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY())
                || !Double.isFinite(at.getZ()) || at.getY() < 1 || at.getY() >= 254) return false;
        ThePit plugin = ThePit.getInstance();
        List<Location> spawns = plugin.getPitConfig().getSpawnLocations();
        if (spawns.isEmpty() || at.getWorld() != spawns.get(0).getWorld()) return false;
        Location a = plugin.getPitConfig().getPitLocA(), b = plugin.getPitConfig().getPitLocB();
        if (a != null && b != null && at.getX() >= Math.min(a.getX(), b.getX()) && at.getX() <= Math.max(a.getX(), b.getX())
                && at.getY() >= Math.min(a.getY(), b.getY()) && at.getY() <= Math.max(a.getY(), b.getY())
                && at.getZ() >= Math.min(a.getZ(), b.getZ()) && at.getZ() <= Math.max(a.getZ(), b.getZ())) return false;
        org.bukkit.block.Block feet = at.getBlock(), head = at.clone().add(0, 1, 0).getBlock(), floor = at.clone().add(0, -1, 0).getBlock();
        return feet.getType() == Material.AIR && head.getType() == Material.AIR
                && floor.getType().isSolid() && floor.getType() != Material.CACTUS;
    }

    /** Removes the marker before validation, so fallback or repeated respawn callbacks cannot reuse it. */
    public static Location consumeRespawn(Player player, Location fallback) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        EconomyData.Deployment point = profile.getEconomyData().deployment;
        profile.getEconomyData().deployment = null;
        if (point == null || point.world == null) return fallback;
        try {
            World world = Bukkit.getWorld(UUID.fromString(point.world));
            Location destination = new Location(world, point.x, point.y, point.z, point.yaw, point.pitch);
            if (world == player.getWorld() && ThePit.getInstance().getEventFactory().getActiveEpicEvent() == null
                    && validDestination(destination)) return destination;
        } catch (IllegalArgumentException ignored) { }
        player.sendMessage(CC.translate("&e重部署点已失效，本次使用正常复活点。"));
        return fallback;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(consumeRespawn(event.getPlayer(), event.getRespawnLocation()));
    }
}
