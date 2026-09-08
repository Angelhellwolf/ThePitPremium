package cn.charlotte.pit.data.mail;

import java.util.List;
import java.util.function.Consumer;

/**
 * Saves the reward and its receipt in the same player document before acknowledging the mail.
 * A failed save retains the in-memory receipt so a retry cannot grant the reward twice.
 */
public final class MailClaimTransaction {
    private MailClaimTransaction() {
    }

    public static String receiptId(String mailId) {
        return "mail-claim:" + mailId;
    }

    public static void complete(String mailId, List<String> receipts, Runnable grantRewards,
                                Runnable saveProfile, Consumer<Boolean> setClaimed, Runnable saveMail) {
        String receipt = receiptId(mailId);
        if (!receipts.contains(receipt)) {
            grantRewards.run();
            receipts.add(receipt);
        }
        // On failure, leave the mail unacknowledged and the receipt available for retry.
        saveProfile.run();
        setClaimed.accept(true);
        try {
            saveMail.run();
        } catch (RuntimeException exception) {
            setClaimed.accept(false);
            throw exception;
        }
    }
}
