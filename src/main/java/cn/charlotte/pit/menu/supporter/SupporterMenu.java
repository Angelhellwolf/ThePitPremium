package cn.charlotte.pit.menu.supporter;

import cn.charlotte.pit.menu.option.button.SupportStarOptionButton;
import cn.charlotte.pit.menu.supporter.button.SupporterRenameButton;
import cn.charlotte.pit.menu.supporter.button.SupporterProductButton;
import cn.charlotte.pit.menu.supporter.button.SupporterHistoryButton;
import cn.charlotte.pit.supporter.SupporterProduct;
import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.menu.workshop.button.WorkshopEntranceButton;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.Menu;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * @Creator Misoryan
 * @Date 2021/5/4 10:16
 */
public class SupporterMenu extends Menu {
    @Override
    public String getTitle(Player player) {
        return "天坑赞助";
    }

    @Override
    public Map<Integer, Button> getButtons(Player player) {
        Map<Integer, Button> button = new HashMap<>();
        button.put(10, new SupporterProductButton(SupporterProduct.PERMANENT));
        button.put(13, new SupporterProductButton(SupporterProduct.MYTHIC));
        button.put(16, new SupporterProductButton(SupporterProduct.SACRED));
        button.put(20, new SupportStarOptionButton());
        button.put(21, new SupporterRenameButton());
        button.put(23, new WorkshopEntranceButton());
        button.put(24, new SupporterHistoryButton());
        return button;
    }

    @Override
    public int getSize() {
        return 3 * 9;
    }

    @Override public boolean isAutoUpdate() { return true; }

    @Override
    public void onOpen(Player player) {
        SupporterService service = SupporterService.get();
        if (service != null) service.refresh(player);
    }
}
