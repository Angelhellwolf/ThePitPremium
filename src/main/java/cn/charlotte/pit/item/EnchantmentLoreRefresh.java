package cn.charlotte.pit.item;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.RomanUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

/** Replace canonical enchant description blocks without reconstructing items or deleting third-party metadata. */
@AutoRegister
public final class EnchantmentLoreRefresh implements Listener {
    public static boolean refresh(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        IMythicItem mythic = MythicUtil.getMythicItem(item);
        if (mythic == null || !mythic.isEnchanted()) return false;
        Map<String, String[]> descriptions = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<AbstractEnchantment, Integer> entry : mythic.getEnchantments().entrySet()) {
            AbstractEnchantment enchant = entry.getKey();
            if (enchant == null) continue;
            String title = plain(enchant.getRarity().getPrefix() + "&9" + enchant.getEnchantName()
                    + (entry.getValue() >= 2 ? " " + RomanUtil.convert(entry.getValue()) : ""));
            if (descriptions.containsKey(title)) ambiguous.add(title);
            descriptions.put(title, enchant.getEconomyLore(entry.getValue()).split("/s", -1));
        }
        ambiguous.forEach(descriptions::remove);
        List<String> original = meta.getLore();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            String line = original.get(i);
            result.add(line);
            String[] description = descriptions.get(plain(line));
            if (description == null) continue;
            int end = i + 1;
            while (end < original.size() && !original.get(end).trim().isEmpty()
                    && !descriptions.containsKey(plain(original.get(end)))) end++;
            // Items with compressed or manually edited tooltips are kept intact.
            if (end == i + 1 || end == original.size()) continue;
            for (String current : description) result.add(CC.translate("&7" + current));
            i = end - 1;
        }
        if (result.equals(original)) return false;
        meta.setLore(result);
        item.setItemMeta(meta);
        return true;
    }

    private static String plain(String text) { return ChatColor.stripColor(CC.translate(text)).trim(); }

    private void refreshInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (refresh(item)) inventory.setItem(slot, item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileLoaded(PitProfileLoadedEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
        if (player == null) return;
        refreshInventory(player.getInventory());
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (ItemStack item : armor) changed |= refresh(item);
        if (changed) player.getInventory().setArmorContents(armor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) { refreshInventory(event.getInventory()); }
}
