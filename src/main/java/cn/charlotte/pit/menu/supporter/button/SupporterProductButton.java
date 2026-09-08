package cn.charlotte.pit.menu.supporter.button;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.menu.supporter.SupporterConfirmMenu;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterPricing;
import cn.charlotte.pit.supporter.SupporterProduct;
import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SupporterProductButton extends Button {
    private final SupporterProduct product;

    public SupporterProductButton(SupporterProduct product) { this.product = product; }

    public static Material material(SupporterProduct product) {
        switch (product) {
            case MYTHIC: return Material.ENCHANTED_BOOK;
            case SACRED: return Material.NETHER_STAR;
            default: return Material.EMERALD;
        }
    }

    public static String percent(double chance) { return String.format(Locale.ROOT, "%.2f%%", chance * 100.0); }

    public static String enhancement(SupporterProduct product) {
        switch (product) {
            case MYTHIC: return "&a略微提高";
            case SACRED: return "&c大幅提高";
            default: return "&7常规";
        }
    }

    @Override
    public ItemStack getButtonItem(Player player) {
        NewConfiguration config = NewConfiguration.INSTANCE;
        SupporterPricing pricing = config.supporterPricing();
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        SupporterAccount account = profile.getSupporterAccount();
        SupporterService service = SupporterService.get();
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (product == SupporterProduct.PERMANENT) {
            lore.add("&7永久身份、可开关星标");
            lore.add("&7彩色装备改名、神话之甲染色");
            lore.add("&7物品展示、隐私设置、事件排期");
            lore.add("&7改名免费额度：每日 " + config.getRenameDailyFreeLimit() + " / 每周 " + config.getRenameWeeklyFreeLimit());
            lore.add("&7超额改名：&d" + config.getRenameExtraCost() + " 点券/次");
            lore.add("&7军需处：可接受圣契邀请，以访客进入");
            if (cn.charlotte.pit.reward.EconomyV2.active()) lore.add("&7三套构筑预设与个人成长记录");
        } else {
            lore.add("&7神话掉落：" + enhancement(product));
            lore.add("&7稀有附魔：" + enhancement(product));
            lore.add("&7独立军需处：每日 " + (product == SupporterProduct.SACRED ? "8" : "4") + " 小时");
            lore.add("&7附魔、天赋、精通三名专属导师");
            if (product == SupporterProduct.SACRED) {
                lore.add("&7十名训练战魂，可发送圣契邀请函");
            } else lore.add("&7五名训练战魂，可设置练习参数");
            if (cn.charlotte.pit.reward.EconomyV2.active()) {
                lore.add("&7构筑与训练预设：" + (product == SupporterProduct.SACRED ? "12" : "6") + " 套");
                lore.add("&7训练成长记录：最近 " + (product == SupporterProduct.SACRED ? "30" : "7") + " 天");
            }
            lore.add("&7购买前提：&e已拥有天坑·会员");
            lore.add("&7两种契约择一生效，到期保留赞助者。");
        }
        lore.add("");
        int price = pricing.price(product);
        lore.add("&7售价：&d" + price + " 点券 &7（" + SupporterPricing.yuan(price) + " 元）");
        lore.add("&7期限：&f" + (product == SupporterProduct.PERMANENT ? "永久" : config.getVipDurationDays() + " 天"));
        if (account != null && account.activeTier(System.currentTimeMillis()) != SupporterTier.NONE) {
            lore.add("&7当前契约：" + account.getTier().getColor() + account.getTier().getDisplayName());
            lore.add("&7到期：&f" + SupporterService.formatExpiry(account.getExpiresAt()));
        }
        lore.add("");
        if (service == null || !service.isReady() || account == null || !profile.isLoaded()) {
            lore.add("&e赞助者资料载入中，稍后重试。");
        } else {
            try {
                SupporterPricing.Quote quote = pricing.quote(account, product,
                        player.hasPermission(config.getPitSupportPermission()), System.currentTimeMillis());
                if (quote.getAction().equals("补差价升级")) {
                    lore.add("&7升级剩余期限补差：&d" + quote.getPoints() + " 点券");
                    lore.add("&7升级后保持当前到期时间。");
                }
                int balance = balance(player);
                if (balance == Integer.MIN_VALUE) lore.add("&7点券余额：&e载入中");
                else lore.add("&7点券余额：&d" + balance);
                lore.add(balance != Integer.MIN_VALUE && balance < quote.getPoints()
                        ? "&c点券不足" : "&e点击" + quote.getAction());
            } catch (IllegalArgumentException exception) {
                lore.add("&7" + exception.getMessage());
            }
        }
        return new ItemBuilder(material(product)).name(product.getTier().getColor() + product.getDisplayName())
                .lore(lore).build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack item) {
        SupporterService service = SupporterService.get();
        if (service == null) { player.sendMessage(CC.translate("&c赞助服务尚未就绪。")); return; }
        try {
            SupporterPricing pricing = service.pricing();
            SupporterPricing.Quote quote = service.quote(player, product, pricing);
            UUID orderId = UUID.randomUUID();
            new SupporterConfirmMenu(quote, confirmed -> {
                if (confirmed) service.purchase(player, pricing, quote, orderId);
            }).openMenu(player);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(CC.translate("&c" + exception.getMessage()));
        }
    }

    private int balance(Player player) {
        try { return ThePit.getInstance().getPointsAPI().getPoints(player); }
        catch (Exception exception) { return Integer.MIN_VALUE; }
    }
}
