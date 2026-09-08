package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicPlayerProgressionTest {
    @Test public void perksRegisteredAfterManagerStartupAreBoughtOnceAndReappliedAfterRespawn() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            PerkFactory factory = new PerkFactory();
            when(plugin.getPerkFactory()).thenReturn(factory);
            PublicPlayerProgression progression = new PublicPlayerProgression(plugin);
            AbstractPerk perk = mock(AbstractPerk.class);
            when(perk.getInternalPerkName()).thenReturn("SafetySecond");
            when(perk.getPerkType()).thenReturn(PerkType.PERK);
            when(perk.requireCoins()).thenReturn(3000.0);
            factory.getPerks().add(perk);
            UUID id = UUID.randomUUID();
            PublicPlayerState state = new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0, id, "MapleRush", "MapleRush"));
            state.profile = new PlayerProfile(id, "MapleRush");
            state.profile.setLoaded(true); state.profile.setBot(true); state.profile.init();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            try {
                progression.prepare(state, player);
                assertEquals(2000, state.profile.getCoins(), 0);
                assertEquals("SafetySecond", state.profile.getChosePerk().get(1).getPerkInternalName());
                progression.prepare(state, player);
                assertEquals(2000, state.profile.getCoins(), 0);
                verify(perk, times(2)).onPerkActive(player);
            } finally { PlayerProfile.getCacheProfile().remove(id); }
        }
    }
}
