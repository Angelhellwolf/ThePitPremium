package cn.charlotte.pit.reward;

import org.junit.Test;
import static org.junit.Assert.*;

public class BotRewardsTest {
    @Test public void botsAlwaysUseHalfOfEveryHumanCalendarMultiplier() {
        double[] human = {1, 2, 3, 5}, bot = {0.5, 1, 1.5, 2.5};
        for (int i = 0; i < human.length; i++) {
            assertEquals(bot[i], BotRewards.multiplier(human[i]), 0);
            assertEquals(10 * bot[i], BotRewards.amount(0, BotRewards.multiplier(human[i])), 0);
        }
    }
    @Test public void invalidValuesCannotMintMoneyAndPrestigeRemainsPartOfTheBase() {
        assertEquals(50, BotRewards.amount(20, BotRewards.multiplier(5)), 0);
        assertEquals(0, BotRewards.amount(0, Double.NaN), 0);
        assertEquals(0, BotRewards.multiplier(Double.POSITIVE_INFINITY), 0);
        assertEquals(0, BotRewards.multiplier(-1), 0);
    }
}
