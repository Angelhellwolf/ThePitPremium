package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.mockito.Mockito.*;

public class JunxuReturnTest {
    @Test public void aSafeReturnEndsTrainingCombatInsteadOfPenalizingTheNextLogout() {
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);MockedStatic<PlayerProfile> profiles=mockStatic(PlayerProfile.class)){
            ThePit plugin=mock(ThePit.class);JunxuManager manager=spy(new JunxuManager(plugin));
            Player player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
            PlayerProfile profile=mock(PlayerProfile.class);profiles.when(()->PlayerProfile.getPlayerProfileByUuid(id)).thenReturn(profile);
            when(profile.getDamageMap()).thenReturn(new java.util.HashMap<>());
            Location safe=new Location(mock(World.class),10,107,-6);doReturn(safe).when(manager).safeReturn();
            when(player.teleport(safe)).thenReturn(true);
            manager.sendBack(player);
            verify(profile).setInArena(false);verify(profile).setCombatTimer(argThat(timer->timer.hasExpired()));
            verify(player).setFallDistance(0);verify(player).setGameMode(GameMode.ADVENTURE);
        }
    }
}
