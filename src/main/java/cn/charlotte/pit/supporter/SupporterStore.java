package cn.charlotte.pit.supporter;

import cn.charlotte.pit.impl.AffordablePointsDebit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Points, entitlements and the receipt commit together in RemiaftValue's database. */
public final class SupporterStore {
    public interface Connections {
        Connection open() throws Exception;
        void release(Connection connection) throws Exception;
    }

    private final Connections connections;
    private final String scope;
    private volatile boolean initialized;

    public SupporterStore(Connections connections, String scope) {
        if (scope == null || scope.isEmpty() || scope.length() > 64) {
            throw new IllegalArgumentException("Invalid supporter database scope");
        }
        this.connections = connections;
        this.scope = scope;
    }

    public synchronized void initialize() throws Exception {
        if (initialized) return;
        Connection connection = connections.open();
        try {
            String engine = connection.getMetaData().getDatabaseProductName();
            boolean mysql = engine.equalsIgnoreCase("MySQL") || engine.equalsIgnoreCase("MariaDB");
            if (mysql) {
                try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                        "SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"
                                + " AND TABLE_NAME IN ('player_pointss','logs_points')")) {
                    int tables = 0;
                    while (result.next()) {
                        tables++;
                        if (!"InnoDB".equalsIgnoreCase(result.getString(2))) {
                            throw new SQLException("Supporter purchases require transactional points tables");
                        }
                    }
                    if (tables != 2) throw new SQLException("RemiaftValue points tables are unavailable");
                }
            }
            String suffix = mysql ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS pit_supporter_accounts ("
                        + "pit_scope VARCHAR(64) NOT NULL,player_uuid VARCHAR(36) NOT NULL,"
                        + "permanent_member BOOLEAN NOT NULL,admin_grant BOOLEAN NOT NULL,"
                        + "contract_tier INT NOT NULL,expires_at BIGINT NOT NULL,revision BIGINT NOT NULL,"
                        + "updated_at BIGINT NOT NULL,PRIMARY KEY(pit_scope,player_uuid))" + suffix);
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS pit_supporter_orders ("
                        + "order_id VARCHAR(36) PRIMARY KEY,pit_scope VARCHAR(64) NOT NULL,"
                        + "player_uuid VARCHAR(36) NOT NULL,player_name VARCHAR(64) NOT NULL,"
                        + "product VARCHAR(16) NOT NULL,order_action VARCHAR(32) NOT NULL,"
                        + "points INT NOT NULL,includes_permanent BOOLEAN NOT NULL,"
                        + "contract_tier INT NOT NULL,expires_at BIGINT NOT NULL,"
                        + "account_revision BIGINT NOT NULL,created_at BIGINT NOT NULL,"
                        + "INDEX pit_supporter_history(pit_scope,player_uuid,created_at))" + suffix);
            }
            initialized = true;
        } finally {
            connections.release(connection);
        }
    }

    public SupporterAccount load(UUID playerId, SupporterAccount legacy, long now) throws Exception {
        initialize();
        Connection connection = connections.open();
        try {
            ensureAccount(connection, playerId, legacy, now);
            return readAccount(connection, playerId, false);
        } finally {
            connections.release(connection);
        }
    }

    public Purchase purchase(UUID orderId, UUID playerId, String playerName, SupporterPricing pricing,
                             SupporterPricing.Quote accepted, boolean externalSupporter, long now) throws Exception {
        initialize();
        Connection connection = connections.open();
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean started = false;
        boolean resetAllowed = false;
        try {
            if (!originalAutoCommit) throw new SQLException("Purchase requires an independent connection");
            started = true;
            connection.setAutoCommit(false);
            SupporterAccount current = readAccount(connection, playerId, true);
            if (current == null) throw new IllegalArgumentException("赞助者资料尚未载入，请重新打开商店。");

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pit_scope,player_uuid,product,order_action,points,includes_permanent,created_at,expires_at"
                            + " FROM pit_supporter_orders WHERE order_id=?")) {
                statement.setString(1, orderId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        if (!scope.equals(result.getString(1)) || !playerId.toString().equals(result.getString(2))
                                || !accepted.getProduct().name().equals(result.getString(3))) {
                            throw new IllegalArgumentException("订单身份不匹配。");
                        }
                        Purchase duplicate = new Purchase(orderId, accepted.getProduct(), result.getString(4),
                                result.getInt(5), result.getBoolean(6), result.getLong(7), result.getLong(8), current, true);
                        connection.commit();
                        resetAllowed = true;
                        return duplicate;
                    }
                }
            }

            if (current.getRevision() != accepted.getExpectedRevision()) {
                throw new IllegalArgumentException("你的契约状态已变化，请重新打开商店确认。");
            }
            if (accepted.getProduct() != SupporterProduct.PERMANENT && !current.isPermanent()) {
                throw new IllegalArgumentException("请先购买天坑·会员，再开通契约。");
            }
            if (now < accepted.getCreatedAt() || now - accepted.getCreatedAt() > 300_000L) {
                throw new IllegalArgumentException("购买确认已过期，请重新打开商店。");
            }
            SupporterPricing.Quote actual = pricing.quote(current, accepted.getProduct(), externalSupporter, now);
            if (actual.getPoints() > accepted.getPoints() || !actual.getAction().equals(accepted.getAction())
                    || actual.isIncludesPermanent() != accepted.isIncludesPermanent()) {
                throw new IllegalArgumentException("价格或契约期限已变化，请重新确认。");
            }

            int oldBalance;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT points FROM player_pointss WHERE player_uuid=? FOR UPDATE")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalArgumentException("点券不足。");
                    oldBalance = result.getInt(1);
                }
            }
            if (!AffordablePointsDebit.debit(connection, playerId, actual.getPoints())) {
                throw new IllegalArgumentException("点券不足，本次没有扣款。");
            }
            writeAccount(connection, playerId, current, actual.getAfter(), now);
            insertOrder(connection, orderId, playerId, playerName, actual.getProduct(), actual.getAction(),
                    actual.getPoints(), actual.isIncludesPermanent(), actual.getAfter(), now);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO logs_points (timestamp,player_uuid,player_name,operation,old_value,new_value,message)"
                            + " VALUES (?,?,?,?,?,?,?)")) {
                statement.setLong(1, now);
                statement.setString(2, playerId.toString());
                statement.setString(3, playerName);
                statement.setString(4, "SUBTRACT");
                statement.setString(5, Integer.toString(oldBalance));
                statement.setString(6, Integer.toString(oldBalance - actual.getPoints()));
                statement.setString(7, "天坑" + actual.getAction() + " " + actual.getProduct().getDisplayName()
                        + (actual.isIncludesPermanent() ? "（含永久赞助者）" : "") + " order=" + orderId);
                statement.executeUpdate();
            }
            connection.commit();
            resetAllowed = true;
            return new Purchase(orderId, actual.getProduct(), actual.getAction(), actual.getPoints(),
                    actual.isIncludesPermanent(), now, actual.getAfter().getExpiresAt(), actual.getAfter(), false);
        } catch (Exception exception) {
            if (started) {
                try { connection.rollback(); resetAllowed = true; }
                catch (SQLException rollback) { exception.addSuppressed(rollback); }
            }
            throw exception;
        } finally {
            releaseTransaction(connection, started, resetAllowed, originalAutoCommit);
        }
    }

    public SupporterAccount setPermanent(UUID playerId, String playerName, boolean give, long now) throws Exception {
        initialize();
        Connection connection = connections.open();
        boolean started = false;
        boolean resetAllowed = false;
        try {
            if (!connection.getAutoCommit()) throw new SQLException("Grant requires an independent connection");
            started = true;
            connection.setAutoCommit(false);
            ensureAccount(connection, playerId, SupporterAccount.empty(), now);
            SupporterAccount before = readAccount(connection, playerId, true);
            SupporterAccount after = new SupporterAccount(give, give,
                    give ? before.getTier() : SupporterTier.NONE, give ? before.getExpiresAt() : 0L,
                    Math.addExact(before.getRevision(), 1L));
            writeAccount(connection, playerId, before, after, now);
            insertOrder(connection, UUID.randomUUID(), playerId, playerName, SupporterProduct.PERMANENT,
                    give ? "管理员授予" : "管理员撤销", 0, false, after, now);
            connection.commit();
            resetAllowed = true;
            return after;
        } catch (Exception exception) {
            if (started) {
                try { connection.rollback(); resetAllowed = true; }
                catch (SQLException rollback) { exception.addSuppressed(rollback); }
            }
            throw exception;
        } finally {
            releaseTransaction(connection, started, resetAllowed, true);
        }
    }

    private void releaseTransaction(Connection connection, boolean started, boolean resetAllowed,
                                    boolean originalAutoCommit) throws Exception {
        try {
            if (started) {
                if (resetAllowed) {
                    try { connection.setAutoCommit(originalAutoCommit); }
                    catch (SQLException exception) { discardConnection(connection); }
                } else {
                    // Setting auto-commit here could commit a partial debit after a failed rollback or JVM error.
                    discardConnection(connection);
                }
            }
        } finally {
            connections.release(connection);
        }
    }

    private static void discardConnection(Connection connection) {
        try { connection.abort(Runnable::run); } catch (Exception | LinkageError ignored) { }
        try { connection.close(); } catch (SQLException ignored) { }
    }

    public List<Purchase> history(UUID playerId) throws Exception {
        initialize();
        Connection connection = connections.open();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id,product,order_action,points,includes_permanent,created_at,expires_at"
                        + " FROM pit_supporter_orders WHERE pit_scope=? AND player_uuid=? ORDER BY created_at DESC LIMIT 20")) {
            statement.setString(1, scope);
            statement.setString(2, playerId.toString());
            List<Purchase> history = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    history.add(new Purchase(UUID.fromString(result.getString(1)), SupporterProduct.valueOf(result.getString(2)),
                            result.getString(3), result.getInt(4), result.getBoolean(5), result.getLong(6),
                            result.getLong(7), null, false));
                }
            }
            return history;
        } finally {
            connections.release(connection);
        }
    }

    private void ensureAccount(Connection connection, UUID playerId, SupporterAccount legacy, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO pit_supporter_accounts"
                        + " (pit_scope,player_uuid,permanent_member,admin_grant,contract_tier,expires_at,revision,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)")) {
            statement.setString(1, scope);
            statement.setString(2, playerId.toString());
            statement.setBoolean(3, legacy.isPermanent());
            statement.setBoolean(4, legacy.isGivenByAdmin());
            statement.setInt(5, legacy.getTier().getId());
            statement.setLong(6, legacy.getExpiresAt());
            statement.setLong(7, 0L);
            statement.setLong(8, now);
            statement.executeUpdate();
        }
    }

    private SupporterAccount readAccount(Connection connection, UUID playerId, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT permanent_member,admin_grant,contract_tier,expires_at,revision FROM pit_supporter_accounts"
                        + " WHERE pit_scope=? AND player_uuid=?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setString(1, scope);
            statement.setString(2, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new SupporterAccount(result.getBoolean(1), result.getBoolean(2),
                        SupporterTier.fromId(result.getInt(3)), result.getLong(4), result.getLong(5)) : null;
            }
        }
    }

    private void writeAccount(Connection connection, UUID playerId, SupporterAccount before,
                              SupporterAccount after, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pit_supporter_accounts SET permanent_member=?,admin_grant=?,contract_tier=?,expires_at=?,"
                        + "revision=?,updated_at=? WHERE pit_scope=? AND player_uuid=? AND revision=?")) {
            statement.setBoolean(1, after.isPermanent());
            statement.setBoolean(2, after.isGivenByAdmin());
            statement.setInt(3, after.getTier().getId());
            statement.setLong(4, after.getExpiresAt());
            statement.setLong(5, after.getRevision());
            statement.setLong(6, now);
            statement.setString(7, scope);
            statement.setString(8, playerId.toString());
            statement.setLong(9, before.getRevision());
            if (statement.executeUpdate() != 1) throw new SQLException("Supporter account changed concurrently");
        }
    }

    private void insertOrder(Connection connection, UUID orderId, UUID playerId, String playerName,
                             SupporterProduct product, String action, int points, boolean includesPermanent,
                             SupporterAccount account, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pit_supporter_orders (order_id,pit_scope,player_uuid,player_name,product,order_action,points,"
                        + "includes_permanent,contract_tier,expires_at,account_revision,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, orderId.toString());
            statement.setString(2, scope);
            statement.setString(3, playerId.toString());
            statement.setString(4, playerName);
            statement.setString(5, product.name());
            statement.setString(6, action);
            statement.setInt(7, points);
            statement.setBoolean(8, includesPermanent);
            statement.setInt(9, account.getTier().getId());
            statement.setLong(10, account.getExpiresAt());
            statement.setLong(11, account.getRevision());
            statement.setLong(12, now);
            statement.executeUpdate();
        }
    }

    public static final class Purchase {
        private final UUID orderId;
        private final SupporterProduct product;
        private final String action;
        private final int points;
        private final boolean includesPermanent;
        private final long createdAt;
        private final long expiresAt;
        private final SupporterAccount account;
        private final boolean duplicate;

        Purchase(UUID orderId, SupporterProduct product, String action, int points, boolean includesPermanent,
                 long createdAt, long expiresAt, SupporterAccount account, boolean duplicate) {
            this.orderId = orderId;
            this.product = product;
            this.action = action;
            this.points = points;
            this.includesPermanent = includesPermanent;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.account = account;
            this.duplicate = duplicate;
        }

        public UUID getOrderId() { return orderId; }
        public SupporterProduct getProduct() { return product; }
        public String getAction() { return action; }
        public int getPoints() { return points; }
        public boolean isIncludesPermanent() { return includesPermanent; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public SupporterAccount getAccount() { return account; }
        public boolean isDuplicate() { return duplicate; }
    }
}
