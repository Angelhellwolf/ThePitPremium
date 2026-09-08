package cn.charlotte.pit.bot;

import cn.charlotte.pit.junxu.JunxuRoom;
import cn.charlotte.pit.supporter.SupporterTier;
import org.bukkit.*;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JunxuFollowTest {
    @Test public void followingApproachesThePlayerButCannotEnterTheServiceArea(){
        JunxuRoom room=new JunxuRoom(UUID.randomUUID(),"Owner",4,mock(World.class),SupporterTier.SACRED);
        Location target=room.at(30,23,42,0);assertEquals(target,JunxuTrainingBots.followDestination(room,target));
        for(Location outside:new Location[]{room.at(12,23,18,0),room.at(54,32,12,0),room.at(60,23,60,0)})
            assertTrue(room.arena(JunxuTrainingBots.followDestination(room,outside)));
    }
}
