package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.bot.JunxuTrainingBots;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.supporter.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.*;
import org.mockito.MockedStatic;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JunxuLifecycleTest {
    private MockedStatic<Bukkit> bukkit;private MockedStatic<JunxuMenu> menus;private MockedStatic<PlayerProfile> profiles;
    private JunxuManager manager;private JunxuRoom room;private JunxuLedger ledger;private World world;
    private JunxuTrainingBots bots;private JunxuNpcs npcs;private Player owner;private Path file;
    @Before public void setup()throws Exception{
        bukkit=mockStatic(Bukkit.class);menus=mockStatic(JunxuMenu.class);profiles=mockStatic(PlayerProfile.class);
        ThePit plugin=mock(ThePit.class);java.util.logging.Logger logger=java.util.logging.Logger.getAnonymousLogger();logger.setLevel(java.util.logging.Level.OFF);when(plugin.getLogger()).thenReturn(logger);
        manager=spy(new JunxuManager(plugin));world=mock(World.class);bots=mock(JunxuTrainingBots.class);npcs=mock(JunxuNpcs.class);
        file=Files.createTempDirectory("junxu-lifecycle").resolve("ledger.dat");ledger=new JunxuLedger(file,ZoneId.of("Asia/Shanghai"));
        UUID id=UUID.randomUUID();long now=System.currentTimeMillis();JunxuLedger.Entry entry=ledger.create(id,"Owner");
        room=new JunxuRoom(id,"Owner",entry.slot,world,SupporterTier.MYTHIC);room.account=new SupporterAccount(true,false,SupporterTier.MYTHIC,now+3600000,1);
        ledger.open(id,now-65000,14400000,room.account.getExpiresAt());
        set("world",world);set("ledger",ledger);set("bots",bots);set("npcs",npcs);set("story",mock(JunxuStory.class));set("ready",true);set("absenceSeconds",60);
        @SuppressWarnings("unchecked") Map<UUID,JunxuRoom> rooms=(Map<UUID,JunxuRoom>)get("rooms");rooms.put(id,room);
        @SuppressWarnings("unchecked") Map<Integer,JunxuRoom> slots=(Map<Integer,JunxuRoom>)get("slots");slots.put(room.slot,room);
        owner=mock(Player.class);when(owner.getUniqueId()).thenReturn(id);when(owner.isOnline()).thenReturn(true);when(owner.getLocation()).thenReturn(room.arrival());
        PlayerProfile profile=mock(PlayerProfile.class);when(profile.isLoaded()).thenReturn(true);when(profile.getSupporterAccount()).thenReturn(room.account);
        profiles.when(()->PlayerProfile.getPlayerProfileByUuid(id)).thenReturn(profile);
        when(world.getPlayers()).thenReturn(Collections.emptyList());when(world.getEntities()).thenReturn(Collections.emptyList());
    }
    @After public void cleanup()throws Exception{
        if(profiles!=null)profiles.close();if(menus!=null)menus.close();if(bukkit!=null)bukkit.close();
        if(file!=null){Files.deleteIfExists(file);Files.deleteIfExists(file.resolveSibling("ledger.dat.tmp"));Files.deleteIfExists(file.getParent());}
    }
    private void tick()throws Exception{Method m=JunxuManager.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(manager);}
    private void set(String name,Object value)throws Exception{Field f=JunxuManager.class.getDeclaredField(name);f.setAccessible(true);f.set(manager,value);}
    private Object get(String name)throws Exception{Field f=JunxuManager.class.getDeclaredField(name);f.setAccessible(true);return f.get(manager);}
    @Test public void aVisitorCannotKeepAnOfflineOwnersRoomOpen()throws Exception{
        Player guest=mock(Player.class);UUID guestId=UUID.randomUUID();when(guest.getUniqueId()).thenReturn(guestId);when(guest.getLocation()).thenReturn(room.arrival());
        room.guests.add(guestId);when(world.getPlayers()).thenReturn(Collections.singletonList(guest),Collections.emptyList());
        doNothing().when(manager).sendBack(guest);room.absentSince=System.currentTimeMillis()-61000;
        tick();assertFalse(room.open);assertNull(manager.room(room.owner));assertFalse(ledger.running(room.owner));
        verify(manager).sendBack(guest);verify(bots).close(room);verify(npcs).close(room);assertTrue(room.guests.isEmpty());
    }
    @Test public void anOnlineOwnerOutsideTheRoomStillTimesOut()throws Exception{
        bukkit.when(()->Bukkit.getPlayer(room.owner)).thenReturn(owner);
        when(owner.getLocation()).thenReturn(new Location(mock(World.class),0,100,0));room.absentSince=System.currentTimeMillis()-61000;
        tick();assertFalse(room.open);verify(bots).close(room);
    }
    @Test public void returningBeforeTheGraceEndsKeepsTheSameOpening()throws Exception{
        bukkit.when(()->Bukkit.getPlayer(room.owner)).thenReturn(owner);room.absentSince=System.currentTimeMillis()-59000;
        UUID session=room.session;tick();assertTrue(room.open);assertEquals(-1,room.absentSince);assertEquals(session,room.session);assertTrue(ledger.running(room.owner));
        verify(bots,never()).close(room);
    }
}
