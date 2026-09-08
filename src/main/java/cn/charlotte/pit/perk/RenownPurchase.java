package cn.charlotte.pit.perk;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.event.PitPlayerUnlockPerkEvent;
import cn.charlotte.pit.event.PitPlayerUpgradePerkEvent;
import org.bukkit.entity.Player;

/** Validates again at purchase time, including after a confirmation menu was open. */
public final class RenownPurchase {
    private RenownPurchase() {}
    public static boolean purchase(Player player, AbstractPerk perk) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded() || profile.getPrestige() < perk.requirePrestige()) return false;
        if (perk instanceof PrestigePrerequisite && !((PrestigePrerequisite) perk).hasUnlockedRequirement(profile)) return false;
        PerkData current = profile.getUnlockedPerkMap().get(perk.getInternalPerkName());
        int next = current == null ? 1 : current.getLevel() + 1;
        if (next > perk.getMaxLevel()) return false;
        double quoted = perk.requireRenown(next);
        if (!Double.isFinite(quoted) || quoted < 0 || quoted > Integer.MAX_VALUE) return false;
        int price = (int) quoted;
        if (profile.getRenown() < price) return false;
        profile.setRenown(profile.getRenown() - price);
        if (current == null) {
            profile.getUnlockedPerkMap().put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), next));
            perk.onUnlock(player);
            new PitPlayerUnlockPerkEvent(player, perk).callEvent();
        } else {
            current.setLevel(next);
            perk.onUpgrade(player);
            new PitPlayerUpgradePerkEvent(player, perk, next).callEvent();
        }
        return true;
    }
}
