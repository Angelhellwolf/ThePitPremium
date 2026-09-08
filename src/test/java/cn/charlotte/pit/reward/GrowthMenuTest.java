package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.menu.growth.GrowthMenu;
import cn.charlotte.pit.perk.PerkFactory;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GrowthMenuTest {
    @Test public void everyMenuRendersOnThe18ItemFactoryWithUniqueUsefulIcons() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
            ThePit plugin = mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            EconomyV2.setMode(EconomyV2.Mode.ACTIVE);
            PlayerProfile p = new PlayerProfile(UUID.randomUUID(), "MenuCheck");
            p.getEconomyData().version = 2; p.setPrestige(50); p.setLoaded(true);
            p.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED, System.currentTimeMillis() + 60000, 1));
            PlayerProfile.getCacheProfile().put(p.getPlayerUuid(), p);
            Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(p.getPlayerUuid());
            try {
                for (GrowthMenu.Page page : GrowthMenu.Page.values()) {
                    GrowthMenu menu = new GrowthMenu(page); Set<Material> icons = new HashSet<>();
                    Map<Integer, Button> buttons = menu.getButtons(player);
                    assertFalse(page.name(), buttons.isEmpty());
                    for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
                        assertTrue(entry.getKey() >= 0 && entry.getKey() < menu.getSize());
                        ItemStack item = entry.getValue().getButtonItem(player);
                        assertNotNull(page.name(), item);
                        assertTrue(page + " duplicate " + item.getType(), icons.add(item.getType()));
                        assertTrue(item.hasItemMeta()); assertTrue(item.getItemMeta().hasDisplayName());
                        assertFalse(item.getItemMeta().getDisplayName().contains("关闭"));
                    }
                }
            } finally { PlayerProfile.getCacheProfile().remove(p.getPlayerUuid()); }
        } finally { EconomyV2.setMode(EconomyV2.Mode.OFF); }
    }
}
