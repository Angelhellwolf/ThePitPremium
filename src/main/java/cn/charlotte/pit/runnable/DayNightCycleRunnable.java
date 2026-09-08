package cn.charlotte.pit.runnable;

import cn.charlotte.pit.util.time.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class DayNightCycleRunnable extends BukkitRunnable {
    @Override
    public void run() {
        long minecraftTick = TimeUtil.getMinecraftTick();
        Bukkit.getWorlds().forEach(world -> {
            cn.charlotte.pit.junxu.JunxuManager manager = cn.charlotte.pit.ThePit.getInstance().getJunxuManager();
            if (manager == null || !manager.isWorld(world)) world.setTime(minecraftTick);
        });
    }
}
