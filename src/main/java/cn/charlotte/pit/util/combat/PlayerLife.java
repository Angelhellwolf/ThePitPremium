package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import cn.charlotte.pit.parm.AutoRegister;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A delayed combat effect cannot cross a death, respawn, or reconnect. Main-thread only. */
@AutoRegister
public final class PlayerLife implements Listener {
    private static final Map<UUID, Object> lives = new HashMap<>();

    public static Object capture(Player player) {
        return lives.computeIfAbsent(player.getUniqueId(), ignored -> new Object());
    }

    public static boolean isCurrent(Player player, Object life) {
        cn.charlotte.pit.ThePit plugin = cn.charlotte.pit.ThePit.getInstance();
        cn.charlotte.pit.bot.PitBotManager bots = plugin == null ? null : plugin.getPitBotManager();
        boolean present = bots != null && bots.isBot(player)
                ? bots.getPlayer(player.getUniqueId()) == player && player.isValid() : player.isOnline();
        return present && !player.isDead() && player.getHealth() > 0
                && lives.get(player.getUniqueId()) == life;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) { lives.remove(event.getEntity().getUniqueId()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { lives.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { lives.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(PitPlayerSpawnEvent event) { lives.remove(event.getPlayer().getUniqueId()); }
}
