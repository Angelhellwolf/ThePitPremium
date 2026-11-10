package cn.charlotte.pit.menu.supporter.button;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.menu.supporter.SupporterMenu;
import cn.charlotte.pit.supporter.SupporterPricing;
import cn.charlotte.pit.supporter.SupporterProduct;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public class SupporterEntranceButton extends Button {
    @Override
    public ItemStack getButtonItem(Player player) {
        SupporterPricing pricing = NewConfiguration.INSTANCE.supporterPricing();
        return new ItemBuilder(Material.EMERALD).name("&e天坑赞助")
                .lore(Arrays.asList("",
                        "&e天坑·会员 &7永久 &d" + pricing.price(SupporterProduct.PERMANENT) + " 点券",
                        "&d神话契约 &7" + NewConfiguration.INSTANCE.getVipDurationDays() + " 天 &d" + pricing.price(SupporterProduct.MYTHIC) + " 点券",
                        "&c神话圣契 &7" + NewConfiguration.INSTANCE.getVipDurationDays() + " 天 &d" + pricing.price(SupporterProduct.SACRED) + " 点券",
                        "", "&7契约提高神话掉落与稀有附魔概率。", "&e点击购买或管理权益"))
                .build();
    }

    @Override
    public void clicked(Player player, int slot, ClickType clickType, int hotbarButton, ItemStack currentItem) {
        new SupporterMenu().openMenu(player);
    }
}