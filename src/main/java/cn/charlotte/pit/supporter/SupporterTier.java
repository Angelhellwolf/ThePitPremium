package cn.charlotte.pit.supporter;

public enum SupporterTier {
    NONE(0, "天坑·会员", "&e"),
    MYTHIC(1, "神话契约", "&d"),
    SACRED(2, "神话圣契", "&c");

    private final int id;
    private final String displayName;
    private final String color;

    SupporterTier(int id, String displayName, String color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public int getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    public static SupporterTier fromId(int id) {
        for (SupporterTier tier : values()) {
            if (tier.id == id) return tier;
        }
        return NONE;
    }
}
