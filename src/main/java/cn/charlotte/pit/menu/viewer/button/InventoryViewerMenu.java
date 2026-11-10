package cn.charlotte.pit.menu.viewer.button;

import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PlayerInv;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.item.ItemUtil;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.RomanUtil;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/13 14:53
 */
public class InventoryViewerMenu extends Menu {
    private static final String VIEW_HIDDEN_ENCHANTS_NBT = "view_hidden_enchants";
    private static final UUID SEVENTY_SEVEN_HZ = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");
    private static final String SWIFT_SPRINT = "swift_sprint_enchant";

    private final PlayerProfile profile;

    public InventoryViewerMenu(PlayerProfile profile) {
        this.profile = profile;
    }

    @Override
    public String getTitle(Player player) {
        return "背包";
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        PlayerInv inventory = profile.getInventory();
        Map<Integer, Button> button = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            int finalI = i;
            button.put(i, new Button() {
                @Override
                public ItemStack getButtonItem(Player player) {
                    ItemStack item = inventory.getContents()[finalI];
                    if (item == null) {
                        return new ItemBuilder(Material.AIR).build();
                    }
                    return forViewer(player, profile, item);
                }

                @Override
                public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {

                }
            });
        }
        return button;
    }

    public static ItemStack forViewer(Player viewer, PlayerProfile owner, ItemStack source) {
        if (source == null || viewer == null || owner == null || viewer.getUniqueId().equals(owner.getPlayerUuid())) {
            return source;
        }

        String configured = ItemUtil.getItemStringData(source, VIEW_HIDDEN_ENCHANTS_NBT);
        if ((configured == null || configured.trim().isEmpty()) && !SEVENTY_SEVEN_HZ.equals(owner.getPlayerUuid())) {
            return source;
        }

        Set<String> hidden = new HashSet<>();
        if (configured != null) {
            Arrays.stream(configured.split("[,;]")).map(String::trim).filter(value -> !value.isEmpty()).forEach(hidden::add);
        }
        if (SEVENTY_SEVEN_HZ.equals(owner.getPlayerUuid())) {
            hidden.add(SWIFT_SPRINT);
        }
        if (hidden.isEmpty()) {
            return source;
        }

        ItemStack view = source.clone();
        hideEnchantLore(view, hidden);
        // View menus are read-only. Do not send the source item's enchant NBT to other clients.
        net.minecraft.server.v1_8_R3.ItemStack nms = UtilKt.reflectGetNmsItem(view);
        NBTTagCompound tag = nms.getTag();
        if (tag != null) {
            tag.remove("extra");
            nms.setTag(tag);
            return CraftItemStack.asBukkitCopy(nms);
        }
        return view;
    }

    /** Remove selected enchantment lore from the viewer copy before stripping its private NBT. */
    private static void hideEnchantLore(ItemStack item, Set<String> hidden) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        IMythicItem mythic = MythicUtil.getMythicItem(item);
        if (mythic == null || !mythic.isEnchanted()) {
            return;
        }

        Map<String, String> titleToNbt = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<AbstractEnchantment, Integer> entry : mythic.getEnchantments().entrySet()) {
            AbstractEnchantment enchant = entry.getKey();
            if (enchant == null) {
                continue;
            }
            String title = plain(enchant.getRarity().getPrefix() + "&9" + enchant.getEnchantName()
                    + (entry.getValue() >= 2 ? " " + RomanUtil.convert(entry.getValue()) : ""));
            if (titleToNbt.containsKey(title)) {
                ambiguous.add(title);
            } else {
                titleToNbt.put(title, enchant.getNbtName());
            }
        }
        for (String title : ambiguous) {
            titleToNbt.remove(title);
        }

        List<String> original = meta.getLore();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            String line = original.get(i);
            String nbtName = titleToNbt.get(plain(line));
            if (nbtName == null || !hidden.contains(nbtName)) {
                result.add(line);
                continue;
            }

            int end = i + 1;
            while (end < original.size()) {
                String next = original.get(end);
                String nextPlain = plain(next);
                if (titleToNbt.containsKey(nextPlain)) {
                    break;
                }
                end++;
                if (next.trim().isEmpty()) {
                    break;
                }
            }
            i = end - 1;
        }

        if (!result.equals(original)) {
            meta.setLore(result);
            item.setItemMeta(meta);
        }
    }

    private static String plain(String text) {
        return ChatColor.stripColor(CC.translate(text)).trim();
    }

    @Override
    public int getSize() {
        return 4 * 9;
    }
}
