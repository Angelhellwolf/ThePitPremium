package cn.charlotte.pit.perk;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.util.level.LevelUtil;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class PrestigeProgressionTest {
    private org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit;
    @org.junit.Before public void prepareBukkit() { bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class); }
    @org.junit.After public void closeBukkit() { bukkit.close(); }
    private PlayerProfile ready(boolean bot) {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TestPlayer");
        profile.setBot(bot); profile.setPrestige(4);
        profile.setExperience(LevelUtil.getLevelTotalExperience(4, 120));
        profile.setGrindedCoins(80000); profile.setCoins(12345); profile.setRenown(5);
        profile.getBoughtPerkMap().put("Strength", new PerkData("Strength", 0));
        profile.getUnlockedPerkMap().put("Tenacity", new PerkData("Tenacity", 1));
        profile.getChosePerk().put(1, new PerkData("Strength", 0));
        return profile;
    }

    @Test public void humansAndSimulatedPlayersShareThePrestigeResetAndRenownAward() {
        for (boolean bot : new boolean[]{false, true}) {
            PlayerProfile profile = ready(bot);
            assertTrue(PrestigeProgression.advanceData(profile, 35));
            assertEquals(5, profile.getPrestige()); assertEquals(25, profile.getRenown());
            assertEquals(0, profile.getCoins(), 0); assertEquals(0, profile.getExperience(), 0);
            assertEquals(0, profile.getGrindedCoins(), 0); assertTrue(profile.getBoughtPerkMap().isEmpty());
            assertEquals(new PerkData("Tenacity", 1), profile.getUnlockedPerkMap().get("Tenacity"));
            assertEquals(1, profile.getChosePerk().size());
            assertEquals("over_drive", profile.getChosePerk().get(5).getPerkInternalName());
            assertFalse(PrestigeProgression.advanceData(profile, 35));
        }
    }

    @Test public void capExperienceAndLifetimeCoinRequirementsAreAllEnforced() {
        PlayerProfile profile = ready(true);
        profile.setGrindedCoins(79999.99);
        assertFalse(PrestigeProgression.advanceData(profile, 35));
        assertEquals(12345, profile.getCoins(), 0);
        profile.setGrindedCoins(80000);
        profile.setExperience(profile.getExperience() - 1);
        assertFalse(PrestigeProgression.advanceData(profile, 35));
        profile.setExperience(LevelUtil.getLevelTotalExperience(4, 120));
        assertFalse(PrestigeProgression.advanceData(profile, 4));
    }

    @Test public void fastPassSurvivesAndRestartsAtLevelFifty() {
        PlayerProfile profile = ready(true);
        profile.getUnlockedPerkMap().put("FastPass", new PerkData("FastPass", 1));
        assertTrue(PrestigeProgression.advanceData(profile, 35));
        assertEquals(50, profile.getLevel());
    }
}
