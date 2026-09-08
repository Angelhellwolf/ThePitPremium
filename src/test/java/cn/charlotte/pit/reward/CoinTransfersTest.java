package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

public class CoinTransfersTest {
    @Test public void emptyOrInsufficientWalletsCannotMintTheRequestedDifference() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerProfile from = profile(2.5), to = profile(100);
            assertEquals(2.5, CoinTransfers.transfer(from, to, 25), 0);
            assertEquals(0, from.getCoins(), 0);
            assertEquals(102.5, to.getCoins(), 0);
            for (int i = 0; i < 100; i++) assertEquals(0, CoinTransfers.transfer(from, to, 25), 0);
            assertEquals(102.5, from.getCoins() + to.getCoins(), 0);
            assertEquals(0, to.getGrindedCoins(), 0);
        }
    }
    @Test public void botsSelfTransfersUnloadedProfilesAndNonFiniteRequestsDoNotTransfer() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerProfile from = profile(100), to = profile(100);
            from.setBot(true);
            assertEquals(0, CoinTransfers.transfer(from, to, 25), 0);
            from.setBot(false); to.setBot(true);
            assertEquals(0, CoinTransfers.transfer(from, to, 25), 0);
            to.setBot(false); to.setLoaded(false);
            assertEquals(0, CoinTransfers.transfer(from, to, 25), 0);
            to.setLoaded(true);
            assertEquals(0, CoinTransfers.transfer(from, from, 25), 0);
            assertEquals(0, CoinTransfers.transfer(from, to, Double.NaN), 0);
            assertEquals(0, CoinTransfers.transfer(from, to, Double.POSITIVE_INFINITY), 0);
            assertEquals(200, from.getCoins() + to.getCoins(), 0);
        }
    }
    private PlayerProfile profile(double coins) {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "TransferTest");
        profile.setLoaded(true); profile.setCoins(coins); return profile;
    }
}
