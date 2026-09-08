package cn.charlotte.pit.menu.supporter.button;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class SupporterRenameButton extends Button {
    @Override
    public ItemStack getButtonItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        int dailyLimit = NewConfiguration.INSTANCE.getRenameDailyFreeLimit();
        int weeklyLimit = NewConfiguration.INSTANCE.getRenameWeeklyFreeLimit();
        int extraCost = NewConfiguration.INSTANCE.getRenameExtraCost();

        return new ItemBuilder(Material.NAME_TAG)
                .name("&a彩色物品命名")
                .lore(Arrays.asList(
                        "",
                        "&7手持神话物品后使用:",
                        "&f/rename <名称>",
                        "",
                        "&7今日免费: &a" + profile.getRenameDailyFreeRemaining(dailyLimit) + "&7/&f" + dailyLimit,
                        "&7本周免费: &a" + profile.getRenameWeeklyFreeRemaining(weeklyLimit) + "&7/&f" + weeklyLimit,
                        "&7免费额度耗尽后: &d" + extraCost + " 点券/次",
                        "",
                        "&7颜色代码使用英文符号 &f& &7加数字或字母.",
                        "&7支持名称中的多个颜色代码.",
                        "",
                        "&e点击在聊天栏查看示例"
                ))
                .build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isSupporter() && !PlayerUtil.isStaff(player) && !player.hasPermission("pit.rename")) {
            player.sendMessage(CC.translate("&c你的天坑·会员当前无效。"));
            return;
        }

        player.closeInventory();
        int dailyLimit = NewConfiguration.INSTANCE.getRenameDailyFreeLimit();
        int weeklyLimit = NewConfiguration.INSTANCE.getRenameWeeklyFreeLimit();
        player.sendMessage("§6§m--------------------------------");
        player.sendMessage("§e彩色命名格式: §f/rename &c赤焰之刃");
        player.sendMessage("§7剩余免费次数: §a今日 "
                + profile.getRenameDailyFreeRemaining(dailyLimit) + "/" + dailyLimit
                + " §7| §a本周 " + profile.getRenameWeeklyFreeRemaining(weeklyLimit) + "/" + weeklyLimit);
        player.sendMessage("§7额度耗尽后每次收取 §d" + NewConfiguration.INSTANCE.getRenameExtraCost() + " 点券§7。");
        player.sendMessage("§7颜色代码: §f&0 &1 &2 &3 &4 &5 &6 &7 &8 &9 &a &b &c &d &e &f");
        player.sendMessage("§7格式代码: §f&l 粗体  &n 下划线  &o 斜体  &r 重置");
        player.sendMessage("§7示例结果: §c赤§6焰§e之§a刃");
        player.sendMessage("§6§m--------------------------------");
        playNeutral(player);
    }
}
