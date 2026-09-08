package cn.charlotte.pit.menu.prestige.button;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.menu.prestige.PrestigeMainMenu;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.MessageType;
import cn.charlotte.pit.util.chat.RomanUtil;
import cn.charlotte.pit.util.chat.TitleUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.menus.ConfirmMenu;
import cn.charlotte.pit.util.rank.RankUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/4 20:43
 */
public class PrestigeStatusButton extends Button {

    public static int limit = 35;

    @Override
    public ItemStack getButtonItem(Player player) {
        return getPrestigeItem(player);
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (profile.getLevel() >= 120 && profile.getPrestige() < limit) {
            // && profile.getCoins() >= Math.round(profile.getPrestige() * 12 / 30) * 10000
            if (cn.charlotte.pit.perk.PrestigeProgression.eligible(profile, limit)) {
                EventFactory eventFactory = ThePit.getInstance().getEventFactory();
                //check if event is available
                if (eventFactory.getActiveNormalEvent() != null && eventFactory.getActiveNormalEventName().equals("auction")) {
                    player.sendMessage(CC.translate("&c请等待当前的拍卖事件结束后再进行此操作!"));
                    return;
                }
                try {
                    profile.getMailData().getMails().forEach(mail ->
                            {
                                if (mail.getContent().contains("拍卖") && !mail.isClaimed()) {
                                    player.sendMessage(CC.translate("&c请确认邮箱内所有关于拍卖的邮件处于已领取状态后重试!"));
                                    throw new Error("Player have unclaimed auction mails");
                                }
                            }
                    );
                } catch (Exception ignored) {
                    return;
                }
                new ConfirmMenu("确认要继续吗?", element -> {
                    if (element) {
                        cn.charlotte.pit.perk.PrestigeProgression.advance(player);

                    } else {
                        new PrestigeMainMenu().openMenu(player);
                    }
                }, true, 5, (Button) null).openMenu(player);
            }
        }
    }

    private ItemStack getPrestigeItem(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (cn.charlotte.pit.reward.EconomyV2.active()) return getV2PrestigeItem(profile);
        List<String> lore = new ArrayList<>();
        int award = cn.charlotte.pit.perk.PrestigeProgression.renownAward(profile.getPrestige() + 1);
        if (profile.getPrestige() >= limit) {
            lore.add("&7当前精通等级: &e" + RomanUtil.convert(profile.getPrestige()));
            if (profile.getLevel() < 120) {
                lore.add("&7升级所需经验是原来的 &b" + new DecimalFormat("0.00").format(Math.pow(1.1, profile.getPrestige()) * 100) + "%");
            }
            lore.add(" ");
            lore.add("&7&o你的精通等级已经达到上限!");
            lore.add("&7&o我们会在游戏内容逐步完善后");
            lore.add("&7&o逐步开放更高的精通等级.");
            lore.add(" ");
            lore.add("&a&l感谢你的游玩!");
        } else {
            if (profile.getLevel() >= 120) {
                lore.add("&7当前精通等级: &e" + RomanUtil.convert(profile.getPrestige()));
                lore.add("&7所需等级: " + LevelUtil.getLevelTag(profile.getPrestige(), 120));
                lore.add(" ");
                lore.add("&7精通代价:");
                boolean fastPass = false;
                PerkData data = profile.getUnlockedPerkMap().get("FastPass");
                if (data != null) {
                    lore.add("&4▶ &c重置 &b等级 &c至 50 级");
                    fastPass = true;
                }
                if (!fastPass) {
                    lore.add("&4▶ &c重置 &b等级 &c至 0 级");
                }
                lore.add("&4▶ &c重置 &6硬币 &c至 0");
                lore.add("&4▶ &c重置所有的天赋与加成");
                //lore.add("&4▶ &c重置背包物品");
                //lore.add("&4▶ &c至少持有硬币 &6" + new DecimalFormat("0.00").format(profile.getCoins()) + "g&c/&6" + Math.round(profile.getPrestige() * 12 / 30) * 10000 + "g");
                lore.add("&4▶ &c累计获得硬币 &6" + new DecimalFormat("0.00").format(profile.getGrindedCoins()) + "g&c/&6" + (profile.getPrestige() + 1) * 16000 + "g");
                lore.add("&7&o精通商店与末影箱内的物品会保留.");
                lore.add("&7&o累计获得硬币只统计战斗获得硬币与任务获得硬币.");
                lore.add(" ");
                lore.add("&7精通奖励: &e" + award + " 声望");

                lore.add("&7下一精通等级: &e" + RomanUtil.convert(profile.getPrestige() + 1));
                lore.add("&7精通后所需经验提升至原来的 &b" + new DecimalFormat("0.00").format(Math.pow(1.1, profile.getPrestige() + 1) * 100) + "%");
                lore.add(" ");
                // && profile.getCoins() >= Math.round(profile.getPrestige() * 12 / 30) * 10000
                if (profile.getGrindedCoins() >= 16000 * (profile.getPrestige() + 1)) {
                    lore.add("&e点击精通!");
                } else {
                    lore.add("&c未满足硬币要求!");
                }
            } else {
                if (profile.getPrestige() > 0) {
                    lore.add("&7当前: &e" + RomanUtil.convert(profile.getPrestige()));
                }
                lore.add("&7升级所需经验是原来的 &b" + new DecimalFormat("0.00").format(Math.pow(1.1, profile.getPrestige()) * 100) + "%");
                lore.add(" ");
                lore.add("&7所需等级: " + LevelUtil.getLevelTag(profile.getPrestige(), 120));
                lore.add("&7提升等级来进行精通吧!");
            }
        }

        return new ItemBuilder(Material.DIAMOND).name("&b精通").lore(lore).build();
    }

    private ItemStack getV2PrestigeItem(PlayerProfile profile) {
        cn.charlotte.pit.reward.EconomyV2.migrate(profile);
        DecimalFormat format = new DecimalFormat("#,##0.##");
        List<String> lore = new ArrayList<>();
        int p = profile.getPrestige();
        lore.add("&7当前精通: &eP" + p + " &7/ &eP50");
        lore.add("&7等级: &b" + profile.getLevel() + " &7/ &b120");
        lore.add("&7本轮经验: &b" + format.format(profile.getExperience()) + " &7/ &b"
                + format.format(cn.charlotte.pit.reward.ProgressionV2.totalExperience(p)));
        if (p < 50) {
            lore.add("&7累计获金: &6" + format.format(profile.getGrindedCoins()) + " &7/ &6"
                    + format.format(cn.charlotte.pit.reward.ProgressionV2.goldRequired(p)));
            lore.add("&7下轮经验储备: &b" + format.format(profile.getEconomyData().experienceReserve));
            lore.add("&8购物不减少累计获金；交易和退款不增加进度");
            lore.add("");
            lore.add("&7精通后: &e+" + cn.charlotte.pit.perk.PrestigeProgression.renownAward(p + 1) + " 声望");
            lore.add("&c余额清零，普通天赋与加成重置");
            lore.add("&7保留声望解锁、装备、末影箱及购买偏好");
            lore.add(profile.getUnlockedPerkMap().containsKey("FastPass") ? "&b高速通道: 从50级起步，并释放经验储备" : "&b释放已积累的下轮经验储备");
            lore.add(cn.charlotte.pit.perk.PrestigeProgression.eligible(profile, 50) ? "&e点击查看精通确认" : "&7继续完成本轮经验与获金目标");
        } else {
            lore.add("&7荣誉等级: &d" + (long) (profile.getEconomyData().honorExperience / cn.charlotte.pit.reward.EconomyV2.honorStep()));
            lore.add("&7达到120级后，玩法经验继续积累荣誉");
        }
        return new ItemBuilder(Material.DIAMOND).name("&b精通成长").lore(lore).build();
    }
}
