package cn.charlotte.pit.util;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.type.normal.SwiftSprintEnchant;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cancels only Spiter flags caused by explicit ThePit movement mechanics.
 * Reflection keeps ThePit compatible when Spiter is not installed.
 */
public final class SpiterCompatibility implements Listener {
    private static final ConcurrentHashMap<UUID, Long> MOVEMENT_EXEMPT_UNTIL = new ConcurrentHashMap<>();
    private static final Set<String> AUTHORIZED_MOVEMENT_CHECKS = new HashSet<>(Arrays.asList(
            "move.jump", "move.vert", "move.hor", "move.notdown", "move.teleport",
            "move.fly", "move.speed", "move.large", "move.spider"
    ));

    private static boolean initialized;

    private final Method getPlayer;
    private final Method getCheck;
    private final Method getType;
    private final Method setCancelled;

    private SpiterCompatibility(Class<?> eventClass) throws ReflectiveOperationException {
        getPlayer = eventClass.getMethod("getPlayer");
        getCheck = eventClass.getMethod("getCheck");
        getType = eventClass.getMethod("getType");
        setCancelled = eventClass.getMethod("setCancelled", boolean.class);
    }

    @SuppressWarnings("unchecked")
    public static void initialize(ThePit plugin) {
        BotCombatExemptions.initialize(plugin);
        if (initialized || !Bukkit.getPluginManager().isPluginEnabled("SpiterLoader")) {
            return;
        }

        try {
            Class<?> rawEventClass = Class.forName("ac.spiter.loader.api.SpiterFlagEvent");
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                throw new IllegalStateException("SpiterFlagEvent is not a Bukkit event");
            }

            SpiterCompatibility listener = new SpiterCompatibility(rawEventClass);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            EventExecutor executor = (ignored, event) -> listener.handleFlag(event);
            Bukkit.getPluginManager().registerEvent(
                    (Class<? extends Event>) rawEventClass,
                    listener,
                    EventPriority.LOWEST,
                    executor,
                    plugin,
                    true
            );
            initialized = true;
            plugin.getLogger().info("Spiter 天坑机制误报兼容已启用。");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Spiter 误报兼容未启用: " + exception.getMessage());
        }
    }

    public static void exemptVelocity(Player player, long ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + ticks * 50L;
        MOVEMENT_EXEMPT_UNTIL.merge(player.getUniqueId(), expiresAt, Math::max);
    }

    private void handleFlag(Event event) {
        try {
            Player player = (Player) getPlayer.invoke(event);
            String check = normalize(getCheck.invoke(event));
            String type = normalize(getType.invoke(event));
            if (isAuthorizedFlag(player, check) || isAuthorizedFlag(player, type)) {
                setCancelled.invoke(event, true);
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Fail closed: a changed Spiter API must never create a broad bypass.
        }
    }

    private boolean isAuthorizedFlag(Player player, String component) {
        if (player == null || component.isEmpty()) {
            return false;
        }
        if (BotCombatExemptions.isExempt(player.getUniqueId(), component, System.currentTimeMillis())) {
            return true;
        }
        if ("move.walk".equals(component) && SwiftSprintEnchant.isSwiftSprintSpeed(player.getWalkSpeed())) {
            return true;
        }

        Long expiresAt = MOVEMENT_EXEMPT_UNTIL.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            MOVEMENT_EXEMPT_UNTIL.remove(player.getUniqueId(), expiresAt);
            return false;
        }
        return component.startsWith("vel.") || AUTHORIZED_MOVEMENT_CHECKS.contains(component);
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            exemptVelocity(event.getPlayer(), 30L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MOVEMENT_EXEMPT_UNTIL.remove(event.getPlayer().getUniqueId());
    }
}
