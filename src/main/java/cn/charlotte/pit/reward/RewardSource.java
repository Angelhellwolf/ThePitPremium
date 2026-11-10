package cn.charlotte.pit.reward;

public enum RewardSource {
    KILL(true), ASSIST(true), EVENT(true), QUEST(true), ENCHANT(true), PERK(true),
    STREAK(true), LOOT(true), FISHING(true), MYTHIC_MOB(true),
    MAIL(false), ADMIN(false), STREAK_PAYOUT(false), BOUNTY(false), HIGHLANDER(false);

    private final boolean noobBonus;
    RewardSource(boolean noobBonus) { this.noobBonus = noobBonus; }
    public boolean appliesNoobBonus() { return noobBonus; }
}
