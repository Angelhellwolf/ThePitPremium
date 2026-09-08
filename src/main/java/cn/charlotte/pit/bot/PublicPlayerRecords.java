package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Used only by the identity I/O worker; checks this server's Mongo records and login cache. */
final class PublicPlayerRecords implements PublicPlayerIdentityStore.Records {
    private final ThePit plugin;
    private final Map<String, UUID> cachedNames = new HashMap<>();
    private final Map<String, Boolean> nameResults = new HashMap<>();

    PublicPlayerRecords(ThePit plugin, Map<String, UUID> onlineNames) throws IOException {
        this.plugin = plugin;
        Path cache = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent().resolve("usercache.json");
        if (Files.exists(cache)) {
            try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
                for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
                    String name = element.getAsJsonObject().get("name").getAsString();
                    UUID uuid = UUID.fromString(element.getAsJsonObject().get("uuid").getAsString());
                    cachedNames.put(name.toLowerCase(Locale.ROOT), uuid);
                }
            } catch (RuntimeException exception) { throw new IOException("Invalid server login cache", exception); }
        }
        onlineNames.forEach((name, id) -> cachedNames.put(name.toLowerCase(Locale.ROOT), id));
    }

    @Override public boolean nameOccupied(String name, UUID ownId) {
        UUID cached = cachedNames.get(name.toLowerCase(Locale.ROOT));
        if (cached != null && !cached.equals(ownId)) return true;
        return nameResults.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> plugin.getMongoDB().getCollection()
                .find(Filters.or(Filters.eq("lowerName", key), Filters.regex("playerName", "^" + Pattern.quote(name) + "$", "i")))
                .projection(new Document("uuid", 1)).limit(1).first() != null);
    }

    @Override public boolean uuidOccupied(UUID id) {
        return plugin.getMongoDB().getCollection().find(Filters.eq("uuid", id.toString()))
                .projection(new Document("uuid", 1)).limit(1).first() != null;
    }
}
