package cn.charlotte.pit.data.mail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MailClaimTransactionTest {
    @Test
    public void savesReceiptAndRewardsBeforeAcknowledgingMail() {
        List<String> receipts = new ArrayList<>();
        List<String> order = new ArrayList<>();
        MailClaimTransaction.complete("mail", receipts, () -> order.add("grant"), () -> {
            assertTrue(receipts.contains(MailClaimTransaction.receiptId("mail")));
            order.add("profile");
        }, claimed -> order.add("claimed=" + claimed), () -> order.add("mail"));
        assertEquals(Arrays.asList("grant", "profile", "claimed=true", "mail"), order);
    }

    @Test
    public void failedProfileSaveCanRetryWithoutRepeatingCurrencyOrItems() {
        List<String> receipts = new ArrayList<>();
        AtomicInteger grants = new AtomicInteger();
        AtomicBoolean claimed = new AtomicBoolean();
        try {
            MailClaimTransaction.complete("mail", receipts, grants::incrementAndGet,
                    () -> { throw new IllegalStateException("database offline"); }, claimed::set,
                    () -> fail("mail must not be saved before the reward"));
            fail("save must fail");
        } catch (IllegalStateException expected) {
            assertFalse(claimed.get());
        }
        MailClaimTransaction.complete("mail", receipts, grants::incrementAndGet,
                () -> { }, claimed::set, () -> { });
        assertEquals(1, grants.get());
        assertTrue(claimed.get());
    }

    @Test
    public void failedMailSaveIsRecoverableFromPersistedProfileReceiptAfterReload() {
        List<String> receipts = new ArrayList<>();
        List<String> persisted = new ArrayList<>();
        AtomicInteger grants = new AtomicInteger();
        AtomicBoolean claimed = new AtomicBoolean();
        try {
            MailClaimTransaction.complete("mail", receipts, grants::incrementAndGet,
                    () -> persisted.addAll(receipts), claimed::set,
                    () -> { throw new IllegalStateException("mail write failed"); });
            fail("save must fail");
        } catch (IllegalStateException expected) {
            assertFalse(claimed.get());
        }
        MailClaimTransaction.complete("mail", new ArrayList<>(persisted), grants::incrementAndGet,
                () -> { }, claimed::set, () -> { });
        assertEquals(1, grants.get());
        assertTrue(claimed.get());
    }

    @Test
    public void grantFailureDoesNotRecordReceiptOrAcknowledgeMail() {
        List<String> receipts = new ArrayList<>();
        try {
            MailClaimTransaction.complete("mail", receipts,
                    () -> { throw new IllegalArgumentException("invalid attachment"); },
                    () -> fail("profile must not save an unsuccessful grant"),
                    ignored -> fail("mail must remain claimable"), () -> fail("mail must not save"));
            fail("grant must fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(receipts.isEmpty());
        }
    }
}
