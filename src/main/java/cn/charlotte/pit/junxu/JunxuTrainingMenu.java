package cn.charlotte.pit.junxu;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class JunxuTrainingMenu implements Listener {
    private final JunxuManager manager;
    public JunxuTrainingMenu(JunxuManager manager) { this.manager = manager; }
    public void open(Player player) {
        if (JunxuEntitlements.bots(manager.tier(player)) == 0) {
            manager.tell(player, "&c神话契约或神话圣契房主可以设置自己的训练战魂。"); return;
        }
        View view = new View(player); view.update(); player.openInventory(view.inventory);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JunxuTrainingMenu.View)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || (!event.isLeftClick() && !event.isRightClick())) return;
        Player player = (Player) event.getWhoClicked();
        JunxuTrainingSettings value = manager.trainingSettings(player.getUniqueId());
        int direction = event.isRightClick() ? -1 : 1;
        switch (event.getRawSlot()) {
            case 10: value = value.health(Math.max(1, Math.min(manager.maximumBotHealth(), value.health + direction * (event.isShiftClick() ? 1 : 10)))); break;
            case 12: value = value.armor(cycle(JunxuTrainingSettings.Armor.values(), value.armor.ordinal(), direction)); break;
            case 14: value = value.mode(cycle(JunxuTrainingSettings.Mode.values(), value.mode.ordinal(), direction)); break;
            case 16: value = value.weapon(cycle(JunxuTrainingSettings.Weapon.values(), value.weapon.ordinal(), direction)); break;
            case 20: value = value.route(cycle(JunxuTrainingSettings.Route.values(),value.route.ordinal(),direction)); break;
            case 22: value = value.strength(cycle(JunxuTrainingSettings.Strength.values(),value.strength.ordinal(),direction)); break;
            case 24: value = value.paused(!value.paused); break;
            default: return;
        }
        manager.configureTraining(player, value);
        ((View) event.getView().getTopInventory().getHolder()).update();
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof JunxuTrainingMenu.View) event.setCancelled(true);
    }
    private static <T> T cycle(T[] values, int current, int direction) { return values[Math.floorMod(current + direction, values.length)]; }
    private final class View implements InventoryHolder {
        final Player player; final Inventory inventory;
        View(Player player) { this.player = player; inventory = Bukkit.createInventory(this, 27, "第七军需处 · 训练设置"); }
        public Inventory getInventory() { return inventory; }
        void update() {
            JunxuTrainingSettings value = manager.trainingSettings(player.getUniqueId());
            inventory.setItem(10, item(Material.APPLE, "§c生命值：" + value.health + " §7（" + value.health / 2 + " 心）",
                    "§7左键 +10，右键 -10", "§7按住 Shift 每次调整 1", "§7范围 1–" + manager.maximumBotHealth() + " 生命值"));
            inventory.setItem(12, item(Material.DIAMOND_CHESTPLATE, "§b盔甲：" + value.armor.title,
                    "§7左键下一套，右键上一套", "§7无甲 / 皮革 / 锁链 / 铁 / 钻石"));
            inventory.setItem(14, item(Material.REDSTONE_TORCH_ON, "§e行为：" + value.mode.title,
                    "§7左键切换，右键反向切换", "§7主动攻击 / 受击反击 / 不还手"));
            inventory.setItem(16, item(Material.IRON_SWORD, "§f武器：" + value.weapon.title,
                    "§7左键下一项，右键上一项", "§7空手 / 木剑 / 石剑 / 铁剑 / 钻石剑"));
            inventory.setItem(20,item(Material.RAILS,"§b路线："+value.route.title,
                    "§7分组 / 环场 / 交叉 / 持续跟随 / 驻守","§7左键切换，右键反向切换"));
            inventory.setItem(22,item(Material.BLAZE_POWDER,"§6强度："+value.strength.title,
                    "§7轻度 / 标准 / 强劲","§7调整移动速度与出手频率"));
            inventory.setItem(24,item(Material.LEVER,value.paused?"§e状态：已暂停":"§a状态：训练中",
                    value.paused?"§7点击恢复训练":"§7点击暂停移动与攻击","§7暂停不停止房间开放计时"));
        }
    }
    private ItemStack item(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title); meta.setLore(Arrays.asList(lore)); item.setItemMeta(meta); return item;
    }
}
