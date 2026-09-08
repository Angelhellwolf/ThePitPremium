package cn.charlotte.pit.junxu;

import cn.charlotte.pit.supporter.*;
import org.bukkit.*;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JunxuAccessTest {
    @Test public void onlyCurrentHighestPaidContractCreatesRoom(){
        long now=1000;
        assertEquals(SupporterTier.NONE,JunxuEntitlements.tier((SupporterAccount)null,now));
        assertEquals(SupporterTier.NONE,JunxuEntitlements.tier(new SupporterAccount(true,false,SupporterTier.NONE,0,0),now));
        assertEquals(SupporterTier.NONE,JunxuEntitlements.tier(new SupporterAccount(false,false,SupporterTier.SACRED,10000,0),now));
        assertEquals(SupporterTier.MYTHIC,JunxuEntitlements.tier(new SupporterAccount(true,false,SupporterTier.MYTHIC,1001,0),now));
        assertEquals(SupporterTier.NONE,JunxuEntitlements.tier(new SupporterAccount(true,false,SupporterTier.SACRED,1000,0),now));
        assertEquals(14400000,JunxuEntitlements.limit(SupporterTier.MYTHIC));assertEquals(28800000,JunxuEntitlements.limit(SupporterTier.SACRED));
        assertEquals(0,JunxuEntitlements.bots(SupporterTier.NONE));assertEquals(5,JunxuEntitlements.bots(SupporterTier.MYTHIC));assertEquals(10,JunxuEntitlements.bots(SupporterTier.SACRED));
        assertFalse(JunxuEntitlements.invite(SupporterTier.MYTHIC));assertTrue(JunxuEntitlements.invite(SupporterTier.SACRED));
    }
    @Test public void roomsHaveIndependentAccessAndSpatialBounds(){
        World world=mock(World.class);UUID a=UUID.randomUUID(),b=UUID.randomUUID(),guest=UUID.randomUUID();
        JunxuRoom first=new JunxuRoom(a,"A",1,world,SupporterTier.SACRED),second=new JunxuRoom(b,"B",2,world,SupporterTier.SACRED);
        assertTrue(first.permits(a));assertFalse(first.permits(b));assertFalse(first.permits(guest));first.guests.add(guest);assertTrue(first.permits(guest));assertFalse(second.permits(guest));
        assertTrue(first.contains(first.arrival()));assertFalse(second.contains(first.arrival()));assertFalse(first.arena(first.arrival()));
        assertTrue(first.arena(first.at(20.5,23,42.5,0)));assertFalse(first.arena(first.at(20.5,23,33.99,0)));
        first.open=false;assertFalse(first.permits(a));assertFalse(first.permits(guest));assertTrue(second.open);
    }
    @Test public void invitationRejectsOtherRecipientsStaleSessionsAndExactExpiry(){
        JunxuInvitations inbox=new JunxuInvitations();UUID owner=UUID.randomUUID(),recipient=UUID.randomUUID(),other=UUID.randomUUID(),session=UUID.randomUUID();
        inbox.send(owner,recipient,session,60000);
        assertNotNull(inbox.get(recipient,owner,session,59999));assertNull(inbox.get(other,owner,session,1));
        assertNull(inbox.get(recipient,owner,UUID.randomUUID(),1));assertNull(inbox.get(recipient,owner,session,60000));
        inbox.expire(60000);assertEquals(0,inbox.size());
    }
    @Test public void declineAndClosureInvalidateOnlyTheirInvitation(){
        JunxuInvitations inbox=new JunxuInvitations();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),guest=UUID.randomUUID(),session=UUID.randomUUID();
        inbox.send(a,guest,session,100);inbox.send(b,guest,session,100);
        assertTrue(inbox.remove(guest,a));assertNotNull(inbox.get(guest,b,session,1));
        inbox.send(a,guest,session,100);inbox.invalidate(a);assertNull(inbox.get(guest,a,session,1));assertNotNull(inbox.get(guest,b,session,1));
    }
}
