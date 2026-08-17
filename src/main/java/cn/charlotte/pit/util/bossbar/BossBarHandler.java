package cn.charlotte.pit.util.bossbar;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/11 23:50
 */
public class BossBarHandler implements Listener {
    private final BossBar bossBar;

    public BossBarHandler() {
        this.bossBar = new BossBar("");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (bossBar.getTitle().isEmpty()) {
                    if (bossBar.getWithers().isEmpty()) {
                        return;
                    }
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        bossBar.removePlayer(player);
                    }
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!bossBar.getWithers().containsKey(player.getUniqueId())) {
                        bossBar.addPlayer(player);
                    }
                }
                bossBar.update();
            }
        }.runTaskTimer(ThePit.getInstance(), 5, 5);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!this.bossBar.getTitle().isEmpty()) {
            this.bossBar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.bossBar.removePlayer(event.getPlayer());
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }
}
