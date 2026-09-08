package cn.charlotte.pit.reward;

import org.junit.Test;
import static org.junit.Assert.*;

public class RewardMathTest {
    @Test public void beginnerBoundaryRequiresFirstPrestigeAndExcludesBots() {
        assertTrue(RewardMath.isNoob(false, 0, 119, 120));
        assertFalse(RewardMath.isNoob(false, 0, 120, 120));
        assertFalse(RewardMath.isNoob(false, 1, 5, 120));
        assertFalse(RewardMath.isNoob(true, 0, 1, 120));
        assertFalse(RewardMath.isNoob(false, 0, 30, 30));
    }
    @Test public void rejectsNonFiniteExperienceAndClampsToProgressionCap() {
        assertEquals(65450, RewardMath.experienceTotal(70000, 65450), 0);
        assertEquals(0, RewardMath.experienceTotal(-10, 65450), 0);
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            try { RewardMath.experienceTotal(value, 65450); fail("Must reject non-finite experience"); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void retainsSmallFractionalAssistContributions() {
        assertEquals(.25, RewardMath.contribution(.2, .8), 1e-12);
        assertEquals(0, RewardMath.contribution(1, 0), 0);
        assertEquals(1, RewardMath.contribution(2, 1), 0);
        assertEquals(0, RewardMath.contribution(Double.NaN, 1), 0);
    }
    @Test public void invalidRewardModifierCannotCreateNegativeOrInfiniteCredit() {
        assertEquals(0, RewardMath.positive(-20), 0);
        assertEquals(0, RewardMath.positive(Double.POSITIVE_INFINITY), 0);
        assertEquals(0, RewardMath.positive(Double.NaN), 0);
        assertEquals(2.5, RewardMath.positive(2.5), 0);
    }
    @Test public void lethalBridgeDoesNotTurnEightHealthIntoTenThousandContribution() {
        assertEquals(8, RewardMath.creditedDamage(8, 0, 10008, 8, true), 0);
        assertEquals(8, RewardMath.creditedDamage(8, 0, 10008, 0, false), 0);
        assertEquals(7, RewardMath.creditedDamage(20, 0, 4, 3, false), 0);
        assertEquals(24, RewardMath.creditedDamage(20, 4, 100, 0, false), 0);
    }
}
