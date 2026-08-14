package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.perk.PrestigePrerequisite;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AllArmedPerk extends AbstractPerk implements PrestigePrerequisite {
    @Override
    public String getInternalPerkName() {
        return "all_armed_perk";
    }

    @Override
    public String getDisplayName() {
        return "全副武装";
    }

    @Override
    public Material getIcon() {
        return Material.IRON_HELMET;
    }

    @Override
    public double requireCoins() {
        return 0;
    }

    @Override
    public double requireRenown(int level) {
        return 10;
    }

    @Override
    public int requirePrestige() {
        return 5;
    }

    @Override
    public int requireLevel() {
        return 0;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("&7使你的精通天赋“铁制盔甲购买”");
        lines.add("&7在商店购买时额外获得一个铁头盔.");
        return lines;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public PerkType getPerkType() {
        return PerkType.PERK;
    }

    @Override
    public void onPerkActive(Player player) {

    }

    @Override
    public void onPerkInactive(Player player) {

    }

    @Override
    public boolean hasUnlockedRequirement(PlayerProfile profile) {
        return profile.getUnlockedPerkMap().containsKey("IronPackShopUnlock");
    }

    @Override
    public String getRequirementDescription() {
        return "商店升级: 铁质盔甲套装";
    }

    @Override
    public String getRequirementFailMessage() {
        return "&c你需要先解锁精通天赋 &e商店升级: 铁质盔甲套装&c!";
    }
}
