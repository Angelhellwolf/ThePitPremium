package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.enchantment.type.normal.MoctezumaEnchant;
import cn.charlotte.pit.item.EnchantmentLoreRefresh;
import cn.charlotte.pit.item.type.mythic.MythicSwordItem;
import cn.charlotte.pit.util.MythicUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantmentLoreRefreshTest {
    @Test public void existingDescriptionUpdatesWithoutReplacingTheItemOrItsCustomLore() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.hasItemMeta()).thenReturn(true); when(item.getItemMeta()).thenReturn(meta); when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(Arrays.asList("§7生命: §a10§7/10", " ", "§9蒙特祖马 II§r", "§7旧说明", " ", "§8绑定玩家：Example"));
        MythicSwordItem sword = new MythicSwordItem(); sword.getEnchantments().put(new MoctezumaEnchant(), 2);
        try (MockedStatic<MythicUtil> items = mockStatic(MythicUtil.class)) {
            items.when(() -> MythicUtil.getMythicItem(item)).thenReturn(sword);
            assertTrue(EnchantmentLoreRefresh.refresh(item));
            ArgumentCaptor<List> lore = ArgumentCaptor.forClass(List.class); verify(meta).setLore(lore.capture());
            assertTrue(lore.getValue().contains("§8绑定玩家：Example"));
            assertTrue(lore.getValue().contains("§7生命: §a10§7/10"));
            assertTrue(lore.getValue().stream().anyMatch(line -> line.toString().contains("Bot")));
            assertFalse(lore.getValue().contains("§7旧说明"));
            verify(item).setItemMeta(meta);
        }
    }
}
