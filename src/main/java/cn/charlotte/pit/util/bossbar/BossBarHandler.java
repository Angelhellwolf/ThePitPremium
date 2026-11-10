package cn.charlotte.pit.util.bossbar;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.server.PluginDisableEvent;
import cn.charlotte.pit.command.BossBarCommand;
import cn.charlotte.pit.util.command.CommandHandler;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/11 23:50
 */
public class BossBarHandler implements Listener {
    private final BossBar bossBar;
    private final BukkitTask task;

    public BossBarHandler() {
        this.bossBar = new BossBar("");
        CommandHandler.registerClass(BossBarCommand.class);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (bossBar.isBlankTitle()) {
                    if (!bossBar.hasPlayers()) {
                        return;
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        bossBar.removePlayer(player);
                    }
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasMetadata("NPC")) continue;
                    if (!bossBar.containsPlayer(player.getUniqueId())) {
                        bossBar.addPlayer(player);
                    }
                }
                bossBar.update();
            }
        }.runTaskTimer(ThePit.getInstance(), 2, 2);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!this.bossBar.isBlankTitle()) {
            this.bossBar.rebuild(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.bossBar.removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null) bossBar.update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { bossBar.rebuild(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { bossBar.rebuild(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { bossBar.rebuild(event.getPlayer()); }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == ThePit.getInstance()) shutdown();
    }

    public void shutdown() {
        task.cancel();
        bossBar.close();
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }
}
