package cn.charlotte.pit.bot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Exactly three durable identities. NPC/entity ids are disposable; these UUIDs are not. */
public final class PublicPlayerIdentityStore {
    public static final int SIZE = 3;
    private static final int MAGIC = 0x50534931;
    private final Path file;

    public PublicPlayerIdentityStore(Path file) { this.file = file; }

    public interface Records {
        boolean nameOccupied(String name, UUID ownId);
        boolean uuidOccupied(UUID id);
    }

    public List<Identity> resolve(List<String> names, Records records) throws IOException {
        if (names.size() != SIZE) throw new IllegalArgumentException("Exactly three public players are required");
        List<Identity> saved = load();
        List<Identity> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        Set<UUID> usedIds = new HashSet<>();
        for (int slot = 0; slot < SIZE; slot++) {
            String preferred = names.get(slot);
            validateName(preferred);
            Identity previous = saved.isEmpty() ? null : saved.get(slot);
            UUID id = previous == null ? UUID.randomUUID() : previous.uuid;
            // Citizens maps v4 player NPC ids to v2 on the wire. Store the actual player UUID directly.
            id = new UUID((id.getMostSignificantBits() & ~0xf000L) | 0x2000L, id.getLeastSignificantBits());
            for (int attempts = 0; usedIds.contains(id) || records.uuidOccupied(id); attempts++) {
                if (attempts >= 100) throw new IOException("Unable to reserve a public player UUID");
                UUID fresh = UUID.randomUUID();
                id = new UUID((fresh.getMostSignificantBits() & ~0xf000L) | 0x2000L, fresh.getLeastSignificantBits());
            }
            String candidate = previous != null && preferred.equals(previous.preferred) ? previous.name : preferred;
            for (int suffix = 1; usedNames.contains(candidate.toLowerCase(Locale.ROOT)) || records.nameOccupied(candidate, id); suffix++) {
                if (suffix > 9999) throw new IOException("No free player name for " + preferred);
                String tail = Integer.toString(suffix);
                candidate = preferred.substring(0, Math.min(preferred.length(), 16 - tail.length())) + tail;
            }
            usedNames.add(candidate.toLowerCase(Locale.ROOT));
            usedIds.add(id);
            result.add(new Identity(slot, id, preferred, candidate));
        }
        if (!result.equals(saved)) save(result);
        return Collections.unmodifiableList(result);
    }

    public List<Identity> load() throws IOException {
        if (Files.notExists(file)) return Collections.emptyList();
        if (Files.size(file) > 8192) throw new IOException("Identity file too large");
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 16) throw new IOException("Truncated identity file");
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - 8);
        if (ByteBuffer.wrap(bytes).getLong(bytes.length - 8) != crc.getValue()) throw new IOException("Identity checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != SIZE) throw new IOException("Unknown identity format");
            List<Identity> identities = new ArrayList<>();
            Set<UUID> ids = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (int slot = 0; slot < SIZE; slot++) {
                Identity identity = new Identity(slot, new UUID(in.readLong(), in.readLong()), in.readUTF(), in.readUTF());
                if (!ids.add(identity.uuid) || !names.add(identity.name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate public identity");
                identities.add(identity);
            }
            in.readLong();
            if (in.available() != 0) throw new IOException("Unexpected identity data");
            return identities;
        } catch (IllegalArgumentException exception) { throw new IOException("Invalid public identity", exception); }
    }

    private void save(List<Identity> identities) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(SIZE);
            for (Identity identity : identities) {
                out.writeLong(identity.uuid.getMostSignificantBits());
                out.writeLong(identity.uuid.getLeastSignificantBits());
                out.writeUTF(identity.preferred);
                out.writeUTF(identity.name);
            }
            out.flush();
            CRC32 crc = new CRC32();
            crc.update(bytes.toByteArray());
            out.writeLong(crc.getValue());
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray());
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid player name: " + name);
    }

    public static final class Identity {
        public final int slot;
        public final UUID uuid;
        public final String preferred, name;
        Identity(int slot, UUID uuid, String preferred, String name) {
            validateName(preferred); validateName(name);
            this.slot = slot; this.uuid = Objects.requireNonNull(uuid); this.preferred = preferred; this.name = name;
        }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Identity)) return false;
            Identity other = (Identity) object;
            return slot == other.slot && uuid.equals(other.uuid) && preferred.equals(other.preferred) && name.equals(other.name);
        }
        @Override public int hashCode() { return Objects.hash(slot, uuid, preferred, name); }
    }
}
