package cn.charlotte.pit.supporter;

/** Immutable snapshot of the authoritative account stored with the points ledger. */
public final class SupporterAccount {
    private final boolean permanent;
    private final boolean givenByAdmin;
    private final SupporterTier tier;
    private final long expiresAt;
    private final long revision;

    public SupporterAccount(boolean permanent, boolean givenByAdmin, SupporterTier tier,
                            long expiresAt, long revision) {
        this.permanent = permanent;
        this.givenByAdmin = permanent && givenByAdmin;
        this.tier = tier == null ? SupporterTier.NONE : tier;
        this.expiresAt = this.tier == SupporterTier.NONE ? 0L : Math.max(0L, expiresAt);
        this.revision = revision;
    }

    public boolean isPermanent() { return permanent; }
    public boolean isGivenByAdmin() { return givenByAdmin; }
    public SupporterTier getTier() { return tier; }
    public long getExpiresAt() { return expiresAt; }
    public long getRevision() { return revision; }

    public SupporterTier activeTier(long now) {
        return expiresAt > now ? tier : SupporterTier.NONE;
    }

    public static SupporterAccount empty() {
        return new SupporterAccount(false, false, SupporterTier.NONE, 0L, 0L);
    }

    public static SupporterAccount fromLegacy(boolean supporter, boolean admin, long supporterExpiry,
                                               int savedTier, long contractExpiry, long now) {
        boolean activeLegacy = supporter && (admin || supporterExpiry <= 0L || supporterExpiry > now);
        SupporterTier tier = SupporterTier.fromId(savedTier);
        long expiry = contractExpiry;
        if (supporter && !admin && supporterExpiry > now && tier == SupporterTier.NONE) {
            // Only still-active old subscriptions receive the migration gift.
            tier = SupporterTier.SACRED;
            expiry = supporterExpiry;
        }
        return new SupporterAccount(activeLegacy, admin, tier, expiry, 0L);
    }
}
