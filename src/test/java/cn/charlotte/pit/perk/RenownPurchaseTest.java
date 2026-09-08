package cn.charlotte.pit.perk;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RenownPurchaseTest {
    @Test public void buyingUpgradingAndInsufficientRenownUseTheActualProfile() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "MapleRush");
            profile.setLoaded(true); profile.setBot(true); profile.setPrestige(1); profile.setRenown(60); profile.init();
            Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(profile.getPlayerUuid());
            AbstractPerk perk = mock(AbstractPerk.class);
            when(perk.getInternalPerkName()).thenReturn("Tenacity");
            when(perk.requirePrestige()).thenReturn(1); when(perk.getMaxLevel()).thenReturn(2);
            when(perk.requireRenown(1)).thenReturn(10.0); when(perk.requireRenown(2)).thenReturn(50.0);
            try {
                assertTrue(RenownPurchase.purchase(player, perk));
                assertEquals(50, profile.getRenown()); verify(perk).onUnlock(player);
                profile.setRenown(49);
                assertFalse(RenownPurchase.purchase(player, perk));
                assertEquals(new PerkData("Tenacity", 1), profile.getUnlockedPerkMap().get("Tenacity"));
                assertEquals(49, profile.getRenown());
                profile.setRenown(50);
                assertTrue(RenownPurchase.purchase(player, perk));
                assertEquals(0, profile.getRenown()); verify(perk).onUpgrade(player);
                assertFalse(RenownPurchase.purchase(player, perk));
            } finally { PlayerProfile.getCacheProfile().remove(profile.getPlayerUuid()); }
        }
    }
}
