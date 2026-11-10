package cn.charlotte.pit.menu.supporter.button;

import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public final class SupporterHistoryButton extends Button {
    @Override
    public ItemStack getButtonItem(Player player) {
        return new ItemBuilder(Material.BOOK).name("&f赞助订单")
                .lore(Arrays.asList("", "&7查看最近的购买、续费和升级记录。", "&e点击在聊天栏查看订单")).build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack item) {
        SupporterService service = SupporterService.get();
        if (service != null) service.showHistory(player);
    }
}
