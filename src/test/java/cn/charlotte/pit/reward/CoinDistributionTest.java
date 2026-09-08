package cn.charlotte.pit.reward;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CoinDistributionTest {
    @Test public void moreRecipientsNeverMultiplyTheFaceValue() {
        for (int count = 1; count <= 50; count++) {
            double[] distances = new double[count];
            for (int i = 0; i < count; i++) distances[i] = i + 1;
            assertEquals(1001, Arrays.stream(CoinDistribution.byDistance(1001, distances)).sum());
        }
    }
    @Test public void aSingleRecipientAndCoincidentRecipientsCanReceiveThePacket() {
        assertArrayEquals(new int[] {1000}, CoinDistribution.byDistance(1000, new double[] {2}));
        assertArrayEquals(new int[] {500, 500}, CoinDistribution.byDistance(1000, new double[] {0, 0}));
    }
    @Test public void invalidCoordinatesDoNotReceiveMoney() {
        assertArrayEquals(new int[] {0, 10}, CoinDistribution.byDistance(10, new double[] {Double.NaN, 1}));
    }
}
