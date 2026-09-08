package cn.charlotte.pit.reward;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BountySharingTest {
    @Test public void eightyPercentIsSharedWithinTheOriginalGrowthBudget() {
        int[] shares = BountySharing.distribute(25, new int[] {5000, 5000, 5000}, 0);
        assertArrayEquals(new int[] {7, 7, 6}, shares);
        assertEquals(5, 25 - Arrays.stream(shares).sum());
    }
    @Test public void fullRecipientsCannotMintMoreBountyOrConsumeTheRemainder() {
        int[] shares = BountySharing.distribute(25, new int[] {0, 3}, 1);
        assertArrayEquals(new int[] {0, 3}, shares);
        assertEquals(22, 25 - Arrays.stream(shares).sum());
        assertEquals(0, BountySharing.distribute(25, new int[0], 0).length);
    }
    @Test public void distributionNeverExceedsCapacityOrGrowthAcrossSmallBudgets() {
        for (int growth = 0; growth <= 25; growth++) for (int seed = -3; seed < 3; seed++) {
            int[] shares = BountySharing.distribute(growth, new int[] {2, 5, 0, 100}, seed);
            assertTrue(Arrays.stream(shares).sum() <= growth);
            assertTrue(shares[0] <= 2 && shares[1] <= 5 && shares[2] == 0 && shares[3] <= 100);
        }
    }
}
