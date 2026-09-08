package cn.charlotte.pit.bot;

import org.bukkit.entity.Player;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PublicKnockbackWindowTest {
    @Test public void airborneKnockbackIsNotOverwrittenWhenTheEightTickWindowEnds(){
        PublicPlayerState state=new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0,UUID.randomUUID(),"Actor","Actor"));
        state.entity=mock(Player.class);state.knockbackUntil=108;
        when(state.entity.isOnGround()).thenReturn(false);
        assertTrue(PublicPlayerCombat.recoveringKnockback(state,107));assertTrue(PublicPlayerCombat.recoveringKnockback(state,112));
        when(state.entity.isOnGround()).thenReturn(true);assertFalse(PublicPlayerCombat.recoveringKnockback(state,112));
        when(state.entity.isOnGround()).thenReturn(false);assertFalse(PublicPlayerCombat.recoveringKnockback(state,121));
    }
    @Test public void normalAirborneMovementIsUnaffectedWithoutARecentHit(){
        PublicPlayerState state=new PublicPlayerState(new PublicPlayerIdentityStore.Identity(0,UUID.randomUUID(),"Actor","Actor"));
        state.entity=mock(Player.class);assertFalse(PublicPlayerCombat.recoveringKnockback(state,100));
    }
}
