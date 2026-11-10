package cn.charlotte.pit.junxu;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;

public final class JunxuEntitlements {
    private JunxuEntitlements() {}
    public static SupporterTier tier(PlayerProfile profile,long now) {
        if(profile==null || !profile.isLoaded() || profile.isBot())return SupporterTier.NONE;
        return tier(profile.getSupporterAccount(),now);
    }
    public static SupporterTier tier(SupporterAccount account,long now) {
        return account==null || !account.isPermanent() ? SupporterTier.NONE : account.activeTier(now);
    }
    public static long limit(SupporterTier tier) {
        return tier==SupporterTier.SACRED?8*3600000L:tier==SupporterTier.MYTHIC?4*3600000L:0;
    }
    public static int bots(SupporterTier tier) { return tier==SupporterTier.SACRED?10:tier==SupporterTier.MYTHIC?5:0; }
    public static boolean invite(SupporterTier tier) { return tier==SupporterTier.SACRED; }
}
