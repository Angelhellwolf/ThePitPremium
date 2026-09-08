package cn.charlotte.pit.util.reward;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreakRewardMathTest {
    @Test
    public void fractionalAssistsGrantOnlyOnCrossingANewMilestone() {
        assertEquals(1, StreakRewardMath.crossedMilestones(9.8, 10.1, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(10.1, 10.9, 10));
        assertEquals(1, StreakRewardMath.crossedMilestones(19.7, 20.2, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(20, 20, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(21, 0, 10));
    }

    @Test
    public void roundingAtDecimalAssistBoundaryDoesNotLoseOrRepeatMilestone() {
        assertEquals(1, StreakRewardMath.crossedMilestones(9.9, 9.999999999999998, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(9.999999999999998, 10.1, 10));
    }

    @Test
    public void milestoneBonusesHaveWholeStepsAndCaps() {
        assertEquals(0, StreakRewardMath.superStreakerBonus(9.99), 0.000001);
        assertEquals(0.05, StreakRewardMath.superStreakerBonus(10), 0.000001);
        assertEquals(0.05, StreakRewardMath.superStreakerBonus(19.9), 0.000001);
        assertEquals(0.5, StreakRewardMath.superStreakerBonus(1000), 0.000001);
        assertEquals(0, StreakRewardMath.hermitBonus(59.9), 0.000001);
        assertEquals(0.05, StreakRewardMath.hermitBonus(60), 0.000001);
        assertEquals(0.7, StreakRewardMath.hermitBonus(190), 0.000001);
        assertEquals(0.75, StreakRewardMath.hermitBonus(200), 0.000001);
        assertEquals(0.75, StreakRewardMath.hermitBonus(1000), 0.000001);
    }

    @Test
    public void invalidAndDecreasingStreaksCannotAwardMilestones() {
        assertEquals(0, StreakRewardMath.crossedMilestones(0, Double.NaN, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(0, Double.POSITIVE_INFINITY, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(20, 10, 10));
        assertEquals(0, StreakRewardMath.crossedMilestones(0, 100, 0));
    }
}
