package cn.charlotte.pit.supporter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/** Prices and upgrade arithmetic are independent of Bukkit and database state. */
public final class SupporterPricing {
    public static final int POINTS_PER_YUAN = 100;
    private final int permanentPoints;
    private final int mythicPoints;
    private final int sacredPoints;
    private final long periodMillis;

    public SupporterPricing(int permanentYuan, int mythicYuan, int sacredYuan, int durationDays) {
        if (permanentYuan <= 0 || mythicYuan <= 0 || sacredYuan <= mythicYuan || durationDays <= 0) {
            throw new IllegalArgumentException("Invalid supporter pricing");
        }
        permanentPoints = Math.multiplyExact(permanentYuan, POINTS_PER_YUAN);
        mythicPoints = Math.multiplyExact(mythicYuan, POINTS_PER_YUAN);
        sacredPoints = Math.multiplyExact(sacredYuan, POINTS_PER_YUAN);
        periodMillis = Math.multiplyExact(TimeUnit.DAYS.toMillis(1), durationDays);
    }

    public int price(SupporterProduct product) {
        switch (product) {
            case MYTHIC: return mythicPoints;
            case SACRED: return sacredPoints;
            default: return permanentPoints;
        }
    }

    public long getPeriodMillis() { return periodMillis; }

    public Quote quote(SupporterAccount account, SupporterProduct product, boolean externalSupporter, long now) {
        boolean hasBase = account.isPermanent();
        SupporterTier active = account.activeTier(now);
        if (product == SupporterProduct.PERMANENT) {
            if (hasBase) throw new IllegalArgumentException("你已经拥有天坑·会员资格。");
            return new Quote(product, "购买", permanentPoints, false, account.getRevision(), now,
                    new SupporterAccount(true, false, account.getTier(), account.getExpiresAt(), Math.addExact(account.getRevision(), 1L)));
        }

        if (!hasBase) throw new IllegalArgumentException("请先购买天坑·会员，再开通契约。");

        if (active == SupporterTier.SACRED && product == SupporterProduct.MYTHIC) {
            throw new IllegalArgumentException("神话圣契到期后才可改用神话契约。");
        }

        int price;
        long expiry;
        String action;
        if (active == SupporterTier.MYTHIC && product == SupporterProduct.SACRED) {
            BigInteger numerator = BigInteger.valueOf(sacredPoints - mythicPoints)
                    .multiply(BigInteger.valueOf(account.getExpiresAt() - now));
            BigInteger period = BigInteger.valueOf(periodMillis);
            price = numerator.add(period).subtract(BigInteger.ONE).divide(period).intValueExact();
            expiry = account.getExpiresAt();
            action = "补差价升级";
        } else {
            price = price(product);
            expiry = Math.addExact(Math.max(now, account.getExpiresAt()), periodMillis);
            action = active == product.getTier() ? "续费" : "开通";
        }
        SupporterAccount after = new SupporterAccount(account.isPermanent(),
                account.isGivenByAdmin(), product.getTier(), expiry, Math.addExact(account.getRevision(), 1L));
        return new Quote(product, action, price, false, account.getRevision(), now, after);
    }

    public static String yuan(int points) {
        return BigDecimal.valueOf(points, 2).stripTrailingZeros().toPlainString();
    }

    public static final class Quote {
        private final SupporterProduct product;
        private final String action;
        private final int points;
        private final boolean includesPermanent;
        private final long expectedRevision;
        private final long createdAt;
        private final SupporterAccount after;

        Quote(SupporterProduct product, String action, int points, boolean includesPermanent,
              long expectedRevision, long createdAt, SupporterAccount after) {
            this.product = product;
            this.action = action;
            this.points = points;
            this.includesPermanent = includesPermanent;
            this.expectedRevision = expectedRevision;
            this.createdAt = createdAt;
            this.after = after;
        }

        public SupporterProduct getProduct() { return product; }
        public String getAction() { return action; }
        public int getPoints() { return points; }
        public boolean isIncludesPermanent() { return includesPermanent; }
        public long getExpectedRevision() { return expectedRevision; }
        public long getCreatedAt() { return createdAt; }
        public SupporterAccount getAfter() { return after; }
    }
}
