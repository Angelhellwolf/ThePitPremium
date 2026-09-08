package cn.charlotte.pit.supporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class SupporterStoreTest {
    private String url;
    private SupporterStore store;
    private final long now = 1_800_000_000_000L;
    private final SupporterPricing pricing = new SupporterPricing(50, 38, 88, 30);

    @Before
    public void setUp() throws Exception {
        url = "jdbc:h2:mem:pit_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_pointss (player_uuid VARCHAR(36) PRIMARY KEY,points INT NOT NULL)");
            statement.execute("CREATE TABLE logs_points (timestamp BIGINT,player_uuid VARCHAR(36),player_name VARCHAR(64),"
                    + "operation VARCHAR(16),old_value VARCHAR(64),new_value VARCHAR(64),message VARCHAR(512))");
        }
        store = new SupporterStore(new SupporterStore.Connections() {
            public Connection open() throws Exception { return SupporterStoreTest.this.open(); }
            public void release(Connection connection) throws Exception { connection.close(); }
        }, "thepit");
        store.initialize();
    }

    @After
    public void tearDown() throws Exception {
        if (url != null) try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private Connection open() throws Exception { return DriverManager.getConnection(url, "sa", ""); }

    private UUID player(int points, SupporterAccount seed) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_pointss(player_uuid,points) VALUES (?,?)")) {
            statement.setString(1, id.toString());
            statement.setInt(2, points);
            statement.executeUpdate();
        }
        store.load(id, seed, now);
        return id;
    }

    private int balance(UUID id) throws Exception {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT points FROM player_pointss WHERE player_uuid=?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getInt(1); }
        }
    }

    private int count(String table) throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private SupporterAccount account(UUID id) throws Exception { return store.load(id, SupporterAccount.empty(), now); }

    private SupporterStore.Purchase buy(UUID id, UUID orderId, SupporterPricing.Quote quote) throws Exception {
        return store.purchase(orderId, id, "StoreTest", pricing, quote, false, now);
    }

    @Test
    public void insufficientOrNegativeBalanceCannotCreateAReceiptOrGrant() throws Exception {
        for (int amount : new int[]{4999, -100}) {
            UUID id = player(amount, SupporterAccount.empty());
            SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
            assertThrows(IllegalArgumentException.class, () -> buy(id, UUID.randomUUID(), quote));
            assertEquals(amount, balance(id));
            assertFalse(account(id).isPermanent());
        }
        assertEquals(0, count("pit_supporter_orders"));
        assertEquals(0, count("logs_points"));
    }

    @Test
    public void memberMustBePurchasedBeforeTheSeparateContractTransaction() throws Exception {
        UUID id = player(13800, SupporterAccount.empty());
        buy(id, UUID.randomUUID(), pricing.quote(account(id), SupporterProduct.PERMANENT, false, now));
        assertEquals(8800, balance(id));
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.SACRED, false, now);
        SupporterStore.Purchase result = buy(id, UUID.randomUUID(), quote);
        assertEquals(0, balance(id));
        assertTrue(account(id).isPermanent());
        assertEquals(SupporterTier.SACRED, account(id).activeTier(now));
        assertEquals(now + TimeUnit.DAYS.toMillis(30), account(id).getExpiresAt());
        assertEquals(8800, result.getPoints());
        assertFalse(result.isIncludesPermanent());
        assertEquals(2, count("pit_supporter_orders"));
        assertEquals(2, count("logs_points"));
    }

    @Test
    public void ledgerFailureRollsBackTheDebitReceiptAndEntitlement() throws Exception {
        UUID id = player(9000, new SupporterAccount(true, false, SupporterTier.NONE, 0L, 0L));
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE logs_points ADD CONSTRAINT reject_test_purchase CHECK(operation='SET')");
        }
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.MYTHIC, false, now);
        assertThrows(Exception.class, () -> buy(id, UUID.randomUUID(), quote));
        assertEquals(9000, balance(id));
        assertTrue(account(id).isPermanent());
        assertEquals(SupporterTier.NONE, account(id).getTier());
        assertEquals(0, count("pit_supporter_orders"));
    }

    @Test
    public void forgedMemberQuoteAndWildcardPermissionCannotBypassTheDatabaseGate() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        SupporterAccount forgedMember = new SupporterAccount(true, false, SupporterTier.NONE, 0L, 0L);
        for (SupporterProduct product : new SupporterProduct[]{SupporterProduct.MYTHIC, SupporterProduct.SACRED}) {
            SupporterPricing.Quote forged = pricing.quote(forgedMember, product, true, now);
            assertThrows(IllegalArgumentException.class, () -> store.purchase(UUID.randomUUID(), id, "StoreTest", pricing, forged, true, now));
        }
        assertEquals(20000, balance(id));
        assertFalse(account(id).isPermanent());
        assertEquals(0, count("pit_supporter_orders"));
        assertEquals(0, count("logs_points"));
    }

    @Test
    public void failedRollbackDiscardsTheConnectionWithoutAutoCommittingAPartialDebit() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE logs_points ADD CONSTRAINT reject_failed_rollback CHECK(operation='SET')");
        }
        AtomicBoolean unsafeReset = new AtomicBoolean(false);
        AtomicBoolean rollbackFailed = new AtomicBoolean(false);
        SupporterStore faulty = new SupporterStore(new SupporterStore.Connections() {
            public Connection open() throws Exception {
                Connection real = SupporterStoreTest.this.open();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("rollback")) {
                                rollbackFailed.set(true);
                                throw new SQLException("Simulated rollback transport failure");
                            }
                            if (method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(arguments[0]) && rollbackFailed.get()) {
                                unsafeReset.set(true);
                            }
                            try { return method.invoke(real, arguments); }
                            catch (InvocationTargetException exception) { throw exception.getCause(); }
                        });
            }
            public void release(Connection connection) throws Exception { connection.close(); }
        }, "thepit");
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
        assertThrows(SQLException.class, () -> faulty.purchase(UUID.randomUUID(), id, "StoreTest", pricing, quote, false, now));
        assertTrue(rollbackFailed.get());
        assertFalse(unsafeReset.get());
        assertEquals(20000, balance(id));
        assertFalse(account(id).isPermanent());
        assertEquals(0, count("pit_supporter_orders"));
    }

    @Test
    public void repeatedReceiptReturnsLatestStateAndNeverDebitsAgain() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        UUID original = UUID.randomUUID();
        SupporterPricing.Quote permanent = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
        buy(id, original, permanent);
        SupporterPricing.Quote sacred = pricing.quote(account(id), SupporterProduct.SACRED, false, now);
        buy(id, UUID.randomUUID(), sacred);
        SupporterStore.Purchase repeat = buy(id, original, permanent);
        assertTrue(repeat.isDuplicate());
        assertEquals(SupporterTier.SACRED, repeat.getAccount().activeTier(now));
        assertEquals(6200, balance(id));
        assertEquals(2, count("pit_supporter_orders"));
        assertEquals(2, count("logs_points"));
    }

    @Test
    public void retryAfterACommittedButUnacknowledgedTransactionDoesNotChargeAgain() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        UUID orderId = UUID.randomUUID();
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
        AtomicBoolean loseCommitResponse = new AtomicBoolean(true);
        SupporterStore uncertain = new SupporterStore(new SupporterStore.Connections() {
            public Connection open() throws Exception {
                Connection real = SupporterStoreTest.this.open();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, arguments) -> {
                            try {
                                Object result = method.invoke(real, arguments);
                                if (method.getName().equals("commit") && loseCommitResponse.getAndSet(false)) {
                                    throw new SQLException("Simulated lost commit acknowledgement");
                                }
                                return result;
                            } catch (InvocationTargetException exception) { throw exception.getCause(); }
                        });
            }
            public void release(Connection connection) throws Exception { connection.close(); }
        }, "thepit");
        assertThrows(SQLException.class, () -> uncertain.purchase(orderId, id, "StoreTest", pricing, quote, false, now));
        SupporterStore.Purchase retried = uncertain.purchase(orderId, id, "StoreTest", pricing, quote, false, now);
        assertTrue(retried.isDuplicate());
        assertEquals(15000, balance(id));
        assertTrue(account(id).isPermanent());
        assertEquals(1, count("pit_supporter_orders"));
        assertEquals(1, count("logs_points"));
    }

    @Test
    public void differentReceiptsFromTheSameConfirmationRevisionCannotDoubleCharge() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
        buy(id, UUID.randomUUID(), quote);
        assertThrows(IllegalArgumentException.class, () -> buy(id, UUID.randomUUID(), quote));
        assertEquals(15000, balance(id));
        assertEquals(1, count("pit_supporter_orders"));
    }

    @Test
    public void concurrentReplayHasExactlyOneCharge() throws Exception {
        UUID id = player(20000, SupporterAccount.empty());
        UUID orderId = UUID.randomUUID();
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.PERMANENT, false, now);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SupporterStore.Purchase> first = executor.submit(() -> { start.await(); return buy(id, orderId, quote); });
            Future<SupporterStore.Purchase> second = executor.submit(() -> { start.await(); return buy(id, orderId, quote); });
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS).isDuplicate(), second.get(10, TimeUnit.SECONDS).isDuplicate());
            assertEquals(15000, balance(id));
            assertEquals(1, count("pit_supporter_orders"));
            assertEquals(1, count("logs_points"));
        } finally { executor.shutdownNow(); }
    }

    @Test
    public void upgradeRetainsDeadlineAndChargesOnlyHalfPeriodDifference() throws Exception {
        long expiry = now + TimeUnit.DAYS.toMillis(15);
        UUID id = player(5000, new SupporterAccount(true, false, SupporterTier.MYTHIC, expiry, 0));
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.SACRED, false, now);
        buy(id, UUID.randomUUID(), quote);
        assertEquals(2500, balance(id));
        assertEquals(expiry, account(id).getExpiresAt());
        assertEquals(SupporterTier.SACRED, account(id).activeTier(now));
    }

    @Test
    public void upgradeCannotTurnIntoAnUnapprovedFullPeriodPurchaseAfterExpiry() throws Exception {
        UUID id = player(20000, new SupporterAccount(true, false, SupporterTier.MYTHIC, now + 1000L, 0));
        SupporterPricing.Quote quote = pricing.quote(account(id), SupporterProduct.SACRED, false, now);
        assertThrows(IllegalArgumentException.class, () -> store.purchase(UUID.randomUUID(), id, "StoreTest", pricing,
                quote, false, now + 1001L));
        assertEquals(20000, balance(id));
        assertEquals(0, count("pit_supporter_orders"));
    }

    @Test
    public void existingAccountWinsOverLegacySeedingAndRevocationsSurviveReload() throws Exception {
        UUID id = player(20000, new SupporterAccount(true, false, SupporterTier.SACRED, now + 10000, 0));
        store.setPermanent(id, "StoreTest", false, now);
        SupporterAccount reloaded = store.load(id, new SupporterAccount(true, false, SupporterTier.SACRED, now + 999999, 0), now);
        assertFalse(reloaded.isPermanent());
        assertEquals(SupporterTier.NONE, reloaded.getTier());
        assertEquals(20000, balance(id));
    }
}
