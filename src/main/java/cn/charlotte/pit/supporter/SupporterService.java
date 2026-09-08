package cn.charlotte.pit.supporter;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.impl.RemiaftValuePointsAPIImpl;
import cn.charlotte.pit.medal.impl.challenge.hidden.SupporterMedal;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SupporterService implements Listener {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static volatile SupporterService instance;
    private final ThePit plugin;
    private final SupporterStore store;
    private final ScheduledExecutorService io;
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> refreshing = ConcurrentHashMap.newKeySet();
    private volatile boolean ready;
    private volatile boolean closing;

    private SupporterService(ThePit plugin) {
        this.plugin = plugin;
        this.io = Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "pit-supporter-database");
            thread.setDaemon(true);
            return thread;
        });
        this.store = new SupporterStore(new ValueConnections(), plugin.getPitConfig().getDatabaseName());
    }

    public static SupporterService get() { return instance; }

    public static void start(ThePit plugin) {
        SupporterService service = new SupporterService(plugin);
        instance = service;
        Bukkit.getPluginManager().registerEvents(service, plugin);
        service.io.execute(service::initialize);
    }

    private void initialize() {
        if (closing) return;
        try {
            store.initialize();
            ready = true;
            plugin.getLogger().info("SUPPORTER_COMMERCE_READY revision=contracts-20260907 pointsPerYuan="
                    + SupporterPricing.POINTS_PER_YUAN + " permanent=" + pricing().price(SupporterProduct.PERMANENT)
                    + " mythic=" + pricing().price(SupporterProduct.MYTHIC) + " sacred=" + pricing().price(SupporterProduct.SACRED));
            onMain(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
            });
        } catch (Exception exception) {
            plugin.getLogger().warning("天坑赞助数据库暂不可用，30 秒后重试: " + exception.getClass().getSimpleName());
            if (!closing) io.schedule(this::initialize, 30L, TimeUnit.SECONDS);
        }
    }

    public boolean isReady() {
        return ready && !closing && plugin.getPointsAPI() == RemiaftValuePointsAPIImpl.INSTANCE;
    }

    public SupporterPricing pricing() { return NewConfiguration.INSTANCE.supporterPricing(); }

    /** Called during the existing asynchronous player load, before the join event is published. */
    public void loadProfile(PlayerProfile profile) {
        if (closing) return;
        try {
            profile.applySupporterAccount(store.load(profile.getPlayerUuid(), legacy(profile), System.currentTimeMillis()));
        } catch (Exception exception) {
            plugin.getLogger().warning("赞助者资料读取失败，保留已有本地权益: " + exception.getClass().getSimpleName());
        }
    }

    private static SupporterAccount legacy(PlayerProfile profile) {
        return SupporterAccount.fromLegacy(profile.getSupporter(), profile.isSupporterGivenByAdmin(),
                profile.getSupporterExpiresAt(), profile.getSupporterContractTier(),
                profile.getSupporterContractExpiresAt(), System.currentTimeMillis());
    }

    public void refresh(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (closing || profile == PlayerProfile.NONE_PROFILE || !profile.isLoaded()
                || !refreshing.add(player.getUniqueId())) return;
        SupporterAccount seed = legacy(profile);
        UUID playerId = player.getUniqueId();
        io.execute(() -> {
            try {
                SupporterAccount account = store.load(playerId, seed, System.currentTimeMillis());
                onMain(() -> apply(playerId, account));
            } catch (Exception exception) {
                plugin.getLogger().warning("赞助者资料刷新失败: " + exception.getClass().getSimpleName());
            } finally {
                refreshing.remove(playerId);
            }
        });
    }

    @EventHandler
    public void onProfileLoaded(PitProfileLoadedEvent event) {
        PlayerProfile profile = event.getPlayerProfile();
        Player player = Bukkit.getPlayer(profile.getPlayerUuid());
        if (player == null) return;
        if (profile.getSupporterAccount() == null) refresh(player);
        else markMedal(profile);
    }

    public SupporterPricing.Quote quote(Player player, SupporterProduct product, SupporterPricing pricing) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!isReady() || profile == PlayerProfile.NONE_PROFILE || !profile.isLoaded()
                || profile.getSupporterAccount() == null) {
            refresh(player);
            throw new IllegalArgumentException("赞助者资料载入中，请稍后重试。");
        }
        return pricing.quote(profile.getSupporterAccount(), product,
                player.hasPermission(NewConfiguration.INSTANCE.getPitSupportPermission()), System.currentTimeMillis());
    }

    public void purchase(Player player, SupporterPricing pricing, SupporterPricing.Quote accepted, UUID orderId) {
        if (!isReady()) {
            player.sendMessage(CC.translate("&c点券服务暂不可用，请稍后重试。"));
            return;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        if (!purchasing.add(playerId)) {
            player.sendMessage(CC.translate("&e你的订单正在处理，请勿重复提交。"));
            return;
        }
        boolean external = player.hasPermission(NewConfiguration.INSTANCE.getPitSupportPermission());
        player.sendMessage(CC.translate("&e正在处理订单，预计扣除 &d" + accepted.getPoints() + " 点券&e。"));
        io.execute(() -> {
            try {
                SupporterStore.Purchase result = null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        result = store.purchase(orderId, playerId, playerName, pricing, accepted, external,
                                System.currentTimeMillis());
                        break;
                    } catch (SQLException exception) {
                        // Reuse the same receipt id after an uncertain commit; never debit twice.
                        if (attempt == 2) throw exception;
                    }
                }
                SupporterStore.Purchase completed = result;
                if (completed == null) throw new SQLException("Missing purchase result");
                RemiaftValuePointsAPIImpl.INSTANCE.synchronizeCommittedBalance(player);
                onMain(() -> {
                    apply(playerId, completed.getAccount());
                    if (Bukkit.getPlayer(playerId) != player || !player.isOnline()) return;
                    player.sendMessage(CC.translate("&a" + completed.getProduct().getDisplayName()
                            + completed.getAction() + "成功！ &7扣除 &d" + completed.getPoints() + " 点券&7。"));
                    if (completed.isIncludesPermanent()) player.sendMessage(CC.translate("&e已同时获得永久天坑·会员资格。"));
                    if (completed.getProduct() != SupporterProduct.PERMANENT) {
                        player.sendMessage(CC.translate("&7到期时间：&f" + formatExpiry(completed.getExpiresAt())));
                    }
                    player.sendMessage(CC.translate("&7订单号：&f" + completed.getOrderId()));
                });
            } catch (IllegalArgumentException exception) {
                onMain(() -> {
                    if (Bukkit.getPlayer(playerId) == player) player.sendMessage(CC.translate("&c" + exception.getMessage()));
                    refresh(player);
                });
            } catch (Exception exception) {
                plugin.getLogger().severe("赞助订单待核对 order=" + orderId + " error=" + exception.getClass().getSimpleName());
                onMain(() -> {
                    if (Bukkit.getPlayer(playerId) == player) {
                        player.sendMessage(CC.translate("&e暂未确认订单结果，请重新打开商店查看权益和订单记录。"));
                        player.sendMessage(CC.translate("&7订单号：&f" + orderId));
                    }
                    refresh(player);
                });
            } finally {
                purchasing.remove(playerId);
            }
        });
    }

    public void showHistory(Player player) {
        if (!isReady()) {
            player.sendMessage(CC.translate("&c订单服务暂不可用。"));
            return;
        }
        UUID playerId = player.getUniqueId();
        player.closeInventory();
        io.execute(() -> {
            try {
                List<SupporterStore.Purchase> history = store.history(playerId);
                onMain(() -> {
                    if (Bukkit.getPlayer(playerId) != player) return;
                    player.sendMessage(CC.translate("&6天坑赞助订单 &7（最近 " + history.size() + " 条）"));
                    if (history.isEmpty()) player.sendMessage(CC.translate("&7暂无订单。"));
                    for (SupporterStore.Purchase order : history) {
                        player.sendMessage(CC.translate("&7" + formatExpiry(order.getCreatedAt()) + " &f"
                                + order.getProduct().getDisplayName() + " " + order.getAction() + " &d"
                                + order.getPoints() + " 点券"));
                        player.sendMessage(CC.translate("&8订单 " + order.getOrderId()));
                    }
                });
            } catch (Exception exception) {
                onMain(() -> {
                    if (Bukkit.getPlayer(playerId) == player) player.sendMessage(CC.translate("&c订单读取失败，请稍后重试。"));
                });
            }
        });
    }

    public void setPermanent(CommandSender sender, Player player, boolean give) {
        if (!isReady()) { sender.sendMessage("赞助者数据库尚未就绪。"); return; }
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        io.execute(() -> {
            try {
                SupporterAccount account = store.setPermanent(playerId, name, give, System.currentTimeMillis());
                onMain(() -> {
                    apply(playerId, account);
                    sender.sendMessage((give ? "已授予 " : "已撤销 ") + name + " 的天坑·会员资格。");
                });
            } catch (Exception exception) {
                onMain(() -> sender.sendMessage("赞助者操作失败，请检查数据库连接。"));
            }
        });
    }

    private static void apply(UUID playerId, SupporterAccount account) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(playerId);
        if (profile == PlayerProfile.NONE_PROFILE) return;
        profile.applySupporterAccount(account);
        markMedal(profile);
    }

    private static void markMedal(PlayerProfile profile) {
        SupporterAccount account = profile.getSupporterAccount();
        if (profile.getMedalData() != null && account != null && account.isPermanent() && !account.isGivenByAdmin()) {
            new SupporterMedal().setProgress(profile, 1);
        }
    }

    public static String formatExpiry(long time) {
        return DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }

    private void onMain(Runnable action) {
        if (closing || !plugin.isEnabled()) return;
        try { Bukkit.getScheduler().runTask(plugin, action); }
        catch (IllegalStateException ignored) { /* A committed account is restored on the next login. */ }
    }

    public void shutdown() {
        closing = true;
        ready = false;
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException exception) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class ValueConnections implements SupporterStore.Connections {
        private final Map<Connection, Object> owners = Collections.synchronizedMap(new IdentityHashMap<>());

        @Override
        public Connection open() throws Exception {
            Plugin value = Bukkit.getPluginManager().getPlugin("RemiaftValue");
            if (value == null || !value.isEnabled()) throw new SQLException("RemiaftValue unavailable");
            Class<?> api = Class.forName("com.remiaft.remiaftvalue.api.RemiaftValueAPI", true, value.getClass().getClassLoader());
            Object manager = api.getMethod("getDatabaseManager").invoke(null);
            Connection connection = (Connection) manager.getClass().getMethod("getConnection").invoke(manager);
            owners.put(connection, manager);
            return connection;
        }

        @Override
        public void release(Connection connection) throws Exception {
            Object manager = owners.remove(connection);
            if (manager == null) { connection.close(); return; }
            try { manager.getClass().getMethod("releaseConnection", Connection.class).invoke(manager, connection); }
            catch (Exception exception) { connection.close(); }
        }
    }
}
