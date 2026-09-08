package cn.charlotte.pit.reward;

/** Origin is retained across delayed, stored and mailed rewards. */
public enum RewardDetail {
    KILL(RewardSource.KILL), ASSIST(RewardSource.ASSIST),
    ENCHANT(RewardSource.ENCHANT), PERK(RewardSource.PERK), STREAK(RewardSource.STREAK),
    OVERDRIVE(RewardSource.STREAK_PAYOUT, false), MOON(RewardSource.STREAK_PAYOUT, false),
    DICE(RewardSource.STREAK_PAYOUT), STREAK_DERIVED(RewardSource.STREAK_PAYOUT), NANO(RewardSource.STREAK_PAYOUT),
    DAY_QUEST(RewardSource.QUEST), NIGHT_QUEST(RewardSource.QUEST),
    KING_QUEST(RewardSource.QUEST, false), FISH_CATCH(RewardSource.FISHING), FISH_CLUB(RewardSource.FISHING),
    GROUND_GOLD(RewardSource.LOOT), SEWER_GOLD(RewardSource.LOOT), SEWER_XP(RewardSource.LOOT),
    OBSIDIAN(RewardSource.LOOT), CAKE(RewardSource.EVENT), DRAGON_EGG(RewardSource.EVENT),
    QUICK_MATH(RewardSource.EVENT), MAJOR_EVENT(RewardSource.EVENT), EVENT(RewardSource.EVENT),
    AIRDROP(RewardSource.LOOT, false), MYTHIC_MOB(RewardSource.MYTHIC_MOB),
    BOUNTY(RewardSource.BOUNTY, false), HIGHLANDER(RewardSource.HIGHLANDER, false),
    MILESTONE(RewardSource.QUEST, false), WEEKLY(RewardSource.QUEST, false),
    MAIL_REWARD(RewardSource.MAIL, false), MAIL_LEGACY(RewardSource.MAIL, false),
    REFUND(RewardSource.MAIL, false), ADMIN(RewardSource.ADMIN, false), LOOT(RewardSource.LOOT);

    public final RewardSource source;
    public final boolean newcomer;
    RewardDetail(RewardSource source) { this(source, true); }
    RewardDetail(RewardSource source, boolean newcomer) { this.source = source; this.newcomer = newcomer; }
    public boolean progression() {
        return this != REFUND && this != ADMIN && this != MAIL_LEGACY;
    }
    public boolean stored() {
        return this == OVERDRIVE || this == MOON || this == DICE || this == STREAK_DERIVED || this == NANO || this == BOUNTY || this == HIGHLANDER
                || this == MILESTONE || this == WEEKLY || this == AIRDROP
                || this == MAIL_LEGACY || this == MAIL_REWARD || this == REFUND || this == ADMIN;
    }
    public RewardSource legacySource() {
        if (this == OVERDRIVE || this == DICE || this == STREAK_DERIVED) return RewardSource.STREAK;
        return this == NANO ? RewardSource.LOOT : source;
    }
    public boolean humanDerived() { return this == DICE || this == STREAK_DERIVED || this == NANO; }
    public static RewardDetail of(RewardSource source) {
        switch (source) {
            case KILL: return KILL;
            case ASSIST: return ASSIST;
            case ENCHANT: return ENCHANT;
            case PERK: return PERK;
            case STREAK: return STREAK;
            case QUEST: return DAY_QUEST;
            case FISHING: return FISH_CLUB;
            case MYTHIC_MOB: return MYTHIC_MOB;
            case EVENT: return EVENT;
            case STREAK_PAYOUT: return MOON;
            case BOUNTY: return BOUNTY;
            case HIGHLANDER: return HIGHLANDER;
            case MAIL: return MAIL_LEGACY;
            case ADMIN: return ADMIN;
            default: return LOOT;
        }
    }
}
