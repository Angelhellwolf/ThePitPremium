package cn.charlotte.pit.listener;

import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

@AutoRegister
public class TrashCanListener implements Listener {

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!"垃圾桶".equals(inventory.getName())) {
            return;
        }
        inventory.clear();
        if (event.getPlayer() instanceof Player) {
            ((Player) event.getPlayer()).sendMessage(CC.translate("&a垃圾桶已清空."));
        }
    }
}
