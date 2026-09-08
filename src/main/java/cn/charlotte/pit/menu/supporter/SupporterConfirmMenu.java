package cn.charlotte.pit.menu.supporter;

import cn.charlotte.pit.menu.supporter.button.SupporterProductButton;
import cn.charlotte.pit.supporter.SupporterPricing;
import cn.charlotte.pit.supporter.SupporterProduct;
import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.util.callback.Callback;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import cn.charlotte.pit.util.menu.buttons.ConfirmationButton;
import cn.charlotte.pit.util.menu.buttons.DisplayButton;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SupporterConfirmMenu extends Menu {
    private final SupporterPricing.Quote quote;
    private final Button yes;
    private final Button no;
    private final Button summary;

    public SupporterConfirmMenu(SupporterPricing.Quote quote, Callback<Boolean> callback) {
        this.quote = quote;
        yes = confirmation(true, callback, Material.EMERALD_BLOCK);
        no = confirmation(false, callback, Material.BARRIER);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7本次" + quote.getAction() + "扣除：&d" + quote.getPoints() + " 点券");
        lore.add("&7折合：&f" + SupporterPricing.yuan(quote.getPoints()) + " 元 &8（1 元=100 点券）");
        if (quote.getProduct() == SupporterProduct.PERMANENT) {
            lore.add("&7有效期：&f永久");
        } else {
            lore.add("&7仅限已拥有天坑·会员的玩家购买。");
            lore.add("&7到期：&f" + SupporterService.formatExpiry(quote.getAfter().getExpiresAt()));
            lore.add("&7神话掉落：" + SupporterProductButton.enhancement(quote.getProduct()));
            lore.add("&7稀有附魔：" + SupporterProductButton.enhancement(quote.getProduct()));
            lore.add("&7到期保留永久赞助者。");
        }
        summary = new DisplayButton(new ItemBuilder(SupporterProductButton.material(quote.getProduct()))
                .name(quote.getProduct().getTier().getColor() + quote.getProduct().getDisplayName()).lore(lore).build(), true);
    }

    private static Button confirmation(boolean confirm, Callback<Boolean> callback, Material material) {
        return new ConfirmationButton(confirm, callback, true, 3) {
            @Override
            public ItemStack getButtonItem(Player player) {
                ItemStack stack = super.getButtonItem(player);
                stack.setType(material);
                stack.setDurability((short) 0);
                return stack;
            }
        };
    }

    @Override
    public String getTitle(Player player) { return "确认" + quote.getAction() + " · " + quote.getProduct().getDisplayName(); }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> buttons = new HashMap<>();
        buttons.put(11, yes);
        buttons.put(13, summary);
        buttons.put(15, no);
        return buttons;
    }

    @Override public int getSize() { return 27; }
    @Override public boolean isAutoUpdate() { return true; }
}
