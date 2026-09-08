package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.chat.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JunxuMessagesTest {
    @Test public void privateStreakAndPerkNoticesStayWithTheOwnerAndGuest(){
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);MockedStatic<ThePit> pit=mockStatic(ThePit.class);MockedStatic<CC> chat=mockStatic(CC.class)){
            ThePit plugin=mock(ThePit.class);pit.when(ThePit::getInstance).thenReturn(plugin);JunxuManager manager=mock(JunxuManager.class);when(plugin.getJunxuManager()).thenReturn(manager);
            World world=mock(World.class);Player owner=mock(Player.class),guest=mock(Player.class),outsider=mock(Player.class);
            UUID ownerId=UUID.randomUUID(),guestId=UUID.randomUUID();when(owner.getUniqueId()).thenReturn(ownerId);when(guest.getUniqueId()).thenReturn(guestId);when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
            JunxuRoom room=new JunxuRoom(ownerId,"Owner",1,world,SupporterTier.SACRED);room.guests.add(guestId);
            bukkit.when(()->Bukkit.getPlayer(ownerId)).thenReturn(owner);when(owner.getWorld()).thenReturn(world);when(owner.getLocation()).thenReturn(room.arrival());
            when(manager.isWorld(world)).thenReturn(true);when(manager.at(any(Location.class))).thenReturn(room);when(manager.occupants(room)).thenReturn(Arrays.asList(owner,guest,outsider));
            assertEquals(Arrays.asList(owner,guest),JunxuMessages.audience(ownerId));
            JunxuMessages.broadcast(ownerId,MessageType.COMBAT,"activated");
            chat.verify(()->CC.send(MessageType.COMBAT,owner,"activated"));chat.verify(()->CC.send(MessageType.COMBAT,guest,"activated"));
            chat.verify(()->CC.boardCast(MessageType.COMBAT,"activated"),never());chat.verify(()->CC.send(MessageType.COMBAT,outsider,"activated"),never());
        }
    }
    @Test public void publicStreakAnnouncementsKeepTheirExistingDelivery(){
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);MockedStatic<ThePit> pit=mockStatic(ThePit.class);MockedStatic<CC> chat=mockStatic(CC.class)){
            ThePit plugin=mock(ThePit.class);pit.when(ThePit::getInstance).thenReturn(plugin);JunxuManager manager=mock(JunxuManager.class);when(plugin.getJunxuManager()).thenReturn(manager);
            UUID id=UUID.randomUUID();Player actor=mock(Player.class);bukkit.when(()->Bukkit.getPlayer(id)).thenReturn(actor);
            JunxuMessages.broadcast(id,MessageType.STREAK,"streak");chat.verify(()->CC.boardCast(MessageType.STREAK,"streak"));
        }
    }
}
