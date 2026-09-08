package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.medal.AbstractMedal;
import org.bukkit.Bukkit;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MedalProgressTest {
    @Test public void cumulativeProgressContinuesPastAnAlreadyCompletedTier() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "MedalTester");
            AbstractMedal medal = new AbstractMedal() {
                public String getInternalName() { return "TEST"; }
                public String getDisplayName(int level) { return "Test"; }
                public String getRequirementDescription(int level) { return "Test"; }
                public int getMaxLevel() { return 3; }
                public int getProgressRequirement(int level) { return 10 * level; }
                public int getRarity(int level) { return 1; }
                public boolean isHidden() { return false; }
                public void handleProfileLoaded(PlayerProfile profile) { }
            };
            for (int i = 0; i < 300; i++) medal.addProgress(profile, 0.1);
            assertTrue(profile.getMedalData().getMedalStatus("TEST", 3).isUnlocked());
            assertEquals(30, profile.getMedalData().getMedalStatus("TEST", 3).getProgress());
            medal.addProgress(profile, -100);
            assertEquals(30, profile.getMedalData().getMedalStatus("TEST", 3).getProgress());
        }
    }
}
