package cn.charlotte.pit.junxu;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class JunxuSettingsStore {
    private final Path file;
    private final double maximumHealth;
    private final Map<UUID, JunxuTrainingSettings> settings = new HashMap<>();
    public JunxuSettingsStore(Path file, double maximumHealth) throws IOException {
        this.file = file; this.maximumHealth = maximumHealth;
        if (!Files.exists(file)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            if (yaml.getConfigurationSection("owners") == null) return;
            for (String key : yaml.getConfigurationSection("owners").getKeys(false)) {
                String path = "owners." + key + ".";
                JunxuTrainingSettings value = new JunxuTrainingSettings(yaml.getDouble(path + "health", 20),
                        JunxuTrainingSettings.Armor.valueOf(yaml.getString(path + "armor", "STANDARD")),
                        JunxuTrainingSettings.Weapon.valueOf(yaml.getString(path + "weapon", "IRON")),
                        JunxuTrainingSettings.Mode.valueOf(yaml.getString(path + "mode", "HOSTILE")),
                        JunxuTrainingSettings.Route.valueOf(yaml.getString(path + "route", "FORMATION")),
                        JunxuTrainingSettings.Strength.valueOf(yaml.getString(path + "strength", "NORMAL")),yaml.getBoolean(path+"paused",false));
                if (value.health > maximumHealth) value = value.health(maximumHealth);
                settings.put(UUID.fromString(key), value);
            }
        } catch (Exception error) { throw new IOException("Invalid Junxu training settings", error); }
    }
    public JunxuTrainingSettings get(UUID id) { return settings.getOrDefault(id, JunxuTrainingSettings.defaults()); }
    public double maximumHealth() { return maximumHealth; }
    public void put(UUID id, JunxuTrainingSettings value) throws IOException {
        if (value.health > maximumHealth) throw new IllegalArgumentException("Health exceeds configured limit");
        JunxuTrainingSettings old = settings.put(id, value);
        try { save(); } catch (IOException error) { if (old == null) settings.remove(id); else settings.put(id, old); throw error; }
    }
    private void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID,JunxuTrainingSettings> entry : settings.entrySet()) {
            String path = "owners." + entry.getKey() + "."; JunxuTrainingSettings value = entry.getValue();
            yaml.set(path + "health", value.health); yaml.set(path + "armor", value.armor.name());
            yaml.set(path + "weapon", value.weapon.name()); yaml.set(path + "mode", value.mode.name());
            yaml.set(path+"route",value.route.name());yaml.set(path+"strength",value.strength.name());yaml.set(path+"paused",value.paused);
        }
        Files.createDirectories(file.getParent()); Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes); channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
