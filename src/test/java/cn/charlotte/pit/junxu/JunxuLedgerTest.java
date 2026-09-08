package cn.charlotte.pit.junxu;

import org.junit.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static org.junit.Assert.*;

public class JunxuLedgerTest {
    private Path file;private JunxuLedger ledger;private UUID owner;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final long FOUR=4*3600000L,EIGHT=8*3600000L;
    private long at(String local){return LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli();}
    @Before public void setup()throws Exception{file=Files.createTempDirectory("junxu-ledger-test").resolve("ledger.dat");ledger=new JunxuLedger(file,ZONE);owner=UUID.randomUUID();ledger.create(owner,"Owner");}
    @After public void cleanup()throws Exception{Files.deleteIfExists(file.resolveSibling("ledger.dat.tmp"));Files.deleteIfExists(file);Files.deleteIfExists(file.getParent());}
    @Test public void closedPeriodsAndSecondOpeningAreNotCharged()throws Exception{
        long start=at("2026-09-07T12:00:00");assertTrue(ledger.open(owner,start,FOUR,Long.MAX_VALUE));
        ledger.renew(owner,start+2500,FOUR,Long.MAX_VALUE);ledger.close(owner,start+3500);
        assertEquals(3500,ledger.used(owner,start+100000));
        assertTrue(ledger.open(owner,start+100000,FOUR,Long.MAX_VALUE));ledger.close(owner,start+102750);
        assertEquals(6250,ledger.used(owner,start+102750));
    }
    @Test public void dailyQuotaDoesNotStackOnUpgrade()throws Exception{
        long t=at("2026-09-07T08:00:00");ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.renew(owner,t+3*3600000L,FOUR,Long.MAX_VALUE);
        assertEquals(3600000L,ledger.remaining(owner,t+3*3600000L,FOUR));
        ledger.renew(owner,t+3*3600000L,EIGHT,Long.MAX_VALUE);
        assertEquals(5*3600000L,ledger.remaining(owner,t+3*3600000L,EIGHT));
    }
    @Test public void ownersConsumeSeparateLedgers()throws Exception{
        UUID second=UUID.randomUUID();ledger.create(second,"Second");long t=at("2026-09-07T12:00:00");
        ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.open(second,t+1000,EIGHT,Long.MAX_VALUE);
        ledger.close(owner,t+3000);ledger.close(second,t+5000);
        assertEquals(3000,ledger.used(owner,t+5000));assertEquals(4000,ledger.used(second,t+5000));
        assertNotEquals(ledger.get(owner).slot,ledger.get(second).slot);
    }
    @Test public void oneOwnerCannotOpenTwice()throws Exception{
        long t=at("2026-09-07T12:00:00");assertTrue(ledger.open(owner,t,FOUR,Long.MAX_VALUE));assertFalse(ledger.open(owner,t+1000,FOUR,Long.MAX_VALUE));
        ledger.close(owner,t+2000);assertEquals(2000,ledger.used(owner,t+2000));
    }
    @Test public void cleanRestartPreservesQuotaAndSlotAndStory()throws Exception{
        long t=at("2026-09-07T12:00:00");ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.close(owner,t+2468);ledger.built(owner,"r1");ledger.completeIntroduction(owner);
        int slot=ledger.get(owner).slot;ledger=new JunxuLedger(file,ZONE);
        assertFalse(ledger.running(owner));assertEquals(2468,ledger.used(owner,t+3000));assertEquals(slot,ledger.get(owner).slot);
        assertTrue(ledger.get(owner).introductionComplete);assertEquals("r1",ledger.get(owner).builtRevision);
    }
    @Test public void uncleanRestartDoesNotGrantBackReservedTime()throws Exception{
        long t=at("2026-09-07T12:00:00");ledger.open(owner,t,FOUR,Long.MAX_VALUE);
        ledger=new JunxuLedger(file,ZONE);assertFalse(ledger.running(owner));assertEquals(5000,ledger.used(owner,t+1000));
        ledger.open(owner,t+1000,FOUR,Long.MAX_VALUE);ledger.close(owner,t+2000);assertEquals(6000,ledger.used(owner,t+2000));
    }
    @Test public void midnightSplitsOneContinuousOpening()throws Exception{
        long t=at("2026-09-07T23:59:58");ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.renew(owner,t+1000,FOUR,Long.MAX_VALUE);
        assertEquals(1000,ledger.used(owner,t+1000));
        ledger.renew(owner,t+4000,FOUR,Long.MAX_VALUE);assertEquals(2000,ledger.used(owner,t+4000));
        ledger.close(owner,t+4500);assertEquals(2500,ledger.used(owner,t+4500));
    }
    @Test public void expiryIsExactAndCannotRenew()throws Exception{
        long t=at("2026-09-07T12:00:00");assertTrue(ledger.open(owner,t,FOUR,t+2500));
        assertFalse(ledger.renew(owner,t+2500,FOUR,t+2500));assertEquals(2500,ledger.used(owner,t+2500));
    }
    @Test public void exhaustionBeforeMidnightClosesInsteadOfRevivingAcrossDay()throws Exception{
        long t=at("2026-09-07T23:59:58");assertTrue(ledger.open(owner,t,1000,Long.MAX_VALUE));
        assertFalse(ledger.renew(owner,t+3000,1000,Long.MAX_VALUE));
        assertFalse(ledger.running(owner));
        assertEquals(0,ledger.used(owner,t+3000));
    }
    @Test public void downgradeClosesOnceActualUseExceedsLowerLimit()throws Exception{
        long t=at("2026-09-07T08:00:00");ledger.open(owner,t,EIGHT,Long.MAX_VALUE);ledger.renew(owner,t+5*3600000L,EIGHT,Long.MAX_VALUE);
        assertFalse(ledger.renew(owner,t+5*3600000L,FOUR,Long.MAX_VALUE));
        assertEquals(5*3600000L,ledger.used(owner,t+5*3600000L));
    }
    @Test public void backwardsClockCannotRefundUsedTime()throws Exception{
        long t=at("2026-09-07T12:00:00");ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.renew(owner,t+2000,FOUR,Long.MAX_VALUE);
        ledger.close(owner,t+1000);assertEquals(2000,ledger.used(owner,t+1000));
    }
    @Test public void corruptedLedgerAndTimezoneChangesFailClosed()throws Exception{
        assertThrows(IOException.class,()->new JunxuLedger(file,ZoneId.of("UTC")));
        byte[] bytes=Files.readAllBytes(file);bytes[bytes.length/2]^=1;Files.write(file,bytes);
        assertThrows(IOException.class,()->new JunxuLedger(file,ZONE));
    }
    @Test public void zeroQuotaDoesNotCreateLease()throws Exception{assertFalse(ledger.open(owner,at("2026-09-07T12:00:00"),0,Long.MAX_VALUE));assertEquals(0,ledger.used(owner,at("2026-09-07T12:01:00")));}
    @Test public void delayedCloseIncludesUnreservedTailWithoutChargingAfterQuota()throws Exception{
        long t=at("2026-09-07T12:00:00");ledger.open(owner,t,FOUR,Long.MAX_VALUE);ledger.close(owner,t+8500);assertEquals(8500,ledger.used(owner,t+8500));
        UUID second=UUID.randomUUID();ledger.create(second,"Second");ledger.open(second,t+10000,1200,Long.MAX_VALUE);ledger.close(second,t+20000);assertEquals(1200,ledger.used(second,t+20000));
    }
}
