package cn.charlotte.pit.bot;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class PublicPlayerProgressStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit;
    @org.junit.Before public void prepareBukkit() { bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class); }
    @org.junit.After public void closeBukkit() { bukkit.close(); }

    @Test public void detachedSnapshotsKeepMoneyPrestigeAndPerksAcrossReloads() throws Exception {
        Path file = folder.getRoot().toPath().resolve("progress.json");
        List<PlayerProfile> players = new ArrayList<>();
        List<PublicPlayerProgressStore.Progress> snapshots = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Player" + slot);
            profile.setPrestige(slot + 1); profile.setExperience(1234.5); profile.setCoins(4123.75);
            profile.setGrindedCoins(12000.5); profile.setRenown(15); profile.setKills(30); profile.setAssists(4); profile.setDeaths(8);
            profile.getBoughtPerkMap().put("Strength", new PerkData("Strength", 0));
            profile.getChosePerk().put(1, new PerkData("Strength", 0));
            profile.getUnlockedPerkMap().put("Tenacity", new PerkData("Tenacity", 1));
            players.add(profile); snapshots.add(PublicPlayerProgressStore.Progress.capture(profile));
            profile.setCoins(0); profile.getUnlockedPerkMap().get("Tenacity").setLevel(2);
        }
        new PublicPlayerProgressStore(file).save(snapshots);
        Map<UUID, PublicPlayerProgressStore.Progress> loaded = new PublicPlayerProgressStore(file).load();
        assertEquals(3, loaded.size());
        for (PlayerProfile original : players) {
            PlayerProfile restored = new PlayerProfile(original.getPlayerUuid(), original.getPlayerName());
            loaded.get(original.getPlayerUuid()).apply(restored);
            assertEquals(4123.75, restored.getCoins(), 0);
            assertEquals(1234.5, restored.getExperience(), 0);
            assertEquals(original.getPrestige(), restored.getPrestige());
            assertEquals(12000.5, restored.getGrindedCoins(), 0);
            assertEquals(15, restored.getRenown()); assertEquals(30, restored.getKills());
            assertEquals(4, restored.getAssists()); assertEquals(8, restored.getDeaths());
            assertEquals(new PerkData("Tenacity", 1), restored.getUnlockedPerkMap().get("Tenacity"));
            assertEquals(new PerkData("Strength", 0), restored.getChosePerk().get(1));
        }
    }

    @Test public void corruptOrPartialProgressCannotSilentlyResetExistingBalances() throws Exception {
        Path file = folder.getRoot().toPath().resolve("progress.json");
        Files.writeString(file, "{broken");
        PublicPlayerProgressStore store = new PublicPlayerProgressStore(file);
        assertThrows(java.io.IOException.class, store::load);
        assertThrows(java.io.IOException.class, () -> store.save(Collections.emptyList()));
        assertEquals("{broken", Files.readString(file));
    }

    @Test public void progressCannotBeAppliedToAnotherIdentity() {
        PlayerProfile first = new PlayerProfile(UUID.randomUUID(), "MapleRush");
        PlayerProfile second = new PlayerProfile(UUID.randomUUID(), "YukiHaze");
        PublicPlayerProgressStore.Progress saved = PublicPlayerProgressStore.Progress.capture(first);
        assertThrows(IllegalArgumentException.class, () -> saved.apply(second));
    }

    @Test public void ordinaryPerkSlotsUnlockAtTheExistingLevelThresholds() {
        assertEquals(1, PublicPlayerProgression.availableSlots(0));
        assertEquals(1, PublicPlayerProgression.availableSlots(34));
        assertEquals(2, PublicPlayerProgression.availableSlots(35));
        assertEquals(2, PublicPlayerProgression.availableSlots(69));
        assertEquals(3, PublicPlayerProgression.availableSlots(70));
        assertEquals(4, PublicPlayerProgression.availableSlots(100));
    }
}
