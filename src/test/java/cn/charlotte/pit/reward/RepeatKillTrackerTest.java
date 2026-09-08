package cn.charlotte.pit.reward;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatKillTrackerTest {
    private final UUID attacker = UUID.randomUUID();
    private final UUID victim = UUID.randomUUID();
    @Test public void assistingFourTimesDoesNotConsumeTheFirstKillReward() {
        RepeatKillTracker tracker = new RepeatKillTracker(600000);
        for (int i=0; i<4; i++) {
            assertEquals(1, tracker.forAssist(attacker, victim, i).getMultiplier(), 0);
        }
        assertEquals(1, tracker.recordKill(attacker, victim, 5).getMultiplier(), 0);
    }
    @Test public void repeatedKillsHaveExplicitProgressionCutoff() {
        RepeatKillTracker tracker = new RepeatKillTracker(600000);
        double[] expected = {1, .75, .5, .25, .05};
        for (int i=0; i<expected.length; i++) {
            RepeatKillTracker.Result result = tracker.recordKill(attacker, victim, i);
            assertEquals(expected[i], result.getMultiplier(), 0);
            assertEquals(i < 4, result.isProgressionEligible());
        }
    }
    @Test public void oldKillsExpireEvenWhenNewKillsKeepArriving() {
        RepeatKillTracker tracker = new RepeatKillTracker(600000);
        for (int i=0; i<4; i++) tracker.recordKill(attacker, victim, i * 100000L);
        assertFalse(tracker.recordKill(attacker, victim, 400000).isProgressionEligible());
        // At 900k, only 400k, 600k and 900k remain in the rolling window.
        tracker.recordKill(attacker, victim, 600000);
        RepeatKillTracker.Result result = tracker.recordKill(attacker, victim, 900000);
        assertTrue(result.isProgressionEligible());
        assertEquals(.5, result.getMultiplier(), 0);
    }
    @Test public void forgettingDestroyedBotRemovesBothSidesOfPairState() {
        RepeatKillTracker tracker = new RepeatKillTracker(600000);
        tracker.recordKill(attacker, victim, 0);
        tracker.recordKill(victim, attacker, 0);
        tracker.forget(victim);
        assertEquals(1, tracker.recordKill(attacker, victim, 1).getMultiplier(), 0);
        assertEquals(1, tracker.recordKill(victim, attacker, 1).getMultiplier(), 0);
    }
}
