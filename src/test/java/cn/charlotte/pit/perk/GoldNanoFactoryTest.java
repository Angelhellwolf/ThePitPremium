package cn.charlotte.pit.perk;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitStreakKillChangeEvent;
import cn.charlotte.pit.perk.type.streak.highlander.GoldNanoFactoryKillStreak;
import cn.charlotte.pit.reward.KillRewardContext;
import cn.charlotte.pit.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class GoldNanoFactoryTest {
    @Test public void aBotMilestoneKeepsHealingButDoesNotScheduleGold() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            UUID uuid = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            players.when(() -> PlayerUtil.isPlayerChosePerk(player, "gold_nano_factory_kill_streak")).thenReturn(true);
            PlayerProfile profile = new PlayerProfile(uuid, "GoldFactoryTest");
            PitStreakKillChangeEvent event = mock(PitStreakKillChangeEvent.class);
            when(event.getPlayerProfile()).thenReturn(profile);
            when(event.getFrom()).thenReturn(20.0);
            when(event.getTo()).thenReturn(21.0);
            try (KillRewardContext ignored = KillRewardContext.openBot(uuid)) {
                new GoldNanoFactoryKillStreak().onStreak(event);
            }
            verifyNoInteractions(scheduler);
            verify(player).addPotionEffect(any(PotionEffect.class), eq(false));
        }
    }
}
