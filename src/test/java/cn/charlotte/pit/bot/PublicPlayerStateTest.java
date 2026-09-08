package cn.charlotte.pit.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicPlayerStateTest {
    @Test public void twoAcceptedCyclesRequireCombatDeathsAndTwoReturnJumps() {
        PublicPlayerState state = new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0, UUID.randomUUID(), "MapleRush", "MapleRush"));
        Player player = mock(Player.class); when(player.getLocation()).thenReturn(new Location(null, 0, 107, 0));
        state.born(null, player, 0, 25);
        state.enteredPit(100); state.hitHuman();
        assertTrue(state.claimDeath()); assertFalse(state.claimDeath());
        assertEquals(0, state.completedLoops());
        state.born(null, player, 200, 25); state.enteredPit(300);
        assertEquals(1, state.completedLoops());
        state.hitHuman(); assertTrue(state.claimDeath());
        state.born(null, player, 400, 25); state.enteredPit(500);
        assertEquals(2, state.completedLoops());
        assertEquals(3, state.jumps); assertEquals(2, state.deaths);
    }
    @Test public void patrolsRecoveriesAndUnopposedDeathsDoNotFakeCombatAcceptance() {
        PublicPlayerState state = new PublicPlayerState(new PublicPlayerIdentityStore.Identity(1, UUID.randomUUID(), "YukiHaze", "YukiHaze"));
        Player player = mock(Player.class); when(player.getLocation()).thenReturn(new Location(null, 0, 107, 0));
        for (int i = 0; i < 3; i++) { state.born(null, player, i * 100, 25); state.enteredPit(i * 100 + 20); state.claimDeath(); }
        assertEquals(0, state.completedLoops());
    }
}
