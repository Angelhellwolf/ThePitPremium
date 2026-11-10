package cn.charlotte.pit.reward;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.LongPredicate;
import java.util.zip.CRC32;

/** Cumulative random-phase rounding with personal and room pressure. Server-wide pressure is disabled. */
public final class RareSupplyLedger implements AutoCloseable {
    public enum Pool { FRESH, RARE }
    public static final long HOUR = 3_600_000L, DAY = 24 * HOUR;
    public static final class Limits {
        public final int serverHour, serverDay, playerHour, playerDay, groupHour, groupDay;
        public Limits(int sh, int sd, int ph, int pd, int gh, int gd) {
            if (sh < 1 || sd < sh || ph < 1 || pd < ph || gh < ph || gd < pd
                    || sh < gh || sd < gd) throw new IllegalArgumentException("Invalid rare supply limits");
            serverHour = sh; serverDay = sd; playerHour = ph; playerDay = pd; groupHour = gh; groupDay = gd;
        }
    }
    public static final UUID SC = UUID.fromString("e4bc7a15-8174-35ea-b23f-4c50080db6e9");
    public static final UUID HZ77 = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");
    private static final int MAGIC = 0x52534c31, VERSION = 2;
    private final Path file;
    private final DoubleSupplier random;
    private final LongPredicate compensationEnabled;
    private final FileChannel lockChannel;
    private final java.nio.channels.FileLock lock;
    private final EnumMap<Pool, Limits> limits;
    private final Map<String, Double> progress = new LinkedHashMap<>();
    private final List<Grant> grants = new ArrayList<>();
    private final List<Transfer> transfers = new ArrayList<>();
    private final Map<UUID, Long> compensation = new LinkedHashMap<>();
    private final Map<UUID, GroundDrop> groundDrops = new LinkedHashMap<>();
    private long lastNow;

    public RareSupplyLedger(Path file, EnumMap<Pool, Limits> limits, DoubleSupplier random) throws IOException {
        this(file, limits, random, now -> false);
    }

    public RareSupplyLedger(Path file, EnumMap<Pool, Limits> limits, DoubleSupplier random,
                            LongPredicate compensationEnabled) throws IOException {
        this.file = file; this.limits = new EnumMap<>(limits); this.random = random;
        this.compensationEnabled = Objects.requireNonNull(compensationEnabled);
        if (this.limits.size() != Pool.values().length) throw new IllegalArgumentException("Missing pool limits");
        Files.createDirectories(file.toAbsolutePath().getParent());
        lockChannel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        java.nio.channels.FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException error) { lockChannel.close(); throw error; }
        if (acquired == null) { lockChannel.close(); throw new IOException("Rare supply ledger already in use"); }
        lock = acquired;
        try { load(); }
        catch (IOException | RuntimeException error) { close(); throw error; }
    }

    public synchronized boolean available(Pool pool, UUID player, String group, long now) {
        return true;
    }

    /** Saturation retains at most one due result. Zero-weight attempts never redeem it. */
    public synchronized boolean attempt(Pool pool, UUID player, String group, double probability, long now) throws IOException {
        if (!Double.isFinite(probability) || probability <= 0) return false;
        probability = Math.min(1, probability);
        now = monotonic(now);
        String key = pool.name() + ":" + player;
        double phase = progress.computeIfAbsent(key, ignored -> random.getAsDouble());
        phase += probability;
        progress.put(key, phase);
        if (phase < 1 - 1e-12) return false;
        if (!available(pool, player, group, now)) { progress.put(key, Math.min(1, phase)); return false; }
        // Debit and fsync before the caller puts a new item into a player's inventory.
        progress.put(key, Math.max(0, phase - 1));
        grants.add(new Grant(pool, player, group, now, first(pool, player)));
        save();
        return true;
    }

    /** Supply fences are retired; member probabilities are the only random weight. */
    public synchronized double multiplier(Pool pool, UUID player, String group, long now) {
        return 1;
    }

    public synchronized boolean attemptSoft(Pool pool, UUID player, String group, double probability, long now) throws IOException {
        if (!Double.isFinite(probability) || probability <= 0) return false;
        now = monotonic(now);
        return attemptWeight(pool, player, group, Math.min(1, probability), now);
    }

    /** Random attempt for an exempt account; pressure is intentionally not applied. */
    public synchronized boolean attemptSoftBypass(Pool pool, UUID player, String group, double probability, long now) throws IOException {
        if (!Double.isFinite(probability) || probability <= 0) return false;
        now = monotonic(now);
        return attemptWeight(pool, player, group, Math.min(1, probability), now);
    }

    public static boolean isolated(Pool pool, UUID player) {
        return pool == Pool.RARE && (SC.equals(player) || HZ77.equals(player));
    }

    public static double specialRareBase(UUID player, boolean scPermission, double ordinary) {
        return ordinary;
    }

    public synchronized boolean compensationAvailable(UUID player, int stock, long now) {
        now = monotonic(now); prune(now);
        Long used = compensation.get(player);
        return compensationEnabled.test(now) && stock >= 0 && stock <= 1 && (used == null || used <= now - DAY)
                && transfers.stream().noneMatch(t -> t.sender.equals(player))
                && groundDrops.values().stream().noneMatch(drop -> drop.sender.equals(player));
    }

    /** One boosted eligible roll per rolling day while the activity is enabled. */
    public synchronized boolean attemptEnchant(UUID player, String group, double probability, int stock,
                                               boolean bypass, long now) throws IOException {
        if (!Double.isFinite(probability) || probability <= 0) return false;
        now = monotonic(now);
        boolean boost = compensationAvailable(player, stock, now) && probability < .20;
        if (boost) { probability = .20; compensation.put(player, now); }
        boolean result = bypass ? attemptSoftBypass(Pool.RARE, player, group, probability, now)
                : attemptSoft(Pool.RARE, player, group, probability, now);
        if (boost && !result) save();
        return result;
    }

    public synchronized double transferMultiplier(UUID player, long now) {
        now = monotonic(now); prune(now);
        int hour = 0, day = 0;
        for (Transfer transfer : transfers) if (transfer.sender.equals(player)) {
            day++; if (transfer.time > now - HOUR) hour++;
        }
        if (hour >= 6 || day >= 12) return .10;
        return hour >= 3 || day >= 6 ? .25 : 1;
    }

    /** Repeated delivery of the same physical item by the same sender counts once per day. */
    public synchronized void transfer(UUID sender, UUID recipient, Collection<String> items, long now) throws IOException {
        if (sender.equals(recipient) || items.isEmpty()) return;
        if (items.size() > 1024) throw new IllegalArgumentException("Invalid transfer size");
        now = monotonic(now); prune(now);
        for (String item : new LinkedHashSet<>(items)) {
            if (item == null || item.isEmpty() || item.length() > 256) throw new IllegalArgumentException("Invalid rare item key");
            if (transfers.stream().noneMatch(t -> t.sender.equals(sender) && t.item.equals(item)))
                transfers.add(new Transfer(sender, recipient, item, now));
        }
        save();
    }

    public synchronized void groundDrop(UUID entity, UUID sender, Collection<String> items, long now) throws IOException {
        if (items.isEmpty()) return;
        if (items.size() > 1024) throw new IllegalArgumentException("Invalid drop size");
        now = monotonic(now); prune(now);
        groundDrops.put(entity, new GroundDrop(sender, new ArrayList<>(items), now));
        save();
    }

    public synchronized void groundPickup(UUID entity, UUID recipient, int amount, long now) throws IOException {
        if (amount <= 0) return;
        now = monotonic(now); prune(now);
        GroundDrop drop = groundDrops.get(entity);
        if (drop == null) return;
        int count = Math.min(amount, drop.items.size());
        List<String> delivered = new ArrayList<>(drop.items.subList(0, count));
        drop.items.subList(0, count).clear();
        if (drop.items.isEmpty()) groundDrops.remove(entity);
        transfer(drop.sender, recipient, delivered, now);
        save();
    }

    private boolean attemptWeight(Pool pool, UUID player, String group, double weight, long now) throws IOException {
        String key = pool.name() + ":" + player;
        double phase = progress.computeIfAbsent(key, ignored -> random.getAsDouble()) + weight;
        progress.put(key, phase);
        if (phase < 1 - 1e-12) return false;
        progress.put(key, Math.max(0, phase - 1));
        record(pool, player, group, 1, now);
        return true;
    }

    /** Existing guaranteed rewards retain their outcome and contribute to subsequent pressure. */
    public synchronized void record(Pool pool, UUID player, String group, int amount, long now) throws IOException {
        if (amount < 1 || amount > 1024) throw new IllegalArgumentException("Invalid supply record amount");
        now = monotonic(now);
        prune(now);
        for (int i = 0; i < amount; i++) grants.add(new Grant(pool, player, group, now, first(pool, player)));
        save();
    }

    /** Guaranteed consumables count against supply, without spending ordinary failure progress. */
    public synchronized boolean guaranteed(Pool pool, UUID player, String group, long now) throws IOException {
        return guaranteed(pool, player, group, 1, now);
    }

    public synchronized boolean guaranteed(Pool pool, UUID player, String group, int amount, long now) throws IOException {
        if (amount < 1 || amount > limits.get(pool).playerDay) return false;
        now = monotonic(now);
        prune(now);
        int originalSize = grants.size();
        for (int i = 0; i < amount; i++) {
            if (!available(pool, player, group, now)) {
                grants.subList(originalSize, grants.size()).clear();
                return false;
            }
            grants.add(new Grant(pool, player, group, now, first(pool, player)));
        }
        save();
        return true;
    }

    public synchronized String diagnostics(long now) {
        now = monotonic(now); prune(now);
        StringBuilder out = new StringBuilder("RARE_SUPPLY progressAccounts=" + progress.size() + " globalPressure=off");
        for (Pool pool : Pool.values()) {
            int hour = 0, day = 0;
            for (Grant grant : grants) if (grant.pool == pool && !isolated(grant.pool, grant.player)) { day++; if (grant.time > now - HOUR) hour++; }
            Limits cap = limits.get(pool);
            out.append(' ').append(pool).append(" hour=").append(hour).append("/off")
                    .append(" rolling24h=").append(day).append("/off");
        }
        out.append(" isolatedRare=").append(grants.stream().filter(g -> isolated(g.pool, g.player)).count())
                .append(" transfers24h=").append(transfers.size()).append(" compensation24h=").append(compensation.size());
        return out.toString();
    }

    private long monotonic(long now) { lastNow = Math.max(lastNow, now); return lastNow; }
    private void prune(long now) {
        grants.removeIf(grant -> grant.time <= now - DAY);
        transfers.removeIf(transfer -> transfer.time <= now - DAY);
        compensation.values().removeIf(time -> time <= now - DAY);
        groundDrops.values().removeIf(drop -> drop.time <= now - DAY);
    }
    private boolean first(Pool pool, UUID player) {
        return grants.stream().noneMatch(grant -> grant.pool == pool && grant.player.equals(player));
    }

    public synchronized void save() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(lastNow);
            out.writeInt(progress.size());
            for (Map.Entry<String, Double> entry : progress.entrySet()) { out.writeUTF(entry.getKey()); out.writeDouble(entry.getValue()); }
            out.writeInt(grants.size());
            for (Grant grant : grants) {
                out.writeByte(grant.pool.ordinal()); out.writeLong(grant.player.getMostSignificantBits());
                out.writeLong(grant.player.getLeastSignificantBits()); out.writeUTF(grant.group); out.writeLong(grant.time); out.writeBoolean(grant.first);
            }
            out.writeInt(transfers.size());
            for (Transfer transfer : transfers) {
                writeUuid(out, transfer.sender); writeUuid(out, transfer.recipient);
                out.writeUTF(transfer.item); out.writeLong(transfer.time);
            }
            out.writeInt(compensation.size());
            for (Map.Entry<UUID, Long> entry : compensation.entrySet()) {
                writeUuid(out, entry.getKey()); out.writeLong(entry.getValue());
            }
            out.writeInt(groundDrops.size());
            for (Map.Entry<UUID, GroundDrop> entry : groundDrops.entrySet()) {
                writeUuid(out, entry.getKey()); writeUuid(out, entry.getValue().sender);
                out.writeLong(entry.getValue().time); out.writeInt(entry.getValue().items.size());
                for (String item : entry.getValue().items) out.writeUTF(item);
            }
            out.flush(); CRC32 crc = new CRC32(); crc.update(bytes.toByteArray()); out.writeLong(crc.getValue());
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes.toByteArray());
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        try (FileChannel directory = FileChannel.open(file.toAbsolutePath().getParent(), StandardOpenOption.READ)) {
            directory.force(true);
        }
    }

    private void load() throws IOException {
        if (Files.notExists(file)) return;
        long size = Files.size(file);
        if (size < 28 || size > 64 * 1024 * 1024) throw new IOException("Invalid rare supply ledger size");
        byte[] bytes = Files.readAllBytes(file);
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 8);
        if (ByteBuffer.wrap(bytes).getLong(bytes.length - 8) != crc.getValue()) throw new IOException("Rare supply checksum mismatch; refusing to reset limits");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bytes.length - 8))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown rare supply ledger magic");
            int version = in.readInt();
            if (version != 1 && version != VERSION) throw new IOException("Unknown rare supply ledger version");
            lastNow = in.readLong(); int count = in.readInt();
            if (count < 0 || count > 1_000_000) throw new IOException("Invalid rare progress count");
            for (int i = 0; i < count; i++) {
                String key = in.readUTF(); double phase = in.readDouble();
                if (!Double.isFinite(phase) || phase < 0 || phase > 1 || progress.put(key, phase) != null)
                    throw new IOException("Invalid rare progress");
            }
            count = in.readInt();
            if (count < 0 || count > 1_000_000) throw new IOException("Invalid rare grant count");
            for (int i = 0; i < count; i++) {
                int pool = in.readUnsignedByte();
                if (pool >= Pool.values().length) throw new IOException("Invalid rare pool");
                UUID player = new UUID(in.readLong(), in.readLong()); String group = in.readUTF(); long time = in.readLong(); boolean first = in.readBoolean();
                if (time < 0 || time > lastNow) throw new IOException("Invalid rare grant time");
                grants.add(new Grant(Pool.values()[pool], player, group, time, first));
            }
            if (version >= 2) {
                count = readCount(in, 1_000_000);
                for (int i = 0; i < count; i++) {
                    UUID sender = readUuid(in), recipient = readUuid(in);
                    String item = in.readUTF(); long time = readTime(in);
                    if (sender.equals(recipient) || item.isEmpty() || item.length() > 256) throw new IOException("Invalid rare transfer");
                    transfers.add(new Transfer(sender, recipient, item, time));
                }
                count = readCount(in, 1_000_000);
                for (int i = 0; i < count; i++) {
                    if (compensation.put(readUuid(in), readTime(in)) != null) throw new IOException("Duplicate rare compensation");
                }
                count = readCount(in, 1_000_000);
                for (int i = 0; i < count; i++) {
                    UUID entity = readUuid(in), sender = readUuid(in); long time = readTime(in);
                    int amount = readCount(in, 1024); List<String> items = new ArrayList<>();
                    for (int j = 0; j < amount; j++) {
                        String item = in.readUTF();
                        if (item.isEmpty() || item.length() > 256) throw new IOException("Invalid rare ground item");
                        items.add(item);
                    }
                    if (amount == 0 || groundDrops.put(entity, new GroundDrop(sender, items, time)) != null)
                        throw new IOException("Invalid rare ground drop");
                }
            }
            if (in.available() != 0) throw new IOException("Trailing rare supply data");
        }
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits()); out.writeLong(uuid.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static int readCount(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IOException("Invalid rare state count");
        return count;
    }
    private long readTime(DataInputStream in) throws IOException {
        long time = in.readLong();
        if (time < 0 || time > lastNow) throw new IOException("Invalid rare state time");
        return time;
    }

    @Override public synchronized void close() throws IOException { lock.release(); lockChannel.close(); }
    private static final class Grant {
        final Pool pool; final UUID player; final String group; final long time; final boolean first;
        Grant(Pool pool, UUID player, String group, long time, boolean first) {
            this.pool = pool; this.player = player; this.group = group; this.time = time; this.first = first;
        }
    }
    private static final class Transfer {
        final UUID sender, recipient; final String item; final long time;
        Transfer(UUID sender, UUID recipient, String item, long time) {
            this.sender = sender; this.recipient = recipient; this.item = item; this.time = time;
        }
    }
    private static final class GroundDrop {
        final UUID sender; final List<String> items; final long time;
        GroundDrop(UUID sender, List<String> items, long time) { this.sender = sender; this.items = items; this.time = time; }
    }
}
