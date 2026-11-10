package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.MessageType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;

/** Restricts announcement recipients, never the event, counters, perk effects or rewards. */
public final class JunxuMessages {
    private JunxuMessages() {}
    private static Player actor(UUID id) {
        Player player=Bukkit.getPlayer(id);ThePit plugin=ThePit.getInstance();
        if(player==null&&plugin!=null&&plugin.getPitBotManager()!=null)player=plugin.getPitBotManager().getPlayer(id);
        return player;
    }
    private static JunxuManager manager(){ThePit plugin=ThePit.getInstance();return plugin==null?null:plugin.getJunxuManager();}
    private static boolean privateActor(UUID id){
        JunxuManager manager=manager();if(manager==null)return false;Player player=actor(id);
        return player!=null?manager.isWorld(player.getWorld()):manager.room(id)!=null;
    }
    public static List<Player> audience(UUID id){
        if(!privateActor(id))return new ArrayList<>(Bukkit.getOnlinePlayers());
        JunxuManager manager=manager();Player player=actor(id);JunxuRoom room=player==null?manager.room(id):manager.at(player.getLocation());
        if(room==null)return Collections.emptyList();
        List<Player> result=new ArrayList<>();for(Player viewer:manager.occupants(room))if(room.permits(viewer.getUniqueId()))result.add(viewer);
        return result;
    }
    public static void broadcast(UUID actor,MessageType type,String message){
        if(!privateActor(actor)){CC.boardCast(type,message);return;}
        for(Player player:audience(actor))CC.send(type,player,message);
    }
    public static void broadcast(UUID actor,String message){
        if(!privateActor(actor)){CC.boardCast(message);return;}
        for(Player player:audience(actor))player.sendMessage(CC.translate(message));
    }
}
