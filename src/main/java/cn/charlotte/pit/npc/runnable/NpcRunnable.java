package cn.charlotte.pit.npc.runnable;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.npc.AbstractPitNPC;
import cn.charlotte.pit.npc.NpcFactory;
import cn.charlotte.pit.util.perf.PerfMetrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * @author EmptyIrony
 * @date 2021/1/1 21:09
 */
public class NpcRunnable extends BukkitRunnable {

    @Override
    public void run() {
        final long startNs = System.nanoTime();
        final boolean optimize = NewConfiguration.INSTANCE.getPerfNpcOptimizeEnable();
        final double maxDistanceSquared = Math.pow(NewConfiguration.INSTANCE.getPerfNpcTextUpdateMaxDistance(), 2.0D);
        int scanned = 0;
        int updated = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            final Location playerLocation = player.getLocation();
            for (AbstractPitNPC pitNpc : NpcFactory.getPitNpc()) {
                scanned++;
                if (optimize) {
                    if (pitNpc.getNpc() == null || pitNpc.getNpc().getLocation() == null) {
                        continue;
                    }
                    final Location npcLocation = pitNpc.getNpc().getLocation();
                    if (npcLocation.getWorld() == null || !npcLocation.getWorld().equals(playerLocation.getWorld())) {
                        continue;
                    }
                    if (playerLocation.distanceSquared(npcLocation) > maxDistanceSquared) {
                        continue;
                    }
                }
                pitNpc.getNpc().setText(player, pitNpc.getNpcTextLine(player));
                updated++;
            }
        }

        PerfMetrics.addCount("npc.textUpdate.scanned", scanned);
        PerfMetrics.addCount("npc.textUpdate.updated", updated);
        PerfMetrics.recordTime("npc.textUpdate.tick", System.nanoTime() - startNs);
    }

}
