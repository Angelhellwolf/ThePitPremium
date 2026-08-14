package cn.charlotte.pit.util.bossbar;

import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBar {
    private final ConcurrentHashMap<UUID, EntityWither> withers = new ConcurrentHashMap<>();
    private volatile String title;

    public BossBar(String title) {
        this.title = title;
    }

    private static void sendPacketSafe(Player player, Packet<?> packet) {
        try {
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        } catch (Exception e) {
            // Silently ignore to prevent kicking cross-version clients
        }
    }

    public void addPlayer(Player player) {
        if (withers.containsKey(player.getUniqueId())) {
            this.removePlayer(player);
        }
        EntityWither wither = new EntityWither(
                ((CraftWorld) player.getWorld()).getHandle());
        Location location = getWitherLocation(player.getLocation());
        wither.setCustomName(title);
        wither.setInvisible(true);
        // Ensure health is valid before spawning to prevent ViaVersion NaN crash
        float maxHealth = wither.getMaxHealth();
        if (maxHealth <= 0 || Float.isNaN(maxHealth)) {
            maxHealth = 300.0F;
        }
        wither.setHealth(maxHealth);
        wither.setLocation(location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
        PacketPlayOutSpawnEntityLiving packet = new PacketPlayOutSpawnEntityLiving(wither);
        sendPacketSafe(player, packet);
        withers.put(player.getUniqueId(), wither);
    }

    public void removePlayer(Player player) {
        EntityWither wither = withers.remove(player.getUniqueId());
        if (wither == null) return;
        PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(wither.getId());
        sendPacketSafe(player, packet);
    }

    public void setProgress(double progress) {
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            progress = 0.0;
        }
        progress = Math.max(0.0, Math.min(1.0, progress));

        for (Map.Entry<UUID, EntityWither> entry : withers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                EntityWither entityWither = entry.getValue();
                synchronized (entityWither) {
                    float maxHealth = entityWither.getMaxHealth();
                    if (maxHealth <= 0 || Float.isNaN(maxHealth)) {
                        maxHealth = 300.0F;
                    }
                    float health = (float) (progress * maxHealth);
                    if (Float.isNaN(health) || health < 0.5F) {
                        health = 0.5F;
                    } else if (health > maxHealth) {
                        health = maxHealth;
                    }
                    entityWither.setHealth(health);
                    PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(
                            entityWither.getId(), entityWither.getDataWatcher(), true);
                    sendPacketSafe(player, packet);
                }
            }
        }
    }

    public void update() {
        for (Map.Entry<UUID, EntityWither> entry : this.withers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                EntityWither entityWither = entry.getValue();
                synchronized (entityWither) {
                    Location location = getWitherLocation(player.getLocation());
                    entityWither.setLocation(location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
                    PacketPlayOutEntityTeleport packet = new PacketPlayOutEntityTeleport(entityWither);
                    sendPacketSafe(player, packet);
                }
            }
        }
    }

    public void update(Player player) {
        EntityWither wither = this.withers.get(player.getUniqueId());
        if (wither == null) {
            return;
        }
        synchronized (wither) {
            Location location = getWitherLocation(player.getLocation());
            wither.setLocation(location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
            PacketPlayOutEntityTeleport packet = new PacketPlayOutEntityTeleport(wither);
            sendPacketSafe(player, packet);
        }
    }

    public Location getWitherLocation(Location location) {
        return location.clone().add(location.getDirection().multiply(60));
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        if (this.title.equals(title)) return;
        this.title = title;
        for (Map.Entry<UUID, EntityWither> entry : withers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                EntityWither entityWither = entry.getValue();
                synchronized (entityWither) {
                    entityWither.setCustomName(title);
                    sanitizeWitherHealth(entityWither);
                    PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(
                            entityWither.getId(), entityWither.getDataWatcher(), true);
                    sendPacketSafe(player, packet);
                }
            }
        }
    }

    private void sanitizeWitherHealth(EntityWither wither) {
        float health = wither.getHealth();
        float maxHealth = wither.getMaxHealth();
        if (maxHealth <= 0 || Float.isNaN(maxHealth)) {
            maxHealth = 300.0F;
        }
        if (Float.isNaN(health) || health < 0.5F) {
            wither.setHealth(0.5F);
        } else if (health > maxHealth) {
            wither.setHealth(maxHealth);
        }
    }

    public ConcurrentHashMap<UUID, EntityWither> getWithers() {
        return this.withers;
    }
}
