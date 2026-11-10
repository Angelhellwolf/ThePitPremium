package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.Bukkit;

/** Transfers conserve currency and cannot draw from synthetic player balances. */
public final class CoinTransfers {
    private CoinTransfers() { }

    public static double transfer(PlayerProfile from, PlayerProfile to, double requested) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Coin transfers must run on the server thread");
        if (from == null || to == null || !from.isLoaded() || !to.isLoaded() || from.isBot() || to.isBot()
                || from.getPlayerUuid().equals(to.getPlayerUuid())) return 0;
        double amount = Math.min(RewardMath.positive(from.getCoins()), RewardMath.positive(requested));
        if (amount <= 0 || !Double.isFinite(to.getCoins() + amount)) return 0;
        CoinLedger.set(from, from.getCoins() - amount, CoinLedger.Flow.TRANSFER, "pit_pocket");
        CoinLedger.set(to, to.getCoins() + amount, CoinLedger.Flow.TRANSFER, "pit_pocket");
        EconomyAudit.transfer(from, to, amount);
        return amount;
    }
}
