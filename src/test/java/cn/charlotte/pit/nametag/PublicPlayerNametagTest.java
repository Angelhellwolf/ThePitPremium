package cn.charlotte.pit.nametag;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.bot.PitBotManager;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.nametag.BufferedNametag;
import cn.charlotte.pit.util.rank.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicPlayerNametagTest {
    @Test public void offRosterSimulatedPlayerGetsTheSameLevelAndMembershipTagAsAnOnlinePlayer() {
        List<Player> roster = new ArrayList<>();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<RankUtil> ranks = mockStatic(RankUtil.class); MockedStatic<PlayerUtil> utilities = mockStatic(PlayerUtil.class)) {
            ThePit plugin = mock(ThePit.class); pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getEventFactory()).thenReturn(mock(EventFactory.class));
            when(plugin.getPitConfig()).thenReturn(mock(PitConfig.class));
            PitBotManager manager = mock(PitBotManager.class); when(plugin.getPitBotManager()).thenReturn(manager);
            when(plugin.getJunxuManager()).thenReturn(mock(cn.charlotte.pit.junxu.JunxuManager.class));
            List<PlayerProfile> profiles = new ArrayList<>();
            List<Player> entities = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), i == 0 ? "HumanPlayer" : "MapleRush");
                profile.setPrestige(2); profile.setExperience(LevelUtil.getLevelTotalExperience(2, 40));
                profile.setBot(i == 1); profile.setLoaded(true);
                profile.applySupporterAccount(new SupporterAccount(true, true, SupporterTier.NONE, 0, 1));
                profile.getPlayerOption().setSupporterStarDisplay(true); profile.init(); profiles.add(profile);
                Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(profile.getPlayerUuid());
                when(player.getName()).thenReturn(profile.getPlayerName()); when(player.isOnline()).thenReturn(i == 0); entities.add(player);
                ranks.when(() -> RankUtil.getPlayerRankColor(profile.getPlayerUuid())).thenReturn("§7");
                if (i == 1) {
                    when(manager.ownsProfile(profile)).thenReturn(true);
                    when(manager.getPlayer(profile.getPlayerUuid())).thenReturn(player);
                }
            }
            roster.add(entities.get(0));
            try {
                List<BufferedNametag> result = new NameTagImpl(() -> roster).getPlate(entities.get(0));
                assertEquals(2, result.size());
                assertEquals(result.get(0).getPrefix(), result.get(1).getPrefix());
                assertEquals(result.get(0).getSuffix(), result.get(1).getSuffix());
                assertTrue(result.get(1).getSuffix().contains("§e✬"));
                assertTrue(result.stream().anyMatch(tag -> tag.getPlayer() == entities.get(1)));
                assertEquals(2,new NameTagImpl(() -> roster).getPlate(null).size());
            } finally { for (PlayerProfile profile : profiles) PlayerProfile.getCacheProfile().remove(profile.getPlayerUuid()); }
        }
    }
}
