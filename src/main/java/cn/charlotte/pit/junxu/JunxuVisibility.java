package cn.charlotte.pit.junxu;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

/** Private NPC player-info packets must never populate the public lobby's Tab list. */
public final class JunxuVisibility {
    private final JunxuManager manager;
    private final PacketAdapter listener;
    public JunxuVisibility(JunxuManager manager) {
        this.manager=manager;
        listener=new PacketAdapter(manager.plugin(),ListenerPriority.HIGHEST,PacketType.Play.Server.PLAYER_INFO){
            @Override public void onPacketSending(PacketEvent event) {
                if(event.getPacket().getPlayerInfoAction().read(0)==EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)return;
                List<PlayerInfoData> data=event.getPacket().getPlayerInfoDataLists().read(0);
                if(data==null)return;
                List<PlayerInfoData> visible=new ArrayList<>();
                for(PlayerInfoData entry:data){
                    UUID id=entry.getProfile()==null?null:entry.getProfile().getUUID();
                    JunxuRoom room=id==null?null:manager.bots().room(id);
                    if(room==null||canView(event.getPlayer().getUniqueId(),event.getPlayer().getLocation(),room))visible.add(entry);
                }
                if(visible.size()==data.size())return;
                if(visible.isEmpty()){event.setCancelled(true);return;}
                com.comphenix.protocol.events.PacketContainer copy=event.getPacket().shallowClone();
                copy.getPlayerInfoDataLists().write(0,visible);event.setPacket(copy);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(listener);
    }
    public static boolean canView(UUID viewer,Location location,JunxuRoom room) {
        return room!=null&&room.permits(viewer)&&room.contains(location);
    }
    public void tick(){refresh(null);}
    public void refresh(Player changed){
        Collection<? extends Player> viewers=changed==null?Bukkit.getOnlinePlayers():Collections.singletonList(changed);
        for(Player viewer:viewers){
            if(viewer.hasMetadata("NPC"))continue;
            for(Player bot:manager.bots().activePlayers()){
                JunxuRoom room=manager.bots().room(bot);
                if(canView(viewer.getUniqueId(),viewer.getLocation(),room)){
                    if(changed!=null&&viewer.canSee(bot))viewer.hidePlayer(bot);
                    viewer.showPlayer(bot);
                }else viewer.hidePlayer(bot);
            }
        }
    }
    public void shutdown(){ProtocolLibrary.getProtocolManager().removePacketListener(listener);}
}
