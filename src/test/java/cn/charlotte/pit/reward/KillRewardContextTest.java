package cn.charlotte.pit.reward;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KillRewardContextTest {
    @Test public void scheduledBotLootRetainsItsOriginAfterTheKillHasFinished() {
        UUID recipient = UUID.randomUUID();
        Runnable scheduled;
        try (KillRewardContext ignored = KillRewardContext.openBot(recipient)) {
            scheduled = KillRewardContext.wrap(() -> {
                assertTrue(KillRewardContext.isBot(recipient));
                assertEquals(0, KillRewardContext.bonusMultiplier(recipient, RewardSource.LOOT), 0);
            });
        }
        assertFalse(KillRewardContext.isBot(recipient));
        scheduled.run();
        assertFalse(KillRewardContext.isBot(recipient));
        assertEquals(1, KillRewardContext.bonusMultiplier(recipient, RewardSource.LOOT), 0);
    }
    @Test public void scheduledReducedLootDoesNotLoseItsReductionOrAffectOtherPlayers() {
        UUID recipient = UUID.randomUUID(), other = UUID.randomUUID();
        Runnable scheduled;
        try (KillRewardContext ignored = KillRewardContext.open(recipient, .2)) {
            scheduled = KillRewardContext.wrap(() -> {
                assertEquals(.2, KillRewardContext.bonusMultiplier(recipient, RewardSource.LOOT), 0);
                assertEquals(1, KillRewardContext.bonusMultiplier(other, RewardSource.LOOT), 0);
                throw new IllegalStateException("reward failure");
            });
        }
        try { scheduled.run(); fail(); } catch (IllegalStateException expected) { }
        assertEquals(1, KillRewardContext.bonusMultiplier(recipient, RewardSource.LOOT), 0);
    }
}
