package cn.charlotte.pit.bot;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Detached snapshots: no Bukkit or mutable profile objects are used by the I/O worker. */
public final class PublicPlayerProgressStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private String lastWritten;

    public PublicPlayerProgressStore(Path file) { this.file = file; }

    public Map<UUID, Progress> load() throws IOException {
        if (Files.notExists(file)) return Collections.emptyMap();
        if (Files.size(file) > 1048576) throw new IOException("Public progression file is too large");
        try {
            String text = Files.readString(file);
            Document document = JSON.fromJson(text, Document.class);
            if (document == null || document.version != 1 || document.players == null || document.players.size() != 3)
                throw new IllegalArgumentException("Invalid progression document");
            Map<UUID, Progress> result = new HashMap<>();
            for (Progress progress : document.players) {
                progress.validate();
                if (result.put(UUID.fromString(progress.uuid), progress) != null) throw new IllegalArgumentException("Duplicate identity");
            }
            lastWritten = text;
            return result;
        } catch (RuntimeException invalid) { throw new IOException("Invalid public progression; refusing to reset it", invalid); }
    }

    public void save(List<Progress> snapshots) throws IOException {
        if (snapshots.size() != 3) throw new IOException("Refusing a partial public progression snapshot");
        Set<String> ids = new HashSet<>();
        for (Progress progress : snapshots) {
            progress.validate();
            if (!ids.add(progress.uuid)) throw new IOException("Duplicate identity");
        }
        String text = JSON.toJson(new Document(snapshots)) + "\n";
        if (text.equals(lastWritten)) return;
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) output.write(bytes);
            output.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        lastWritten = text;
    }

    private static final class Document {
        int version = 1;
        List<Progress> players;
        Document(List<Progress> players) { this.players = players; }
    }

    public static final class Progress {
        String uuid, name;
        double coins, experience, grindedCoins;
        int prestige, renown, kills, assists, deaths, highestStreaks;
        Map<String, PerkData> bought, unlocked;
        Map<Integer, PerkData> chosen;
        cn.charlotte.pit.data.sub.EconomyData economyData;

        public static Progress capture(PlayerProfile profile) {
            Progress result = new Progress();
            result.uuid = profile.getUuid(); result.name = profile.getPlayerName();
            result.coins = profile.getCoins(); result.experience = profile.getExperience();
            result.grindedCoins = profile.getGrindedCoins(); result.prestige = profile.getPrestige(); result.renown = profile.getRenown();
            result.kills = profile.getKills(); result.assists = profile.getAssists(); result.deaths = profile.getDeaths();
            result.highestStreaks = profile.getHighestStreaks();
            result.economyData = profile.getEconomyData().copy();
            result.bought = copy(profile.getBoughtPerkMap()); result.unlocked = copy(profile.getUnlockedPerkMap());
            result.chosen = copy(profile.getChosePerk());
            result.validate();
            return result;
        }

        public void apply(PlayerProfile profile) {
            validate();
            if (!uuid.equals(profile.getUuid())) throw new IllegalArgumentException("Progress belongs to another identity");
            profile.setLoaded(false);
            profile.setPrestige(prestige); profile.setExperience(experience); profile.setCoins(coins);
            profile.setGrindedCoins(grindedCoins); profile.setRenown(renown);
            profile.setEconomyData(economyData);
            profile.setKills(kills); profile.setAssists(assists); profile.setDeaths(deaths); profile.setHighestStreaks(highestStreaks);
            profile.setBoughtPerkMap(copy(bought)); profile.setUnlockedPerkMap(copy(unlocked)); profile.setChosePerk(copy(chosen));
        }

        private void validate() {
            UUID.fromString(uuid);
            if (name == null || !name.matches("[A-Za-z0-9_]{3,16}") || !finite(coins) || !finite(experience) || !finite(grindedCoins)
                    || prestige < 0 || renown < 0 || kills < 0 || assists < 0 || deaths < 0 || bought == null || unlocked == null || chosen == null)
                throw new IllegalArgumentException("Invalid progress values");
            for (Map<?, PerkData> map : Arrays.asList(bought, unlocked, chosen)) {
                if (map.size() > 256) throw new IllegalArgumentException("Invalid perk count");
                for (PerkData perk : map.values()) if (perk == null || perk.getPerkInternalName() == null || perk.getLevel() < 0)
                    throw new IllegalArgumentException("Invalid perk");
            }
        }
        private static boolean finite(double value) { return Double.isFinite(value) && value >= 0; }
        private static <K> Map<K, PerkData> copy(Map<K, PerkData> source) {
            Map<K, PerkData> result = new LinkedHashMap<>();
            source.forEach((key, value) -> { if (value != null) result.put(key, new PerkData(value.getPerkInternalName(), value.getLevel())); });
            return result;
        }
    }
}
