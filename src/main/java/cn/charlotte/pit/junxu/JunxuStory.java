package cn.charlotte.pit.junxu;

import net.md_5.bungee.api.chat.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;

/** Short, cancellable personal introduction; no shared quest or repeated gameplay requirement. */
public final class JunxuStory implements Listener {
    private final JunxuManager manager;
    private final Map<UUID,Scene> scenes=new HashMap<>();
    private final BukkitTask task;
    public JunxuStory(JunxuManager manager){
        this.manager=manager;Bukkit.getPluginManager().registerEvents(this,manager.plugin());
        task=Bukkit.getScheduler().runTaskTimer(manager.plugin(),this::tick,10,10);
    }
    public void replay(Player player){
        JunxuRoom room=manager.at(player.getLocation());
        if(!manager.authorized(player,room)){manager.tell(player,"&e进入自己的或受邀的军需处后，可回顾《余烬之约》。");return;}
        play(player,room,true);
    }
    public void play(Player player,JunxuRoom room,boolean replay){
        cancel(player.getUniqueId());
        Scene scene=new Scene(player,room);scenes.put(player.getUniqueId(),scene);
        player.teleport(room.arrival());
        manager.tell(player,"&6《第七军需处·余烬之约》"+(replay?" &7· 回顾":""));
        manager.tell(player,"&6洛恩：&f第七军需处仍在值守。报上你的契约，我们为下一场战斗做准备。");
        manager.tell(player,"&7裂隙吞没军需处后，三位导师守住了星火核心。现在，你的契约正重新接通它。");
        TextComponent skip=new TextComponent("§e[跳过剧情，直接使用军需处]");
        skip.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/junxu story skip"));player.spigot().sendMessage(skip);
        if(room.owner.equals(player.getUniqueId())&&room.tier==cn.charlotte.pit.supporter.SupporterTier.SACRED)manager.bots().assemble(room,2400);
        // Personal block changes are restored on every finish/cancel path.
        for(Location lamp:Arrays.asList(room.at(20,28,5,0),room.at(40,28,5,0),room.at(30,25,23,0))){
            scene.lamps.add(lamp);player.sendBlockChange(lamp,Material.COAL_BLOCK,(byte)0);
        }
    }
    private void tick(){
        long now=System.currentTimeMillis();
        for(Scene scene:new ArrayList<>(scenes.values())){
            Player player=scene.player;
            if(!player.isOnline()||!manager.authorized(player,scene.room)||!scene.room.contains(player.getLocation())){cancel(player.getUniqueId());continue;}
            long elapsed=now-scene.started;
            if(scene.step==0&&elapsed>=1500){restore(scene,0);player.playSound(player.getLocation(),Sound.ORB_PICKUP,1,0.8F);scene.step++;}
            if(scene.step==1&&elapsed>=3000){restore(scene,1);scene.step++;}
            if(scene.step==2&&elapsed>=4500){restore(scene,2);manager.tell(player,"&6洛恩：&f军印已核验。让我们认识仍在值守的人。");scene.step++;}
            if(scene.step==3&&elapsed>=6500){
                player.teleport(scene.room.at(20.5,23,18.5,90));
                manager.tell(player,"&d伊瑟：&f刃口会钝，刻进兵器的符文不会忘记它的使命。");
                manager.tell(player,"&7符文工坊提供附魔。消耗和解锁条件沿用你的神话之井。");scene.step++;
            }
            if(scene.step==4&&elapsed>=11500){
                player.teleport(scene.room.at(40.5,23,18.5,270));
                manager.tell(player,"&b凯恩：&f先选好你要走的路，再决定如何赢下下一场。");
                manager.tell(player,"&7战术室提供天赋。选择与购买使用你自己的等级、金币和解锁记录。");scene.step++;
            }
            if(scene.step==5&&elapsed>=16500){
                player.teleport(scene.room.at(27.5,23,30.5,90));
                manager.tell(player,"&6雷铎：&f兵器认得握住它的人。去演武场，让我看看你的本事。");
                manager.tell(player,"&7兵器陈列廊提供精通。跨过后厅金线后才进入战斗区。");scene.step++;
            }
            if(scene.step==6&&elapsed>=21500){
                player.teleport(scene.room.at(30.5,23,20.5,0));
                manager.tell(player,"&6重燃军印 &7· 右键触碰前方星火核心，完成引导。");scene.step++;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void touch(PlayerInteractEvent event){
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null)return;
        Player player=event.getPlayer();JunxuRoom room=manager.at(player.getLocation());
        if(!manager.authorized(player,room))return;
        Location at=event.getClickedBlock().getLocation(),core=room.at(30,24,23,0);
        boolean body=at.getWorld()==core.getWorld()&&Math.abs(at.getBlockX()-core.getBlockX())<=1
                &&Math.abs(at.getBlockZ()-core.getBlockZ())<=1&&at.getBlockY()>=core.getBlockY()-1&&at.getBlockY()<=core.getBlockY()+2;
        boolean label=at.getWorld()==core.getWorld()&&at.getBlockX()==core.getBlockX()
                &&at.getBlockZ()==core.getBlockZ()-4&&at.getBlockY()==core.getBlockY()-1;
        if(!body&&!label)return;
        event.setCancelled(true);Scene scene=scenes.get(player.getUniqueId());
        if(scene==null){manager.menu(player);return;}
        if(scene.step<7){manager.tell(player,"&e导师介绍尚未结束。稍候再触碰核心，或使用 /junxu story skip 跳过引导。");return;}
        complete(scene,false);
    }
    public void skip(Player player){Scene scene=scenes.get(player.getUniqueId());if(scene==null){manager.tell(player,"&7当前没有正在播放的剧情。");return;}complete(scene,true);}
    private void complete(Scene scene,boolean skip){
        Player player=scene.player;cancel(player.getUniqueId());
        try{manager.ledger().completeIntroduction(player.getUniqueId());}catch(IOException error){manager.plugin().getLogger().log(java.util.logging.Level.SEVERE,"军需处引导进度保存失败",error);}
        if(!skip){
            player.playSound(player.getLocation(),Sound.LEVEL_UP,1,0.8F);
            manager.tell(player,"&6军印重新燃起。&f星火核心记住了你的名字。");
            if(scene.room.tier==cn.charlotte.pit.supporter.SupporterTier.SACRED){
                if(scene.room.owner.equals(player.getUniqueId()))manager.bots().assemble(scene.room,100);
                player.teleport(scene.room.at(30.5,23,32,0));
                manager.tell(player,"&b十名远征军战魂列队苏醒。&f完整圣契已点亮演武场。");
            }
        }else manager.tell(player,"&7已跳过引导。之后可在军需官处回顾《余烬之约》。");
    }
    @SuppressWarnings("deprecation")private void restore(Scene scene,int index){
        Location at=scene.lamps.get(index);scene.player.sendBlockChange(at,at.getBlock().getType(),at.getBlock().getData());
    }
    public void cancel(UUID player){Scene scene=scenes.remove(player);if(scene==null)return;for(int i=0;i<scene.lamps.size();i++)if(scene.player.isOnline())restore(scene,i);
        if(scene.room.owner.equals(player))manager.bots().assemble(scene.room,0);
    }
    public void cancel(JunxuRoom room){for(Scene scene:new ArrayList<>(scenes.values()))if(scene.room==room)cancel(scene.player.getUniqueId());}
    public int count(){return scenes.size();}
    public int step(UUID player){Scene scene=scenes.get(player);return scene==null?-1:scene.step;}
    public void shutdown(){task.cancel();for(UUID id:new HashSet<>(scenes.keySet()))cancel(id);}
    private static final class Scene {
        final Player player;final JunxuRoom room;final long started=System.currentTimeMillis();final List<Location> lamps=new ArrayList<>();int step;
        Scene(Player player,JunxuRoom room){this.player=player;this.room=room;}
    }
}
