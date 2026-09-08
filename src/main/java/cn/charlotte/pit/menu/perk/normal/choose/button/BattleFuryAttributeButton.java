package cn.charlotte.pit.menu.perk.normal.choose.button;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.event.PitPlayerUnlockPerkEvent;
import cn.charlotte.pit.event.PitPlayerUpgradePerkEvent;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.RomanUtil;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BattleFuryAttributeButton extends Button {
    private final AbstractPerk perk;

    public BattleFuryAttributeButton(AbstractPerk perk) {
        this.perk = perk;
    }

    @Override
    public ItemStack getButtonItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        List<String> lore = new ArrayList<>(perk.getEconomyDescription(player));
        lore.add(" ");

        PerkData data = profile.getUnlockedPerkMap().get(perk.getInternalPerkName());
        if (data != null && data.getLevel() < 1) {
            data.setLevel(1);
        }

        if (profile.getPrestige() < perk.requirePrestige()) {
            lore.add("&c需要精通等级: &e" + perk.requirePrestige());
            return perk.getIconWithNameAndLore("&c" + perk.getDisplayName(), lore, 0, 1);
        }

        if (data != null) {
            lore.add("&7当前等级: &b" + RomanUtil.convert(data.getLevel()));
        }

        String color;
        if (data != null && data.getLevel() >= perk.getMaxLevel()) {
            lore.add("&a此属性已满级!");
            color = "&a";
        } else {
            int nextLevel = data == null ? 1 : data.getLevel() + 1;
            int price = (int) perk.requireRenown(nextLevel);
            lore.add("&7升级价格: &e" + price + " 声望");
            lore.add("&7你的声望: &e" + profile.getRenown() + " 声望");
            if (profile.getRenown() < price) {
                lore.add("&c你没有足够的声望!");
                color = "&c";
            } else {
                lore.add("&e点击升级此属性!");
                color = "&e";
            }
        }

        return perk.getIconWithNameAndLore(color + perk.getDisplayName(), lore, 0, 1);
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (profile.getPrestige() < perk.requirePrestige()) {
            player.sendMessage(CC.translate("&c精通等级不足!"));
            return;
        }

        PerkData data = profile.getUnlockedPerkMap().get(perk.getInternalPerkName());
        if (data != null && data.getLevel() < 1) {
            data.setLevel(1);
        }

        int nextLevel = data == null ? 1 : data.getLevel() + 1;
        if (data != null && data.getLevel() >= perk.getMaxLevel()) {
            return;
        }

        int price = (int) perk.requireRenown(nextLevel);
        if (profile.getRenown() < price) {
            player.sendMessage(CC.translate("&c你没有足够的声望!"));
            return;
        }

        profile.setRenown(profile.getRenown() - price);
        if (data == null) {
            PerkData perkData = new PerkData(perk.getInternalPerkName(), 1);
            profile.getUnlockedPerkMap().put(perkData.getPerkInternalName(), perkData);
            perk.onUnlock(player);
            new PitPlayerUnlockPerkEvent(player, perk).callEvent();
            player.sendMessage(CC.translate("&a&l天赋解锁! &7你解锁了属性 &e" + perk.getDisplayName() + " &7."));
            return;
        }

        data.setLevel(nextLevel);
        perk.onUpgrade(player);
        new PitPlayerUpgradePerkEvent(player, perk, nextLevel);
        player.sendMessage(CC.translate("&a&l天赋升级! &7你升级了属性 &e" + perk.getDisplayName() + " &7至等级 &b" + RomanUtil.convert(nextLevel) + " &7."));
    }

    @Override
    public boolean shouldUpdate(Player player, int slot, ClickType clickType) {
        return true;
    }
}
