package cn.charlotte.pit.menu.stash;

import cn.charlotte.pit.listener.StashListener;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class StashMainMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    @Override
    public String getTitle(Player player) {
        return "寄存处";
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        for (int i = 0; i < SLOTS.length; i++) {
            final int page = i;
            buttons.put(SLOTS[i], new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    return new ItemBuilder(Material.CHEST)
                            .name("&e寄存箱 #" + (page + 1))
                            .lore("&7容量: &f45 格", "&e点击打开")
                            .build();
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                    player.closeInventory();
                    StashListener.openStashPage(player, page);
                }

                @Override
                public boolean shouldUpdate(Player player, int slot, ClickType clickType) {
                    return false;
                }
            });
        }
        return buttons;
    }

    @Override
    public int getSize() {
        return 4 * 9;
    }
}
