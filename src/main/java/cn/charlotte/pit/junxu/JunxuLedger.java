package cn.charlotte.pit.junxu;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.CRC32;

/** UUID/day accounting. Persist a short paid lease before allowing a room to run.
 * Normal closure refunds its unused tail; an unclean crash cannot roll usage back.
 * Instances and introduction progress share the same checked, atomically replaced file.
 */
public final class JunxuLedger {
    private static final int MAGIC = 0x4a584c31, VERSION = 1;
    private static final long LEASE_MILLIS = 5000;
    private final Path file;
    private final ZoneId zone;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, Lease> leases = new HashMap<>();
    private int nextSlot = 1;
    private long lastNow;

    public JunxuLedger(Path file, ZoneId zone) throws IOException {
        this.file = file; this.zone = zone;
        load();
    }

    public ZoneId zone() { return zone; }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public Entry get(UUID id) { return entries.get(id); }
    public boolean running(UUID id) { return leases.containsKey(id); }
    public long day(long now) { return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay(); }
    public long midnightAfter(long now) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
    }
    // A backwards wall-clock adjustment must not refund already consumed time.
    private long monotonic(long now) { lastNow = Math.max(lastNow, now); return lastNow; }

    public Entry create(UUID id, String name) throws IOException {
        Entry existing = entries.get(id);
        if (existing != null) { existing.name = name; return existing; }
        if (nextSlot > 60000) throw new IOException("Junxu instance coordinate capacity reached");
        Entry entry = new Entry(id, name, nextSlot++);
        entries.put(id, entry);
        try { save(); } catch (IOException error) { entries.remove(id); nextSlot--; throw error; }
        return entry;
    }

    public long used(UUID id, long now) {
        now = Math.max(lastNow, now);
        Entry entry = entries.get(id);
        if (entry == null) return 0;
        long today = day(now), result = entry.days.getOrDefault(today, 0L);
        Lease lease = leases.get(id);
        if (lease != null && lease.day == today) result -= Math.max(0, lease.until - Math.max(now, lease.start));
        return Math.max(0, result);
    }
    public long remaining(UUID id, long now, long dailyLimit) { return Math.max(0, dailyLimit - used(id, now)); }

    public boolean open(UUID id, long now, long dailyLimit, long entitlementExpiry) throws IOException {
        if (leases.containsKey(id)) return false;
        if (!entries.containsKey(id)) throw new IllegalArgumentException("Missing room owner");
        return renew(id, now, dailyLimit, entitlementExpiry);
    }

    /** Main-thread transaction: returns false at quota/contract expiry. */
    public boolean renew(UUID id, long now, long dailyLimit, long entitlementExpiry) throws IOException {
        now = monotonic(now);
        Entry entry = Objects.requireNonNull(entries.get(id));
        Lease oldLease = leases.get(id);
        if (oldLease != null && oldLease.limit == dailyLimit && oldLease.expiry == entitlementExpiry
                && now < oldLease.until - 1000) return true;
        Map<Long, Long> before = new HashMap<>(entry.days);
        boolean exhaustedBeforeMidnight = oldLease != null && now >= oldLease.until
                && oldLease.until < midnightAfter(oldLease.start)
                && entry.days.getOrDefault(oldLease.day, 0L) >= dailyLimit;
        if (oldLease != null) refund(entry, oldLease, now);
        leases.remove(id);
        long today = day(now);
        long available = Math.max(0, dailyLimit - entry.days.getOrDefault(today, 0L));
        // Account for a delayed server tick rather than silently making that interval free.
        if (!exhaustedBeforeMidnight && oldLease != null && now > oldLease.until) {
            long end = Math.min(now, entitlementExpiry);
            long cursor = oldLease.until;
            while (cursor < end) {
                long partEnd = Math.min(end, midnightAfter(cursor));
                long d = day(cursor), charged = entry.days.getOrDefault(d, 0L);
                entry.days.put(d, charged + Math.max(0, Math.min(dailyLimit - charged, partEnd - cursor)));
                cursor = partEnd;
            }
            available = Math.max(0, dailyLimit - entry.days.getOrDefault(today, 0L));
        }
        long until = Math.min(Math.min(now + Math.min(LEASE_MILLIS, available), midnightAfter(now)), entitlementExpiry);
        if (!exhaustedBeforeMidnight && dailyLimit > 0 && until > now) {
            entry.days.merge(today, until - now, Long::sum);
            leases.put(id, new Lease(today, now, until, dailyLimit, entitlementExpiry));
        }
        try { save(); }
        catch (IOException error) {
            entry.days.clear(); entry.days.putAll(before);
            if (oldLease != null) leases.put(id, oldLease); else leases.remove(id);
            throw error;
        }
        return leases.containsKey(id);
    }

    public void close(UUID id, long now) throws IOException {
        now = monotonic(now);
        Lease lease = leases.remove(id);
        if (lease == null) return;
        Entry entry = entries.get(id);
        Map<Long,Long> before = new HashMap<>(entry.days);
        refund(entry, lease, now);
        boolean alreadyExhausted = lease.until < midnightAfter(lease.start)
                && entry.days.getOrDefault(lease.day, 0L) >= lease.limit;
        if (!alreadyExhausted) {
            long cursor = lease.until, end = Math.min(now, lease.expiry);
            while (cursor < end) {
                long partEnd = Math.min(end, midnightAfter(cursor)), day = day(cursor);
                long used = entry.days.getOrDefault(day, 0L);
                entry.days.put(day, used + Math.max(0, Math.min(lease.limit - used, partEnd - cursor)));
                cursor = partEnd;
            }
        }
        try { save(); }
        catch (IOException error) { entry.days.clear(); entry.days.putAll(before); throw error; }
    }
    private void refund(Entry entry, Lease lease, long now) {
        long amount = Math.max(0, lease.until - Math.max(now, lease.start));
        entry.days.computeIfPresent(lease.day, (date, used) -> Math.max(0, used - amount));
    }
    public void completeIntroduction(UUID id) throws IOException {
        Entry entry = entries.get(id);
        if (entry == null || entry.introductionComplete) return;
        entry.introductionComplete = true;
        try { save(); } catch (IOException error) { entry.introductionComplete = false; throw error; }
    }
    public void built(UUID id, String revision) throws IOException {
        Entry entry = Objects.requireNonNull(entries.get(id));
        String old = entry.builtRevision;
        entry.builtRevision = revision;
        try { save(); } catch (IOException error) { entry.builtRevision = old; throw error; }
    }
    public void save() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(zone.getId());
            out.writeInt(nextSlot); out.writeLong(lastNow); out.writeInt(entries.size());
            for (Entry entry : entries.values()) {
                out.writeLong(entry.id.getMostSignificantBits()); out.writeLong(entry.id.getLeastSignificantBits());
                out.writeUTF(entry.name); out.writeInt(entry.slot); out.writeBoolean(entry.introductionComplete);
                out.writeUTF(entry.builtRevision); out.writeInt(entry.days.size());
                for (Map.Entry<Long,Long> usage : entry.days.entrySet()) { out.writeLong(usage.getKey()); out.writeLong(usage.getValue()); }
            }
            out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray()); out.writeLong(crc.getValue());
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray());
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private void load() throws IOException {
        if (Files.notExists(file)) return;
        long size = Files.size(file);
        if (size < 24 || size > 128 * 1024 * 1024) throw new IOException("Invalid Junxu ledger size");
        byte[] bytes = Files.readAllBytes(file); CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 8);
        if (ByteBuffer.wrap(bytes).getLong(bytes.length - 8) != crc.getValue()) throw new IOException("Junxu ledger checksum mismatch; refusing to reset quotas");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unknown Junxu ledger version");
            if (!zone.getId().equals(in.readUTF())) throw new IOException("Junxu ledger timezone changed; explicit migration is required");
            nextSlot = in.readInt(); lastNow = in.readLong(); int count = in.readInt();
            if (count < 0 || count > 60000 || nextSlot < 1 || nextSlot > 60001) throw new IOException("Invalid Junxu entry count");
            Set<Integer> slots = new HashSet<>();
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                Entry entry = new Entry(id, in.readUTF(), in.readInt());
                entry.introductionComplete = in.readBoolean(); entry.builtRevision = in.readUTF();
                if (entry.slot < 1 || entry.slot >= nextSlot || !slots.add(entry.slot) || entries.put(id, entry) != null) throw new IOException("Duplicate Junxu owner/instance");
                int days = in.readInt();
                if (days < 0 || days > 100000) throw new IOException("Invalid Junxu day count");
                for (int j = 0; j < days; j++) {
                    long day = in.readLong(), used = in.readLong();
                    if (used < 0 || used > 172800000 || entry.days.put(day, used) != null) throw new IOException("Invalid Junxu usage");
                }
            }
            in.readLong(); if (in.available() != 0) throw new IOException("Unexpected Junxu ledger data");
        }
    }
    public static final class Entry {
        public final UUID id;
        public final int slot;
        public String name;
        public boolean introductionComplete;
        public String builtRevision = "";
        private final Map<Long,Long> days = new TreeMap<>();
        private Entry(UUID id, String name, int slot) { this.id = id; this.name = name; this.slot = slot; }
    }
    private static final class Lease {
        final long day, start, until, limit, expiry;
        Lease(long day, long start, long until, long limit, long expiry) {
            this.day = day; this.start = start; this.until = until; this.limit = limit; this.expiry = expiry;
        }
    }
}
