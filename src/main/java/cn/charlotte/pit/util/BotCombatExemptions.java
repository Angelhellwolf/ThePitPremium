package cn.charlotte.pit.util;

import cn.charlotte.pit.ThePit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Only bot-sensitive statistical checks get a short, target-scoped exemption. */
public final class BotCombatExemptions implements Listener {
    private static final long WINDOW_MILLIS = 1500L;
    private static final ConcurrentHashMap<UUID, Long> LAST_BOT_ATTACK = new ConcurrentHashMap<>();
    private static boolean registered;

    public static void initialize(ThePit plugin) {
        if (registered) return;
        plugin.getServer().getPluginManager().registerEvents(new BotCombatExemptions(), plugin);
        registered = true;
    }

    public static void recordTarget(UUID attacker, boolean registeredBot, long now) {
        if (registeredBot) LAST_BOT_ATTACK.put(attacker, now);
        else LAST_BOT_ATTACK.remove(attacker);
    }

    public static boolean isExempt(UUID attacker, String check, long now) {
        if (!("abt.analysis.3".equals(check) || "combat.clk.m".equals(check))) return false;
        Long last = LAST_BOT_ATTACK.get(attacker);
        if (last == null) return false;
        if (now < last || now - last > WINDOW_MILLIS) {
            LAST_BOT_ATTACK.remove(attacker, last);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        ThePit plugin = ThePit.getInstance();
        boolean bot = plugin.getPitBotManager() != null && plugin.getPitBotManager().isBot(event.getEntity());
        recordTarget(attacker.getUniqueId(), bot, System.currentTimeMillis());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        LAST_BOT_ATTACK.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        LAST_BOT_ATTACK.remove(event.getPlayer().getUniqueId());
    }
}
