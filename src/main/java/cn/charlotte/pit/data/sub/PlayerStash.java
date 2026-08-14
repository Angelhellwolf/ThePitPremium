package cn.charlotte.pit.data.sub;

import cn.charlotte.pit.util.inventory.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerStash {
    public static final int PAGE_SIZE = 10;
    public static final int SLOT_SIZE = 45;

    private List<String> pages = new ArrayList<>();

    public PlayerStash() {
        ensurePages();
    }

    public Inventory createPageInventory(int page) {
        int pageIndex = normalizePage(page);
        Inventory inventory = Bukkit.createInventory(null, SLOT_SIZE, "寄存处 #" + (pageIndex + 1));
        ItemStack[] content = InventoryUtil.stringToItems(pages.get(pageIndex));
        if (content != null) {
            for (int i = 0; i < Math.min(content.length, SLOT_SIZE); i++) {
                inventory.setItem(i, content[i]);
            }
        }
        return inventory;
    }

    public void savePageInventory(int page, Inventory inventory) {
        int pageIndex = normalizePage(page);
        ItemStack[] content = new ItemStack[SLOT_SIZE];
        for (int i = 0; i < SLOT_SIZE; i++) {
            content[i] = inventory.getItem(i);
        }
        pages.set(pageIndex, InventoryUtil.itemsToString(content));
    }

    public List<String> getPages() {
        ensurePages();
        return pages;
    }

    public void setPages(List<String> pages) {
        this.pages = pages == null ? new ArrayList<>() : pages;
        ensurePages();
    }

    private int normalizePage(int page) {
        if (page < 0) {
            return 0;
        }
        if (page >= PAGE_SIZE) {
            return PAGE_SIZE - 1;
        }
        return page;
    }

    private void ensurePages() {
        if (pages == null) {
            pages = new ArrayList<>();
        }
        while (pages.size() < PAGE_SIZE) {
            pages.add(InventoryUtil.itemsToString(new ItemStack[SLOT_SIZE]));
        }
        if (pages.size() > PAGE_SIZE) {
            pages = new ArrayList<>(pages.subList(0, PAGE_SIZE));
        }
    }
}
