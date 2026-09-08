package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/** Public events cannot enroll, teleport or score private-room participants. */
public final class PublicPitParticipants {
    private PublicPitParticipants() {}
    public static boolean privateRoom(Entity entity) {
        ThePit plugin=ThePit.getInstance();JunxuManager manager=plugin==null?null:plugin.getJunxuManager();
        return entity!=null&&manager!=null&&manager.isWorld(entity.getWorld());
    }
    public static List<Player> online() {
        List<Player> result=new ArrayList<>();
        for(Player player:Bukkit.getOnlinePlayers())if(isHuman(player))result.add(player);
        return result;
    }
    public static boolean isHuman(Entity entity) {
        return entity instanceof Player && !privateRoom(entity)
                && !cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(entity.getUniqueId()).isBot();
    }
    public static boolean humanCombat(Entity attacker, Entity victim) {
        return isHuman(cn.charlotte.pit.util.combat.CombatContext.playerSource(attacker)) && isHuman(victim);
    }
}
