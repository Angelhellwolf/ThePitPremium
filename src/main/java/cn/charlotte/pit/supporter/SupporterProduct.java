package cn.charlotte.pit.supporter;

public enum SupporterProduct {
    PERMANENT(SupporterTier.NONE),
    MYTHIC(SupporterTier.MYTHIC),
    SACRED(SupporterTier.SACRED);

    private final SupporterTier tier;

    SupporterProduct(SupporterTier tier) { this.tier = tier; }

    public SupporterTier getTier() { return tier; }
    public String getDisplayName() { return tier.getDisplayName(); }
}
