package cn.charlotte.pit.bot;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.Assert.*;

public class PublicPlayerIdentityStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final List<String> NAMES = Arrays.asList("MapleRush", "YukiHaze", "NoxTrail");
    private PublicPlayerIdentityStore.Records records(Set<String> names, Set<UUID> ids) {
        return new PublicPlayerIdentityStore.Records() {
            public boolean nameOccupied(String name, UUID own) { return names.contains(name.toLowerCase(Locale.ROOT)); }
            public boolean uuidOccupied(UUID id) { return ids.contains(id); }
        };
    }
    @Test public void restartKeepsExactlyThreeNamesAndIndependentUuids() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("identities.dat");
        PublicPlayerIdentityStore store = new PublicPlayerIdentityStore(file);
        List<PublicPlayerIdentityStore.Identity> first = store.resolve(NAMES, records(Collections.emptySet(), Collections.emptySet()));
        byte[] before = Files.readAllBytes(file);
        assertEquals(first, new PublicPlayerIdentityStore(file).resolve(NAMES, records(Collections.emptySet(), Collections.emptySet())));
        assertArrayEquals(before, Files.readAllBytes(file));
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            assertEquals(NAMES.get(i), first.get(i).name);
            assertEquals(2, first.get(i).uuid.version());
            assertTrue(ids.add(first.get(i).uuid));
            assertNotEquals(UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAMES.get(i)).getBytes(java.nio.charset.StandardCharsets.UTF_8)), first.get(i).uuid);
        }
    }
    @Test public void collisionsAreCaseInsensitiveAndChosenFallbackSurvivesRestart() throws Exception {
        PublicPlayerIdentityStore store = new PublicPlayerIdentityStore(temporary.getRoot().toPath().resolve("identities.dat"));
        Set<String> occupied = new HashSet<>(Arrays.asList("maplerush", "maplerush1", "yukihaze"));
        List<PublicPlayerIdentityStore.Identity> resolved = store.resolve(NAMES, records(occupied, Collections.emptySet()));
        assertEquals("MapleRush2", resolved.get(0).name);
        assertEquals("YukiHaze1", resolved.get(1).name);
        assertEquals("NoxTrail", resolved.get(2).name);
        assertEquals(resolved, store.resolve(NAMES, records(Collections.emptySet(), Collections.emptySet())));
    }
    @Test public void configuredNameChangesDoNotChangeTheReservedUuid() throws Exception {
        PublicPlayerIdentityStore store = new PublicPlayerIdentityStore(temporary.getRoot().toPath().resolve("identities.dat"));
        List<PublicPlayerIdentityStore.Identity> first = store.resolve(NAMES, records(Collections.emptySet(), Collections.emptySet()));
        List<PublicPlayerIdentityStore.Identity> changed = store.resolve(Arrays.asList("MapleRiver", "YukiHaze", "NoxTrail"), records(Collections.emptySet(), Collections.emptySet()));
        assertEquals(first.get(0).uuid, changed.get(0).uuid);
        assertEquals("MapleRiver", changed.get(0).name);
    }
    @Test public void corruptionCannotSilentlyCreateThreeNewIdentities() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("identities.dat");
        PublicPlayerIdentityStore store = new PublicPlayerIdentityStore(file);
        store.resolve(NAMES, records(Collections.emptySet(), Collections.emptySet()));
        byte[] bytes = Files.readAllBytes(file); bytes[25] ^= 1; Files.write(file, bytes);
        try { store.resolve(NAMES, records(Collections.emptySet(), Collections.emptySet())); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("checksum")); }
    }
    @Test public void slotCountAndNameFormatAreValidatedBeforeCreatingActors() throws Exception {
        PublicPlayerIdentityStore store = new PublicPlayerIdentityStore(temporary.getRoot().toPath().resolve("identities.dat"));
        try { store.resolve(Arrays.asList("One", "Two", "Three", "Four"), records(Collections.emptySet(), Collections.emptySet())); fail(); }
        catch (IllegalArgumentException expected) { }
        try { store.resolve(Arrays.asList("Bad Name", "YukiHaze", "NoxTrail"), records(Collections.emptySet(), Collections.emptySet())); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
