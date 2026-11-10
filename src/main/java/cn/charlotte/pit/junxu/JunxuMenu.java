package cn.charlotte.pit.junxu;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class JunxuMenu implements Listener {
    private final JunxuManager manager;
    public JunxuMenu(JunxuManager manager){this.manager=manager;}
    @EventHandler(priority=EventPriority.HIGHEST)public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof View))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player))return;Player player=(Player)event.getWhoClicked();
        if(!player.hasPermission("junxu.use"))return;
        int slot=event.getRawSlot();if(slot<0||slot>=27)return;
        if(slot==7)manager.trainingMenu(player);
        if(slot==10)manager.open(player);
        if(slot==12)manager.enterOwn(player);
        if(slot==14)manager.closeOwn(player);
        if(slot==16){player.closeInventory();manager.leave(player);}
        if(slot==21||slot==23){player.closeInventory();manager.showVisitors(player);}
        if(slot==25){player.closeInventory();manager.story().replay(player);}
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);}
    public static void refresh(JunxuManager manager){for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof View)((View)player.getOpenInventory().getTopInventory().getHolder()).update();}
    public static final class View implements InventoryHolder {
        private final JunxuManager manager;private final Player player;private final Inventory inventory;
        View(JunxuManager manager,Player player){this.manager=manager;this.player=player;inventory=Bukkit.createInventory(this,27,"第七军需处 · 契约管理");}
        void open(){update();player.openInventory(inventory);}
        public Inventory getInventory(){return inventory;}
        void update(){
            JunxuRoom room=manager.room(player.getUniqueId());boolean open=room!=null&&room.open;
            inventory.setItem(1,item(Material.NETHER_STAR,"§6"+manager.tierName(player),"§7以当前最高有效契约计算", "§7神话契约：每日 4 小时", "§7神话圣契：每日 8 小时"));
            inventory.setItem(3,item(Material.WATCH,"§e今日额度","§7已用：§f"+JunxuManager.duration(manager.ledger().used(player.getUniqueId(),System.currentTimeMillis())),"§7剩余：§f"+JunxuManager.duration(manager.remaining(player)),"§7每日 00:00 刷新 · "+manager.ledger().zone()));
            inventory.setItem(5,item(Material.COMPASS,"§b房间："+(open?"开启中":manager.building(player.getUniqueId())?"构筑中":"已关闭"),"§7功能 NPC：§f"+(open?manager.npcs().count(room):0)+" / 3","§7训练战魂：§f"+(open?manager.bots().count(room):0)+" / "+JunxuEntitlements.bots(manager.tier(player))));
            inventory.setItem(10,item(Material.EMERALD_BLOCK,"§a开启房间",open?"§7当前已开启":"§7开启后开始累计扣时","§7自动进入自己的独立空间"));
            inventory.setItem(12,item(Material.ENDER_PEARL,"§b进入房间",open?"§7返回自己的第七军需处":"§7请先开启房间"));
            inventory.setItem(14,item(Material.REDSTONE_BLOCK,"§c关闭房间","§7停止扣时并送回全部访客","§7清理战魂，保留精装建筑"));
            inventory.setItem(16,item(Material.IRON_DOOR,"§e返回公共天坑","§7房主离开 "+manager.absenceSeconds()+" 秒后自动关闭","§7访客留守不能延长开放"));
            boolean sacred=manager.tier(player)==cn.charlotte.pit.supporter.SupporterTier.SACRED;
            inventory.setItem(7,JunxuEntitlements.bots(manager.tier(player))>0?item(Material.ANVIL,"§6训练设置","§7血量、装备、敌对行为","§7路线、强度、暂停与恢复"):null);
            inventory.setItem(21,sacred?item(Material.BOOK_AND_QUILL,"§6圣契邀请函","§f/junxu invite <玩家名>","§7仅本次开放有效；"+manager.invitationSeconds()+" 秒到期","§7同时接待 "+manager.visitorCap()+" 名访客"):null);
            inventory.setItem(23,sacred?item(Material.SKULL_ITEM,"§f访客名单", "§7当前："+(open?room.guests.size():0)+" / "+manager.visitorCap(),"§7点击查看与移除访客"):null);
            inventory.setItem(25,item(Material.BOOK,"§6回顾《余烬之约》","§7进入房间后播放，可随时跳过"));
        }
        private ItemStack item(Material type,String name,String...lore){ItemStack item=new ItemStack(type);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);return item;}
    }
}
