package cn.charlotte.pit.reward;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class BountyTrackerTest {
    @Test public void growthRequiresDifferentOpponentsAndHasARollingMinuteBudget() {
        BountyTracker tracker = new BountyTracker();
        UUID hunter = UUID.randomUUID(), opponent = UUID.randomUUID();
        assertEquals(25, tracker.grow(hunter, opponent, 0, 5000, 1000));
        assertEquals(0, tracker.grow(hunter, opponent, 25, 5000, 1001));
        assertEquals(0, tracker.grow(opponent, hunter, 0, 5000, 1002));
        for (int i = 1; i < 4; i++) assertEquals(25, tracker.grow(hunter, UUID.randomUUID(), i * 25, 5000, 1000 + i));
        assertEquals(0, tracker.grow(hunter, UUID.randomUUID(), 100, 5000, 60_999));
        assertEquals(25, tracker.grow(hunter, UUID.randomUUID(), 100, 5000, 61_000));
        assertEquals(25, tracker.grow(hunter, opponent, 125, 5000, 301_000));
    }

    @Test public void deathsOrReconnectsDoNotResetTheSymmetricClaimCooldown() {
        BountyTracker tracker = new BountyTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(tracker.claim(first, second, 1000));
        tracker.finishLife(first);
        tracker.finishLife(second);
        tracker.cleanup(2000);
        assertFalse(tracker.claim(first, second, 2000));
        assertFalse(tracker.claim(second, first, 2000));
        assertFalse(tracker.claim(second, first, 600_999));
        assertTrue(tracker.claim(second, first, 601_000));
        assertFalse(tracker.claim(first, first, 900_000));
    }

    @Test public void combatEvidenceMustBeRealRecentAndBelongToTheCurrentLife() {
        BountyTracker tracker = new BountyTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.recordDamage(first, second, 5, 1000);
        assertFalse(tracker.hasRealFight(first, second, 1000));
        tracker.recordDamage(second, first, Double.NaN, 1000);
        tracker.recordDamage(second, first, 1, 1000);
        assertFalse(tracker.hasRealFight(first, second, 1000));
        tracker.recordDamage(second, first, 1, 1001);
        assertTrue(tracker.hasRealFight(first, second, 1001));
        assertFalse(tracker.hasRealFight(first, second, 31_000));
        tracker.recordDamage(first, second, 5, 40_000);
        tracker.recordDamage(second, first, 5, 40_000);
        tracker.finishLife(second);
        assertFalse(tracker.hasRealFight(first, second, 40_001));
    }

    @Test public void growthNeverExceedsTheBountyCap() {
        BountyTracker tracker = new BountyTracker();
        UUID hunter = UUID.randomUUID();
        assertEquals(5, tracker.grow(hunter, UUID.randomUUID(), 4995, 5000, 1000));
        assertEquals(0, tracker.grow(hunter, UUID.randomUUID(), 5000, 5000, 1000));
        assertEquals(0, tracker.grow(hunter, hunter, 0, 5000, 1000));
    }

    @Test public void bountyBonusesAreAdditiveAndHighlanderHasBoundedRisk() {
        assertEquals(1000, BountyRules.payout(1000, false, false), 0);
        assertEquals(1500, BountyRules.payout(1000, false, true), 0);
        assertEquals(2000, BountyRules.payout(1000, true, true), 0);
        assertEquals(20_000, BountyRules.payout(Integer.MAX_VALUE, true, true), 0);
        assertEquals(0, BountyRules.payout(-1, true, true), 0);
        assertEquals(10, BountyRules.highlanderBonus(20, 1), 0);
        assertEquals(50, BountyRules.highlanderBonus(20, 5), 0);
        assertEquals(0, BountyRules.highlanderVulnerability(49), 0);
        assertEquals(.003, BountyRules.highlanderVulnerability(51), 0.000001);
        assertEquals(.30, BountyRules.highlanderVulnerability(5000), 0);
    }
}
