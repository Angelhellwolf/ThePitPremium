package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class StashPerk extends AbstractPerk {
    @Override
    public String getInternalPerkName() {
        return "stash_unlock";
    }

    @Override
    public String getDisplayName() {
        return "寄存处";
    }

    @Override
    public Material getIcon() {
        return Material.CHEST;
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
        return 1;
    }

    @Override
    public int requireLevel() {
        return 0;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("&7解锁寄存处NPC访问权限.");
        lines.add("&7每位玩家拥有 &e10 &7个寄存箱,");
        lines.add("&7每个寄存箱包含 &e45 &7个存储位.");
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
}
