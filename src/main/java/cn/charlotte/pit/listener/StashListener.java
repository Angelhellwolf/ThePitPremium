package cn.charlotte.pit.listener;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AutoRegister
public class StashListener implements Listener {

    private static final Map<UUID, Integer> OPENED_STASH_PAGE = new HashMap<>();

    public static void openStashPage(Player player, int page) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        OPENED_STASH_PAGE.put(player.getUniqueId(), page);
        player.openInventory(profile.getStash().createPageInventory(page));
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        Integer page = OPENED_STASH_PAGE.remove(player.getUniqueId());
        if (page == null) {
            return;
        }
        if (!event.getInventory().getName().startsWith("寄存处 #")) {
            return;
        }
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        profile.getStash().savePageInventory(page, event.getInventory());
        player.sendMessage(CC.translate("&a寄存处已保存."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OPENED_STASH_PAGE.remove(event.getPlayer().getUniqueId());
    }
}
