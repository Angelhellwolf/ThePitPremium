package cn.charlotte.pit.util.nametag;

import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Hands ThePit's dynamic level/event tags to ServerManager's packet renderer. */
final class ServerManagerNametagBridge implements Listener {

    private static final String API_CLASS = "com.remiaft.api.PlayerTabPrefixProvider";
    private static final String[] SERVER_MANAGERS = {"ServerManager", "ServerManager3"};

    private final NametagHandler handler;
    private Object provider;
    private Class<?> providerType;
    private Object fallback;

    ServerManagerNametagBridge(NametagHandler handler) {
        this.handler = handler;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, handler.getPlugin());
        register();
    }

    void stop() {
        unregister();
        HandlerList.unregisterAll(this);
    }

    boolean isActive() {
        return provider != null;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (isServerManager(event.getPlugin())) {
            Bukkit.getScheduler().runTask(handler.getPlugin(), this::register);
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (isServerManager(event.getPlugin())) {
            unregister();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register() {
        if (provider != null) {
            return;
        }
        Plugin serverManager = findServerManager();
        if (serverManager == null || !serverManager.isEnabled()) {
            return;
        }
        try {
            Class<?> service = Class.forName(API_CLASS, true, serverManager.getClass().getClassLoader());
            service.getMethod("getNametagPrefix", UUID.class);
            service.getMethod("getNametagSuffix", UUID.class);
            service.getMethod("overridesLuckPermsNametag", UUID.class);
            service.getMethod("getTabSortOrder", UUID.class);
            service.getMethod("isNameTagVisible", UUID.class);
            org.bukkit.plugin.RegisteredServiceProvider<?> existing =
                    Bukkit.getServicesManager().getRegistration((Class) service);
            fallback = existing == null ? null : existing.getProvider();
            provider = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service}, new Provider());
            Bukkit.getServicesManager().register((Class) service, provider, handler.getPlugin(), ServicePriority.Highest);
            providerType = service;
            handler.getPlugin().getLogger().info("ThePit 称号已交由 ServerManager 高低版本双路径渲染。");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            handler.getPlugin().getLogger().warning("ServerManager 称号 API 不可用，继续使用原生 Scoreboard: "
                    + error.getMessage());
            provider = null;
            providerType = null;
            fallback = null;
        }
    }

    private Plugin findServerManager() {
        for (String name : SERVER_MANAGERS) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) return plugin;
        }
        return null;
    }

    private boolean isServerManager(Plugin plugin) {
        if (plugin == null) return false;
        for (String name : SERVER_MANAGERS) {
            if (name.equals(plugin.getName())) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void unregister() {
        if (provider != null && providerType != null) {
            Bukkit.getServicesManager().unregister((Class) providerType, provider);
        }
        provider = null;
        providerType = null;
        fallback = null;
    }

    private BufferedNametag find(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || handler.getAdapter() == null) {
            return null;
        }
        List<BufferedNametag> tags = handler.getAdapter().getPlate(player);
        if (tags == null) {
            tags = Collections.emptyList();
        }
        for (BufferedNametag tag : tags) {
            if (tag != null && tag.getPlayer() != null
                    && playerId.equals(tag.getPlayer().getUniqueId())) {
                return tag;
            }
        }
        return null;
    }

    private boolean isPitBot(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(playerId);
        return profile != null && profile != PlayerProfile.NONE_PROFILE
                && profile.isLoaded() && profile.isBot();
    }

    private Object callFallback(Method method, Object[] args) {
        if (fallback == null) {
            return defaultValue(method);
        }
        try {
            return method.invoke(fallback, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return defaultValue(method);
        }
    }

    private Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf("isNameTagVisible".equals(method.getName()));
        }
        if (type == String.class) {
            return "";
        }
        return null;
    }

    private final class Provider implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) return "ThePitServerManagerNametagProvider";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
            }
            UUID playerId = args != null && args.length == 1 && args[0] instanceof UUID
                    ? (UUID) args[0] : null;
            if ("isNameTagVisible".equals(name) && isPitBot(playerId)) {
                return Boolean.FALSE;
            }
            BufferedNametag tag = playerId == null ? null : find(playerId);
            if ("getNametagPrefix".equals(name) && tag != null) {
                return tag.getPrefix() == null ? "" : tag.getPrefix();
            }
            if ("getNametagSuffix".equals(name) && tag != null) {
                return tag.getSuffix() == null ? "" : tag.getSuffix();
            }
            if ("overridesLuckPermsNametag".equals(name) && tag != null) {
                return Boolean.TRUE;
            }
            if ("getTabSortOrder".equals(name) && tag != null) {
                try {
                    return Integer.valueOf(tag.getGroupName());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return callFallback(method, args);
        }
    }
}
