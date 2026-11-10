package cn.charlotte.pit.menu.viewer.button;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerStash;
import cn.charlotte.pit.menu.viewer.StatusViewerMenu;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only paginated viewer for another player's stash.
 */
public class StashViewerMenu extends Menu {

    private final PlayerProfile profile;
    private final int page;

    public StashViewerMenu(PlayerProfile profile) {
        this(profile, 0);
    }

    private StashViewerMenu(PlayerProfile profile, int page) {
        this.profile = profile;
        this.page = Math.max(0, Math.min(PlayerStash.PAGE_SIZE - 1, page));
    }

    @Override
    public String getTitle(Player player) {
        return "寄存箱查看 #" + (page + 1);
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        Inventory stashPage = profile.getStash().createPageInventory(page);

        // Register every content slot, including empty ones, so the menu remains
        // strictly read-only even when the viewer is carrying an item on the cursor.
        for (int slot = 0; slot < PlayerStash.SLOT_SIZE; slot++) {
            ItemStack content = stashPage.getItem(slot);
            ItemStack display = content == null ? new ItemStack(Material.AIR) : content.clone();
            buttons.put(slot, new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    return InventoryViewerMenu.forViewer(player, profile, display);
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                }
            });
        }

        for (int slot = PlayerStash.SLOT_SIZE; slot < getSize(); slot++) {
            buttons.put(slot, Button.placeholder(Material.STAINED_GLASS_PANE, (byte) 15, " "));
        }

        if (page > 0) {
            buttons.put(48, new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    return new ItemBuilder(Material.ARROW)
                            .name("&a上一页")
                            .lore("&7前往寄存箱 #" + page)
                            .build();
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                    Button.playNeutral(player);
                    new StashViewerMenu(profile, page - 1).openMenu(player);
                }
            });
        }

        buttons.put(49, new Button() {
            @Override
            public ItemStack getButtonItem(Player player) {
                return new ItemBuilder(Material.BARRIER)
                        .name("&c返回玩家档案")
                        .build();
            }

            @Override
            public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                Button.playNeutral(player);
                new StatusViewerMenu(profile).openMenu(player);
            }
        });

        if (page < PlayerStash.PAGE_SIZE - 1) {
            buttons.put(50, new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    return new ItemBuilder(Material.ARROW)
                            .name("&a下一页")
                            .lore("&7前往寄存箱 #" + (page + 2))
                            .build();
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
                    Button.playNeutral(player);
                    new StashViewerMenu(profile, page + 1).openMenu(player);
                }
            });
        }

        return buttons;
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }
}
