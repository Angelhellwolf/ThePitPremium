package cn.charlotte.pit.command;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.bossbar.BossBar;
import cn.charlotte.pit.util.bossbar.BossBarRuntimeCheck;
import cn.charlotte.pit.util.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BossBarCommand {
    @Command(names = "pitadmin bossbarstatus", permissionNode = "pit.admin")
    public void status(CommandSender sender) {
        if (sender instanceof Player) { sender.sendMessage("请从服务端控制台执行。"); return; }
        BossBar bar = ThePit.getInstance().getBossBar().getBossBar();
        sender.sendMessage("BOSSBAR_STATUS revision=live-sync-20260907 legacy=" + bar.getWithers().size()
                + " modern=" + bar.getModernPlayerCount() + " pending=" + bar.getPendingPlayerCount()
                + " progress=" + bar.getProgress() + " title=" + bar.getTitle());
        cn.charlotte.pit.events.EventFactory events = ThePit.getInstance().getEventFactory();
        sender.sendMessage("BOSSBAR_EVENTS normal=" + events.getActiveNormalEventName()
                + " epic=" + events.getActiveEpicEventName() + " preparing=" + (events.getNextEpicEvent() != null));
    }

    @Command(names = "pitadmin bossbarcheck", permissionNode = "pit.admin")
    public void check(CommandSender sender) {
        if (sender instanceof Player) { sender.sendMessage("请从服务端控制台执行。"); return; }
        try {
            for (String line : BossBarRuntimeCheck.run(ThePit.getInstance())) sender.sendMessage(line);
        } catch (Exception | LinkageError failure) {
            sender.sendMessage("BOSSBAR_SMOKE FAILED " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }
}
