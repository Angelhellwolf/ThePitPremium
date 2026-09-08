package cn.charlotte.pit.reward;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class PassiveDeathTrackerTest {
    @Test
    public void onlyConsecutiveNoOutputCombatDeathsReduceRewards() {
        PassiveDeathTracker tracker=new PassiveDeathTracker(600_000);
        UUID victim=UUID.randomUUID();
        double[] expected={1,1,.5,.5,.2,.2};
        for(int i=0;i<expected.length;i++)assertEquals(expected[i],tracker.finishLife(victim,true,1000+i).getMultiplier(),0);
        tracker.recordOutput(victim,0.1,2000);
        assertEquals(0,tracker.finishLife(victim,true,2001).getPassiveDeaths());
        assertEquals(1,tracker.current(victim,2002).getMultiplier(),0);
        assertEquals(1,tracker.finishLife(victim,true,3000).getMultiplier(),0);
    }

    @Test
    public void activePlayersCanFightTheSameOpponentWithoutAnArbitraryKillLimit() {
        PassiveDeathTracker tracker=new PassiveDeathTracker(600_000);
        UUID victim=UUID.randomUUID();
        for(int i=0;i<100;i++){
            tracker.recordOutput(victim,4,1000+i*10);
            assertEquals(1,tracker.finishLife(victim,true,1001+i*10).getMultiplier(),0);
        }
    }

    @Test
    public void zeroAndInvalidDamageDoNotResetTheSequenceAndAssistsCannotIncrementIt() {
        PassiveDeathTracker tracker=new PassiveDeathTracker(600_000);
        UUID victim=UUID.randomUUID();
        for(int i=0;i<3;i++)tracker.finishLife(victim,true,1000+i);
        for(double damage:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})tracker.recordOutput(victim,damage,2000);
        for(int i=0;i<20;i++)assertEquals(3,tracker.current(victim,2001+i).getPassiveDeaths());
        assertEquals(.5,tracker.current(victim,2100).getMultiplier(),0);
    }

    @Test
    public void environmentalDeathsDoNotAccumulateAndOldRecordsExpire() {
        PassiveDeathTracker tracker=new PassiveDeathTracker(600_000);
        UUID victim=UUID.randomUUID();
        for(int i=0;i<20;i++)assertEquals(1,tracker.finishLife(victim,false,1000+i).getMultiplier(),0);
        assertEquals(1,tracker.finishLife(victim,true,2000).getPassiveDeaths());
        tracker.finishLife(victim,true,3000);tracker.finishLife(victim,true,4000);
        assertEquals(.5,tracker.current(victim,5000).getMultiplier(),0);
        tracker.cleanup(604000);
        assertEquals(1,tracker.finishLife(victim,true,604001).getPassiveDeaths());
    }
}
