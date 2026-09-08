package cn.charlotte.pit.util.nametag;

import cn.charlotte.pit.ThePit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class NametagThread implements Runnable {

    private final NametagHandler handler;
    private final int taskId;
    private int frame;

    /**
     * Nametag Thread.
     *
     * @param handler instance.
     */
    public NametagThread(NametagHandler handler) {
        this.handler = handler;
        this.taskId = Bukkit.getScheduler()
                .runTaskTimer(handler.getPlugin(), this, 1L, 1L)
                .getTaskId();
    }

    @Override
    public void run() {
        if (!ThePit.getInstance().isEnabled()) {
            stop();
            return;
        }
        tick();
    }

    public void stop() {
        Bukkit.getScheduler().cancelTask(taskId);
    }

    /**
     * Thread Tick Logic.
     */
    private void tick() {
        if (this.handler.getAdapter() == null) {
            return;
        }

        // Each viewer owns a separate scoreboard. Updating every viewer in one
        // one-second burst caused a visible packet/CPU spike, so process one frame
        // of viewers per server tick and complete a full pass in handler.ticks.
        final int interval = (int) Math.max(1L, this.handler.getTicks());
        if (frame >= interval) {
            frame = 0;
        }

        int index = 0;
        for (Player player : this.handler.getPlugin().getServer().getOnlinePlayers()) {
            if ((index++ % interval) != frame) {
                continue;
            }
            NametagBoard board = this.handler.getBoards().get(player.getUniqueId());

            // This shouldn't happen, but just in case.
            if (board != null) {
                board.update();
            }
        }
        frame++;
    }
}
