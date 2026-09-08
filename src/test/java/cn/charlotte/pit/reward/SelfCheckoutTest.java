package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.type.normal.SelfCheckoutEnchant;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SelfCheckoutTest {
    @Test public void cashoutRequiresAConsumableLifeAndCanOnlyConsumeTheBountyOnce() {
        for (int lives : new int[]{0, 1}) {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                 MockedStatic<ItemUtil> items = mockStatic(ItemUtil.class);
                 MockedConstruction<MythicLeggingsItem> construction = mockConstruction(MythicLeggingsItem.class, (item, context) -> {
                     when(item.isEnchanted()).thenReturn(true);
                     when(item.getMaxLive()).thenReturn(10);
                     when(item.getLive()).thenReturn(lives);
                 })) {
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "CheckoutTest");
                profile.setLoaded(true); profile.setCoins(0); profile.setBounty(5000);
                PlayerProfile.getCacheProfile().put(profile.getPlayerUuid(), profile);
                try {
                    Player player = mock(Player.class);
                    when(player.getUniqueId()).thenReturn(profile.getPlayerUuid());
                    PlayerInventory inventory = mock(PlayerInventory.class);
                    ItemStack leggings = mock(ItemStack.class);
                    when(player.getInventory()).thenReturn(inventory);
                    when(inventory.getLeggings()).thenReturn(leggings);
                    items.when(() -> ItemUtil.getInternalName(leggings)).thenReturn("mythic_leggings");
                    SelfCheckoutEnchant enchant = new SelfCheckoutEnchant();
                    enchant.handle(3, player);
                    enchant.handle(3, player);
                    assertEquals(lives == 0 ? 0 : 5000, profile.getCoins(), 0);
                    assertEquals(lives == 0 ? 5000 : 0, profile.getBounty());
                    if (lives == 0) verify(inventory, never()).setLeggings(any(ItemStack.class));
                    else verify(inventory).setLeggings(any(ItemStack.class));
                } finally { PlayerProfile.getCacheProfile().remove(profile.getPlayerUuid()); }
            }
        }
    }
}
