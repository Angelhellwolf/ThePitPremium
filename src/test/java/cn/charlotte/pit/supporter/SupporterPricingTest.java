package cn.charlotte.pit.supporter;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SupporterPricingTest {
    private final SupporterPricing pricing = new SupporterPricing(50, 38, 88, 30);
    private final long now = 1_800_000_000_000L;
    private final long day = TimeUnit.DAYS.toMillis(1);

    @Test
    public void yuanPricesUseOneHundredPointsAndContractsRequireMembership() {
        SupporterAccount empty = SupporterAccount.empty();
        assertEquals(5000, pricing.quote(empty, SupporterProduct.PERMANENT, false, now).getPoints());
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(empty, SupporterProduct.MYTHIC, false, now));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(empty, SupporterProduct.SACRED, false, now));
        SupporterAccount member = new SupporterAccount(true, false, SupporterTier.NONE, 0L, 0L);
        assertEquals(3800, pricing.quote(member, SupporterProduct.MYTHIC, false, now).getPoints());
        assertEquals(8800, pricing.quote(member, SupporterProduct.SACRED, false, now).getPoints());
        assertEquals("25.01", SupporterPricing.yuan(2501));
        assertEquals("50", SupporterPricing.yuan(5000));
    }

    @Test
    public void renewalsExtendTheExistingExpiryWithoutChargingForPermanentAgain() {
        SupporterAccount account = new SupporterAccount(true, false, SupporterTier.MYTHIC, now + 5 * day, 9);
        SupporterPricing.Quote quote = pricing.quote(account, SupporterProduct.MYTHIC, false, now);
        assertEquals(3800, quote.getPoints());
        assertEquals(now + 35 * day, quote.getAfter().getExpiresAt());
        assertFalse(quote.isIncludesPermanent());
        assertTrue(quote.getAfter().isPermanent());
    }

    @Test
    public void upgradeChargesOnlyRemainingTimeAndRoundsUpToOnePoint() {
        SupporterAccount half = new SupporterAccount(true, false, SupporterTier.MYTHIC, now + 15 * day, 0);
        SupporterPricing.Quote upgrade = pricing.quote(half, SupporterProduct.SACRED, false, now);
        assertEquals(2500, upgrade.getPoints());
        assertEquals(half.getExpiresAt(), upgrade.getAfter().getExpiresAt());
        SupporterAccount moment = new SupporterAccount(true, false, SupporterTier.MYTHIC, now + 1L, 0);
        assertEquals(1, pricing.quote(moment, SupporterProduct.SACRED, false, now).getPoints());
    }

    @Test
    public void paidHighTierCannotBeReplacedByLowTierBeforeItsDeadline() {
        SupporterAccount active = new SupporterAccount(true, false, SupporterTier.SACRED, now + day, 0);
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(active, SupporterProduct.MYTHIC, false, now));
        SupporterAccount expired = new SupporterAccount(true, false, SupporterTier.SACRED, now, 0);
        SupporterPricing.Quote quote = pricing.quote(expired, SupporterProduct.MYTHIC, false, now);
        assertEquals(3800, quote.getPoints());
        assertEquals(now + 30 * day, quote.getAfter().getExpiresAt());
    }

    @Test
    public void expiryRemovesOnlyTheContractAndBuyingPermanentCannotOverwriteIt() {
        SupporterAccount expired = new SupporterAccount(true, false, SupporterTier.SACRED, now, 2);
        assertEquals(SupporterTier.NONE, expired.activeTier(now));
        assertTrue(expired.isPermanent());
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(expired, SupporterProduct.PERMANENT, false, now));
    }

    @Test
    public void activeOldMonthlySubscriptionMigratesExactlyOnceWithoutAddingTime() {
        SupporterAccount account = SupporterAccount.fromLegacy(true, false, now + 8 * day, 0, 0, now);
        assertTrue(account.isPermanent());
        assertEquals(SupporterTier.SACRED, account.activeTier(now));
        assertEquals(now + 8 * day, account.getExpiresAt());
        SupporterAccount expired = SupporterAccount.fromLegacy(true, false, now, 0, 0, now);
        assertFalse(expired.isPermanent());
        assertEquals(SupporterTier.NONE, expired.getTier());
        SupporterAccount reclaimed = SupporterAccount.fromLegacy(false, false, now + day, 0, 0, now);
        assertFalse(reclaimed.isPermanent());
        assertEquals(SupporterTier.NONE, reclaimed.getTier());
    }

    @Test
    public void permanentAndAdministrativeLegacyGrantsDoNotReceiveAContract() {
        SupporterAccount permanent = SupporterAccount.fromLegacy(true, false, 0, 0, 0, now);
        assertTrue(permanent.isPermanent());
        assertEquals(SupporterTier.NONE, permanent.getTier());
        SupporterAccount admin = SupporterAccount.fromLegacy(true, true, now + day, 0, 0, now);
        assertTrue(admin.isGivenByAdmin());
        assertEquals(SupporterTier.NONE, admin.getTier());
    }

    @Test
    public void externalPermissionsCannotReplaceStoredMembershipOrPreventBuyingIt() {
        for (SupporterProduct product : new SupporterProduct[]{SupporterProduct.MYTHIC, SupporterProduct.SACRED}) {
            assertThrows(IllegalArgumentException.class, () -> pricing.quote(SupporterAccount.empty(), product, true, now));
        }
        assertEquals(5000, pricing.quote(SupporterAccount.empty(), SupporterProduct.PERMANENT, true, now).getPoints());
    }
}
