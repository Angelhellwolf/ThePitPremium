package cn.charlotte.pit.util.scoreboard;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.util.perf.PerfMetrics;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class AssembleThread implements Runnable {

    private final Assemble assemble;

    protected int taskId;
    private int frame = 0;

    /**
     * Assemble Thread.
     *
     * @param assemble instance.
     */
    AssembleThread(Assemble assemble) {
        this.assemble = assemble;
        Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        boolean protocolLib5 = protocolLib != null
                && protocolLib.getDescription() != null
                && protocolLib.getDescription().getVersion() != null
                && protocolLib.getDescription().getVersion().startsWith("5");

        if (protocolLib5) {
            taskId = Bukkit.getScheduler()
                    .runTaskTimer(ThePit.getInstance(), this, assemble.getTicks(), assemble.getTicks())
                    .getTaskId();
        } else {
            taskId = Bukkit.getScheduler()
                    .runTaskTimerAsynchronously(ThePit.getInstance(), this, assemble.getTicks(), assemble.getTicks())
                    .getTaskId();
        }
    }

    @Override
    @SneakyThrows
    public void run() {
        tick();
    }

    /**
     * Tick logic for thread.
     */
    private void tick() {
        final long startNs = System.nanoTime();
        final int onlineCount = this.assemble.getPlugin().getServer().getOnlinePlayers().size();
        if (onlineCount <= 0) {
            return;
        }

        final boolean spreadEnabled = NewConfiguration.INSTANCE.getPerfScoreboardSpreadEnable();
        final int maxPerRunCfg = NewConfiguration.INSTANCE.getPerfScoreboardMaxUpdatesPerRun();

        // Spread scoreboard updates across multiple runs to avoid huge spikes when many players are online.
        // Rollback switch: perf.scoreboard.spread-updates.enable=false
        final int maxUpdates = spreadEnabled ? Math.min(maxPerRunCfg, onlineCount) : onlineCount;
        final int stride = spreadEnabled ? Math.max(1, (onlineCount + maxUpdates - 1) / maxUpdates) : 1;
        if (frame >= stride) {
            frame = 0;
        }

        int index = 0;
        int processed = 0;
        for (Player player : this.assemble.getPlugin().getServer().getOnlinePlayers()) {
            if ((index++ % stride) != frame) {
                continue;
            }
            if (++processed > maxUpdates) {
                break;
            }

            try {
                AssembleBoard board = this.assemble.getBoards().get(player.getUniqueId());

                // This shouldn't happen, but just in case.
                if (board == null) {
                    continue;
                }

                Scoreboard scoreboard = board.getScoreboard();
                Objective objective = board.getObjective();

                if (scoreboard == null || objective == null) {
                    continue;
                }

                // Just make a variable so we don't have to
                // process the same thing twice.
                String title = translateIfNeeded(this.assemble.getAdapter().getTitle(player));

                // Update the title if needed.
                if (!objective.getDisplayName().equals(title)) {
                    objective.setDisplayName(title);
                }

                List<String> newLines = this.assemble.getAdapter().getLines(player);

                // Allow adapter to return null/empty list to display nothing.
                if (newLines == null || newLines.isEmpty()) {
                    board.getEntries().forEach(AssembleBoardEntry::remove);
                    board.getEntries().clear();
                } else {
                    final int lineCount = Math.min(15, newLines.size());
                    if (lineCount <= 0) {
                        board.getEntries().forEach(AssembleBoardEntry::remove);
                        board.getEntries().clear();
                    } else {
                        final boolean descending = this.assemble.getAssembleStyle().isDescending();

                        // Remove excessive amount of board entries.
                        while (board.getEntries().size() > lineCount) {
                            AssembleBoardEntry entry = board.getEntries().remove(board.getEntries().size() - 1);
                            entry.remove();
                        }

                        // Update existing entries / add new entries.
                        int cache = this.assemble.getAssembleStyle().getStartNumber();
                        for (int i = 0; i < lineCount; i++) {
                            AssembleBoardEntry entry = board.getEntryAtPosition(i);

                            // Avoid allocating/reversing lists; map source index on the fly.
                            int sourceIndex = descending ? i : (lineCount - 1 - i);
                            String line = translateIfNeeded(newLines.get(sourceIndex));

                            // If the entry is null, just create a new one.
                            // Creating a new AssembleBoardEntry instance will add
                            // itself to the provided board's entries list.
                            if (entry == null) {
                                entry = new AssembleBoardEntry(board, line, i);
                            }

                            int score = descending ? cache-- : cache++;

                            // Skip updates if nothing changed.
                            if (line.equals(entry.getText()) && entry.getLastScore() == score) {
                                continue;
                            }

                            // Update text and the display values.
                            entry.setText(line);
                            entry.send(score);
                        }
                    }
                }

                if (player.getScoreboard() != scoreboard && !assemble.isHook()) {
                    player.setScoreboard(scoreboard);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new AssembleException("There was an error updating " + player.getName() + "'s scoreboard.");
            }

        }

        frame++;
        if (frame >= stride) {
            frame = 0;
        }

        PerfMetrics.addCount("scoreboard.assemble.players", processed);
        PerfMetrics.addCount("scoreboard.assemble.online", onlineCount);
        PerfMetrics.recordTime("scoreboard.assemble.tick", System.nanoTime() - startNs);
    }

    private static String translateIfNeeded(String text) {
        if (text == null) return "";
        return text.indexOf('&') >= 0 ? ChatColor.translateAlternateColorCodes('&', text) : text;
    }

}
