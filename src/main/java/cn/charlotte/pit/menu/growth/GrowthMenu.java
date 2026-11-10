package cn.charlotte.pit.menu.growth;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.menu.prestige.button.PrestigeStatusButton;
import cn.charlotte.pit.menu.supporter.SupporterMenu;
import cn.charlotte.pit.reward.BuildPresets;
import cn.charlotte.pit.reward.EconomyAudit;
import cn.charlotte.pit.reward.GrowthProgress;
import cn.charlotte.pit.reward.ProgressionV2;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import static cn.charlotte.pit.reward.GrowthProgress.number;

/** Every entry opens an existing action or exposes progress needed to choose the next activity. */
public final class GrowthMenu extends Menu {
    public enum Page { MAIN, WEEKLY, BUILDS, TRAINING, REPORT, HONOR }
    private final Page page;
    private static final Material[] PRESET_ICONS = {Material.WOOD_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.DIAMOND_SWORD, Material.GOLD_SWORD, Material.WOOD_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.DIAMOND_AXE, Material.GOLD_AXE, Material.BOW, Material.FISHING_ROD};
    public GrowthMenu() { this(Page.MAIN); }
    public GrowthMenu(Page page) { this.page = page; }
    @Override public int getSize() { return 54; }
    @Override public boolean isAutoUpdate() { return true; }
    @Override public String getTitle(Player player) {
        switch (page) {
            case WEEKLY: return "周目标 · 任选三项";
            case BUILDS: return "我的构筑方案";
            case TRAINING: return "我的训练方案";
            case REPORT: return "成长与训练记录";
            case HONOR: return "荣誉收藏";
            default: return "天坑成长 · P50";
        }
    }

    @Override public Map<Integer, Button> getButtons(Player player) {
        PlayerProfile p = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        Map<Integer, Button> b = new LinkedHashMap<>();
        if (page != Page.MAIN) b.put(45, button(Material.ARROW, "&e返回成长", List.of(), (who, click) -> new GrowthMenu().openMenu(who)));
        switch (page) {
            case MAIN: main(b, p); break;
            case WEEKLY: weekly(b, p); break;
            case BUILDS: presets(b, p, false); break;
            case TRAINING: presets(b, p, true); break;
            case REPORT: report(b, p); break;
            case HONOR: honor(b, p); break;
        }
        return b;
    }

    private void main(Map<Integer, Button> b, PlayerProfile p) {
        boolean maximumPrestige = p.getPrestige() >= ProgressionV2.MAX_PRESTIGE;
        b.put(10, button(Material.EXP_BOTTLE, "&b等级经验", List.of("&7等级 &b" + p.getLevel() + "/120",
                "&7经验 &b" + number(p.getExperience()) + "/" + number(ProgressionV2.totalExperience(p.getPrestige())),
                maximumPrestige ? "&7满120级后继续积累荣誉经验" : "&7下轮储备 &b" + number(p.getEconomyData().experienceReserve)), (who, click) -> new GrowthMenu(Page.REPORT).openMenu(who)));
        b.put(11, button(Material.GOLD_INGOT, "&6金币进度", List.of("&7余额 &6" + number(p.getCoins()),
                "&7本轮累计 &6" + number(p.getGrindedCoins()) + (maximumPrestige ? "" : "/" + number(ProgressionV2.goldRequired(p.getPrestige()))),
                maximumPrestige ? "&7已达最高精通，无后续获金门槛" : "&7购物不会减少累计进度"), (who, click) -> new GrowthMenu(Page.REPORT).openMenu(who)));
        b.put(12, new PrestigeStatusButton());
        b.put(14, button(Material.BOOK_AND_QUILL, "&a周目标", List.of("&7战斗、挑战、钓鱼、探索、事件", "&7任选三项领取本周奖励", "&7未完成目标可保留至下一周"),
                (who, click) -> new GrowthMenu(Page.WEEKLY).openMenu(who)));
        b.put(15, button(Material.ARMOR_STAND, "&e构筑方案", List.of("&7可用栏位 &e" + GrowthProgress.presetSlots(p), "&7保存已选天赋与自动购买偏好", "&7安全区内应用已拥有的天赋"),
                (who, click) -> new GrowthMenu(Page.BUILDS).openMenu(who)));
        b.put(16, button(Material.IRON_SWORD, "&b训练方案", List.of("&7保存军需处战魂的训练设置", "&7金币与经验随训练量浮动", "&7连续高速训练的超出部分收益降低"),
                (who, click) -> new GrowthMenu(Page.TRAINING).openMenu(who)));
        b.put(28, button(Material.WATCH, "&e成长记录", List.of("&7本次游玩收获与金币支出", "&7可查看最近 " + GrowthProgress.reportDays(p) + " 天"),
                (who, click) -> new GrowthMenu(Page.REPORT).openMenu(who)));
        b.put(30, button(Material.NAME_TAG, "&d荣誉收藏", List.of("&7满 P50、120级后继续积累荣誉", "&7称号只改变展示效果"),
                (who, click) -> new GrowthMenu(Page.HONOR).openMenu(who)));
        int flags = p.getEconomyData().newcomerMilestones;
        b.put(32, button(Material.NETHER_STAR, "&a初入天坑", List.of(flag(flags, 1, "首个有效击杀"), flag(flags, 2, "10个标准击杀单位"),
                flag(flags, 4, "完成首个挑战"), flag(flags, 8, "达到60级"), "&7P0–P2期间完成后自动领取", "&7合计 1000金币与4000经验"),
                (who, click) -> new cn.charlotte.pit.menu.quest.main.QuestMenu().openMenu(who)));
        b.put(34, button(Material.EMERALD, "&a天坑会员与契约", List.of("&7查看现有权益、有效期与购买记录"), (who, click) -> new SupporterMenu().openMenu(who)));
    }

    private static String flag(int flags, int bit, String title) { return ((flags & bit) != 0 ? "&a✓ " : "&7○ ") + title; }

    private void weekly(Map<Integer, Button> b, PlayerProfile p) {
        GrowthProgress.currentWeek(p.getEconomyData(), System.currentTimeMillis());
        Material[] icons = {Material.IRON_SWORD, Material.PAPER, Material.FISHING_ROD, Material.CHEST, Material.NETHER_STAR};
        // One unfinished week is shown at a time; finishing carried goals exposes the current week.
        EconomyData.WeekProgress week = p.getEconomyData().weeks.stream().filter(w -> w.claimed.size() < 3)
                .findFirst().orElse(p.getEconomyData().weeks.get(p.getEconomyData().weeks.size() - 1));
        for (GrowthProgress.Goal goal : GrowthProgress.Goal.values()) {
            double progress = week.progress.getOrDefault(goal.name(), 0.0);
            List<String> lore = new ArrayList<>(List.of("&7进度 &f" + number(progress) + "/" + number(goal.target),
                    "&6" + number(250 * ProgressionV2.sideScale(p.getPrestige())) + " 金币 &b"
                            + number(1000 * ProgressionV2.sideScale(p.getPrestige())) + " 经验"));
            lore.add(week.claimed.contains(goal.name()) ? "&a本周已领取" : week.claimed.size() >= 3 ? "&7本周三项目标已完成"
                    : progress >= goal.target ? "&e左键领取" : "&7继续参与对应玩法");
            b.put(11 + goal.ordinal(), button(icons[goal.ordinal()], "&a" + goal.title, lore,
                    (who, click) -> { if (click == ClickType.LEFT) GrowthProgress.claim(p, week.start, goal); }));
        }
        b.put(31, button(Material.HOPPER, "&e三项目标奖励", List.of("&7已领取 " + week.claimed.size() + "/3 项",
                "&7第三项领取时自动追加", "&6" + number(500 * ProgressionV2.sideScale(p.getPrestige())) + " 金币 &b"
                        + number(2000 * ProgressionV2.sideScale(p.getPrestige())) + " 经验"), (who, click) -> { }));
    }

    private void presets(Map<Integer, Button> b, PlayerProfile p, boolean training) {
        for (int i = 1; i <= GrowthProgress.presetSlots(p); i++) {
            final int index = i;
            String key = Integer.toString(i);
            boolean saved = training ? p.getEconomyData().trainingPresets.containsKey(key) : p.getEconomyData().builds.containsKey(key);
            List<String> lore = new ArrayList<>(); lore.add(saved ? "&a已保存" : "&7尚未保存");
            if (!training && saved) p.getEconomyData().builds.get(key).selected.values().stream().limit(8).forEach(perk -> {
                cn.charlotte.pit.perk.AbstractPerk item = ThePit.getInstance().getPerkFactory().getPerkMap().get(perk.getPerkInternalName());
                if (item != null) lore.add("&7" + item.getDisplayName());
            });
            lore.add("&e左键应用"); lore.add("&e右键用当前设置覆盖此栏");
            b.put(10 + (i - 1) / 6 * 9 + (i - 1) % 6, button(PRESET_ICONS[i - 1], "&e方案 " + i, lore, (who, click) -> {
                if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
                if (training) BuildPresets.training(who, index, click == ClickType.RIGHT);
                else if (click == ClickType.RIGHT) BuildPresets.save(who, index); else BuildPresets.apply(who, index);
            }));
        }
        if (training) b.put(31, button(Material.ARMOR_STAND, "&b调整当前训练", List.of("&7打开军需处训练设置"),
                (who, click) -> ThePit.getInstance().getJunxuManager().trainingMenu(who)));
    }

    private void report(Map<Integer, Button> b, PlayerProfile p) {
        double[] session = GrowthProgress.session(p);
        b.put(11, button(Material.GOLD_INGOT, "&6本次金币", List.of("&7奖励收入 &6" + number(session[0]), "&7余额支出 &6" + number(session[1])), (who, click) -> { }));
        b.put(13, button(Material.EXP_BOTTLE, "&b本次经验", List.of("&7等级经验 &b" + number(session[2]), "&7新增储备 &b" + number(session[3])), (who, click) -> { }));
        int days = GrowthProgress.reportDays(p);
        List<String> history = new ArrayList<>();
        String cutoff = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(days - 1).toString();
        p.getEconomyData().reports.entrySet().stream().filter(e -> e.getKey().compareTo(cutoff) >= 0)
                .sorted(Map.Entry.<String, EconomyData.DailyReport>comparingByKey().reversed()).limit(7)
                .forEach(e -> history.add("&7" + e.getKey() + " &6+" + number(e.getValue().coinsIn) + " &b+" + number(e.getValue().experience)));
        if (history.isEmpty()) history.add("&7参与玩法后开始记录");
        if (days > 7) history.add("&e左键输出完整30天记录到聊天");
        b.put(15, button(Material.BOOK, "&e最近 " + days + " 天", history, (who, click) -> {
            p.getEconomyData().reports.entrySet().stream().filter(e -> e.getKey().compareTo(cutoff) >= 0).sorted(Map.Entry.comparingByKey())
                    .forEach(e -> who.sendMessage(CC.translate("&7" + e.getKey() + " &6奖励 " + number(e.getValue().coinsIn)
                            + " / 支出 " + number(e.getValue().coinsOut) + " &b经验 " + number(e.getValue().experience))));
        }));
        b.put(31, button(Material.IRON_SWORD, "&b训练贡献", List.of("&7今日标准击杀单位 &b" + number(EconomyAudit.report(p).normalizedKills),
                "&7正常训练与高强度训练均保留收益", "&7私人训练的血量装备只影响难度"), (who, click) -> new GrowthMenu(Page.TRAINING).openMenu(who)));
    }

    private void honor(Map<Integer, Button> b, PlayerProfile p) {
        b.put(4, button(Material.NETHER_STAR, "&d荣誉等级 " + (long) (p.getEconomyData().honorExperience / cn.charlotte.pit.reward.EconomyV2.honorStep()),
                List.of("&7当前荣誉经验 &d" + number(p.getEconomyData().honorExperience), "&7每" + number(cn.charlotte.pit.reward.EconomyV2.honorStep()) + "经验提升一级", "&7在P50、120级之后积累"), (who, click) -> { }));
        String[] keys = {"bronze", "silver", "gold"}; String[] names = {"历练者", "远征者", "天坑传奇"};
        int[] prices = {20000, 80000, 200000}; Material[] icons = {Material.BRICK, Material.IRON_INGOT, Material.GOLD_INGOT};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i]; final int price = prices[i];
            boolean owned = p.getEconomyData().cosmetics.contains(key);
            b.put(11 + 2 * i, button(icons[i], "&e" + names[i], List.of(owned ? "&a已拥有 · 左键装备" : "&6价格 " + number(price) + " 金币",
                    "&7聊天与战斗消息中的展示称号", "&7只改变外观，不增加战斗属性", owned ? "&7保留至所有后续精通" : "&e左键购买并装备"),
                    (who, click) -> { if (click == ClickType.LEFT && !GrowthProgress.cosmetic(p, key, price)) who.sendMessage(CC.translate("&c金币不足。")); }));
        }
        if (p.getEconomyData().migratedAt > 0) b.put(30, button(Material.BOOK, "&7旧版精通记录",
                List.of("&7更新前精通：P" + p.getEconomyData().legacyPrestige, "&e左键查看成长记录"), (who, click) -> new GrowthMenu(Page.REPORT).openMenu(who)));
        b.put(32, button(Material.SHEARS, "&7隐藏展示称号", List.of("&7保留已拥有的收藏"), (who, click) -> p.getEconomyData().selectedCosmetic = ""));
    }

    private static Button button(Material material, String title, List<String> lore, BiConsumer<Player, ClickType> action) {
        return new Button() {
            @Override public ItemStack getButtonItem(Player player) { return new ItemBuilder(material).name(title).lore(lore).build(); }
            @Override public void clicked(Player player, int slot, ClickType click, int hotbar, ItemStack item) {
                try { action.accept(player, click); }
                catch (IllegalArgumentException failure) { player.sendMessage(CC.translate("&c" + failure.getMessage())); }
            }
            @Override public boolean shouldUpdate(Player player, int slot, ClickType click) { return true; }
        };
    }
}
