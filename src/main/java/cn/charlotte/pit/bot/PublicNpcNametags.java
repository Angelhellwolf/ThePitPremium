package cn.charlotte.pit.bot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.util.Map;
import java.util.UUID;

/** Sends the three public actors through the same renderer and conflict protection as human players. */
final class PublicNpcNametags {
    private Object manager;
    private Field viewers;
    private Constructor<?> snapshot;
    private Method sync,remove;
    private Field running;
    private long nextResolve;
    String status(){return manager==null?"legacy-fallback":"ServerManager";}
    boolean publish(Player viewer,UUID id,String name,int slot,String prefix,String suffix){
        if(!resolve())return false;
        try{
            if(!running.getBoolean(manager)){manager=null;return false;}
            Object state=((Map<?,?>)viewers.get(manager)).get(viewer.getUniqueId());
            if(state==null)return false;
            Object value=snapshot.newInstance(id,name,prefix,suffix,"",prefix,suffix,true,true,0,200-slot,"sm_pit_"+slot);
            sync.invoke(manager,state,viewer,id,value);
            org.bukkit.scoreboard.Team legacy=viewer.getScoreboard().getTeam("ptp_"+slot);
            if(legacy!=null)legacy.unregister();
            return true;
        }catch(ReflectiveOperationException|RuntimeException error){manager=null;nextResolve=System.currentTimeMillis()+5000;return false;}
    }
    void remove(UUID id){if(!resolve())return;try{remove.invoke(manager,id);}catch(ReflectiveOperationException ignored){}}
    private boolean resolve(){
        if(manager!=null)return true;
        long now=System.currentTimeMillis();if(now<nextResolve)return false;nextResolve=now+5000;
        Plugin plugin=Bukkit.getPluginManager().getPlugin("ServerManager");
        if(plugin==null||!plugin.isEnabled())return false;
        try{
            Field field=plugin.getClass().getDeclaredField("nametagManager");field.setAccessible(true);Object candidate=field.get(plugin);
            if(candidate==null)return false;
            Class<?> type=candidate.getClass();ClassLoader loader=type.getClassLoader();
            Class<?> view=loader.loadClass(type.getName()+"$ViewerState"),snap=loader.loadClass(type.getName()+"$NametagSnapshot");
            viewers=type.getDeclaredField("viewerStates");viewers.setAccessible(true);
            running=type.getDeclaredField("running");running.setAccessible(true);
            snapshot=snap.getDeclaredConstructor(UUID.class,String.class,String.class,String.class,String.class,String.class,String.class,
                    boolean.class,boolean.class,int.class,int.class,String.class);snapshot.setAccessible(true);
            sync=type.getDeclaredMethod("syncTargetToViewer",view,Player.class,UUID.class,snap);sync.setAccessible(true);
            remove=type.getDeclaredMethod("removeTarget",UUID.class);remove.setAccessible(true);
            manager=candidate;return true;
        }catch(ReflectiveOperationException|RuntimeException error){return false;}
    }
    static String[] legacy(String prefix,String suffix){
        String first=cut(prefix);return new String[]{first,cut(ChatColor.getLastColors(first)+(suffix==null?"":suffix))};
    }
    private static String cut(String value){
        if(value==null)return "";if(value.length()<=16)return value;
        return value.substring(0,value.charAt(15)=='§'?15:16);
    }
}
