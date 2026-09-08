package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import java.util.LinkedHashMap;
import java.util.Map;

/** Balance changes distinguish generated currency from circulation, escrow and refunds. */
public final class CoinLedger {
    public enum Flow { MINT, SPEND, PRESTIGE_RESET, TRANSFER, ESCROW, REFUND, ADMIN, LEGACY_CREDIT, ROLLBACK, OTHER }
    private static final ThreadLocal<Context> current = new ThreadLocal<>();
    private static final Map<String, Double> deltas = new LinkedHashMap<>();
    private CoinLedger() { }

    public static void set(PlayerProfile profile, double balance, Flow flow, String source) {
        if (EconomyV2.active() && !Double.isFinite(balance)) throw new IllegalArgumentException("Non-finite coin balance");
        Context before = current.get(); current.set(new Context(flow, source));
        try { profile.setCoins(balance); }
        finally { if (before == null) current.remove(); else current.set(before); }
    }

    public static void record(PlayerProfile profile, double before, double after) {
        if ((!EconomyV2.active() && !EconomyV2.observing()) || !profile.isLoaded() || profile.isBot() || !Double.isFinite(before)
                || !Double.isFinite(after) || before == after) return;
        Context c = current.get();
        Flow flow = c == null ? Flow.OTHER : c.flow;
        String source = c == null ? "unclassified" : c.source;
        double delta = after - before;
        deltas.merge("uuid=" + profile.getPlayerUuid() + " flow=" + flow + " source=" + source, delta, Double::sum);
        if (!EconomyV2.active()) return;
        EconomyData.DailyReport report = EconomyAudit.report(profile);
        if (flow == Flow.SPEND || flow == Flow.PRESTIGE_RESET || flow == Flow.OTHER && delta < 0)
            EconomyAudit.spend(profile, source, Math.max(0, -delta));
        if (flow == Flow.TRANSFER) {
            if (delta > 0) report.transferredIn += delta; else report.transferredOut -= delta;
        }
        if (flow == Flow.ESCROW) report.escrowChange -= delta;
        if (flow == Flow.REFUND) report.refunds += Math.max(0, delta);
    }

    public static void flush(java.util.logging.Logger logger) {
        if (logger != null) deltas.forEach((key, delta) -> logger.info("ECONOMY_BALANCE " + key + " delta=" + delta));
        deltas.clear();
    }

    /** Winning bids were debited into escrow earlier; recording the sink must not debit them twice. */
    public static void escrowSpent(PlayerProfile profile, double amount, String source) {
        if ((!EconomyV2.active() && !EconomyV2.observing()) || profile == null || !profile.isLoaded() || profile.isBot()) return;
        deltas.merge("uuid=" + profile.getPlayerUuid() + " flow=ESCROW_SPENT source=" + source, -RewardMath.positive(amount), Double::sum);
        if (EconomyV2.active()) EconomyAudit.spend(profile, source, amount);
    }

    private static final class Context {
        final Flow flow; final String source;
        Context(Flow flow, String source) { this.flow = flow; this.source = source; }
    }
}
