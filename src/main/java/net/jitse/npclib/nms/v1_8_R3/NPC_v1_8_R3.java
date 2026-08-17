/*
 * Copyright (c) 2018 Jitse Boonstra
 */

package net.jitse.npclib.nms.v1_8_R3;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.jitse.npclib.NPCLib;
import net.jitse.npclib.api.skin.Skin;
import net.jitse.npclib.api.state.NPCAnimation;
import net.jitse.npclib.api.state.NPCSlot;
import net.jitse.npclib.hologram.Hologram;
import net.jitse.npclib.internal.MinecraftVersion;
import net.jitse.npclib.internal.NPCBase;
import net.jitse.npclib.nms.v1_8_R3.packets.*;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Jitse Boonstra
 */
public class NPC_v1_8_R3 extends NPCBase {

    private static final long TAB_LIST_REMOVE_DELAY_TICKS = 20L;
    private static final long TAB_LIST_REMOVE_RETRY_TICKS = 100L;
    private static final AtomicBoolean PACKET_EVENTS_WARNING_LOGGED = new AtomicBoolean();

    private PacketPlayOutNamedEntitySpawn packetPlayOutNamedEntitySpawn;
    private PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeamRegister;
    private PacketPlayOutPlayerInfo packetPlayOutPlayerInfoAdd, packetPlayOutPlayerInfoRemove;
    private PacketPlayOutEntityHeadRotation packetPlayOutEntityHeadRotation;
    private PacketPlayOutEntityDestroy packetPlayOutEntityDestroy;

    public NPC_v1_8_R3(NPCLib instance, List<String> lines) {
        super(instance, lines);
    }

    @Override
    public Hologram getHologram(Player player) {
        Hologram holo = super.getHologram(player);
        if (holo == null) {
            holo = new Hologram(MinecraftVersion.V1_8_R3, location.clone().add(0, 0.5, 0), getText(player));
        }
        super.playerHologram.put(player.getUniqueId(), holo);
        return holo;
    }

    @Override
    public void createPackets() {
        Bukkit.getOnlinePlayers().forEach(this::createPackets);
    }

    @Override
    public void createPackets(Player player) {

        PacketPlayOutPlayerInfoWrapper packetPlayOutPlayerInfoWrapper = new PacketPlayOutPlayerInfoWrapper();

        // Packets for spawning the NPC:
        this.packetPlayOutScoreboardTeamRegister = new PacketPlayOutScoreboardTeamWrapper()
                .createRegisterTeam(name); // First packet to send.

        this.packetPlayOutPlayerInfoAdd = packetPlayOutPlayerInfoWrapper
                .create(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, gameProfile, name); // Second packet to send.

        this.packetPlayOutNamedEntitySpawn = new PacketPlayOutNamedEntitySpawnWrapper()
                .create(uuid, location, entityId); // Third packet to send.

        this.packetPlayOutEntityHeadRotation = new PacketPlayOutEntityHeadRotationWrapper()
                .create(location, entityId); // Fourth packet to send.

        this.packetPlayOutPlayerInfoRemove = packetPlayOutPlayerInfoWrapper
                .create(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, gameProfile, name); // Fifth packet to send (delayed).

        // Packet for destroying the NPC:
        this.packetPlayOutEntityDestroy = new PacketPlayOutEntityDestroy(entityId); // First packet to send.
    }

    private static void sendPacketSafe(PlayerConnection conn, Packet<?> packet) {
        try {
            conn.sendPacket(packet);
        } catch (Exception ignored) {
            // Prevent cross-version packet translation errors from kicking players
        }
    }

    private void scheduleTabListRemoval(Player player) {
        UUID viewerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(instance.getPlugin(),
                () -> removeFromTabList(viewerUuid), TAB_LIST_REMOVE_DELAY_TICKS);
        Bukkit.getScheduler().runTaskLater(instance.getPlugin(),
                () -> removeFromTabList(viewerUuid), TAB_LIST_REMOVE_RETRY_TICKS);
    }

    private void removeFromTabList(UUID viewerUuid) {
        Player player = Bukkit.getPlayer(viewerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (sendModernTabListRemove(player)) {
            return;
        }

        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;
        sendPacketSafe(playerConnection, packetPlayOutPlayerInfoRemove);
    }

    private boolean sendModernTabListRemove(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            return false;
        }

        try {
            ClientVersion clientVersion = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            if (clientVersion == null || clientVersion.isOlderThan(ClientVersion.V_1_19_3)) {
                return false;
            }

            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    player, new WrapperPlayServerPlayerInfoRemove(uuid));
            return true;
        } catch (RuntimeException | LinkageError exception) {
            if (PACKET_EVENTS_WARNING_LOGGED.compareAndSet(false, true)) {
                Bukkit.getLogger().warning("[ThePitPremium] PacketEvents NPC tab cleanup failed; "
                        + "falling back to the legacy PlayerInfo packet: " + exception.getMessage());
            }
            return false;
        }
    }

    @Override
    public void sendShowPackets(Player player) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;

        if (hasTeamRegistered.add(player.getUniqueId()))
            sendPacketSafe(playerConnection, packetPlayOutScoreboardTeamRegister);
        sendPacketSafe(playerConnection, packetPlayOutPlayerInfoAdd);
        sendPacketSafe(playerConnection, packetPlayOutNamedEntitySpawn);
        sendPacketSafe(playerConnection, packetPlayOutEntityHeadRotation);

        getHologram(player).show(player);

        // Keep the profile briefly for skin loading, then remove it from the viewer's tab list.
        scheduleTabListRemoval(player);
    }

    @Override
    public void sendHidePackets(Player player) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;

        sendPacketSafe(playerConnection, packetPlayOutEntityDestroy);
        removeFromTabList(player.getUniqueId());

        getHologram(player).hide(player);
    }

    @Override
    public void sendMetadataPacket(Player player) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;
        PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadataWrapper().create(activeStates, entityId);

        sendPacketSafe(playerConnection, packet);
    }

    @Override
    public void sendEquipmentPacket(Player player, NPCSlot slot, boolean auto) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;

        if (slot == NPCSlot.OFFHAND) {
            if (!auto) {
                throw new UnsupportedOperationException("Offhand is not supported on servers below 1.9");
            }
            return;
        }

        ItemStack item = getItem(slot);

        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(entityId, slot.getSlot(), CraftItemStack.asNMSCopy(item));
        sendPacketSafe(playerConnection, packet);
    }

    @Override
    public void sendAnimationPacket(Player player, NPCAnimation animation) {
        if (animation == NPCAnimation.SWING_OFFHAND) {
            throw new IllegalArgumentException("Offhand Swing Animations are only available on 1.9 and up.");
        }

        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;

        PacketPlayOutAnimation packet = new PacketPlayOutAnimationWrapper().create(animation, entityId);
        sendPacketSafe(playerConnection, packet);
    }

    @Override
    public void updateSkin(Skin skin) {
        GameProfile newProfile = new GameProfile(uuid, name);
        newProfile.getProperties().get("textures").clear();
        newProfile.getProperties().put("textures", new Property("textures", skin.getValue(), skin.getSignature()));
        this.packetPlayOutPlayerInfoAdd = new PacketPlayOutPlayerInfoWrapper().create(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, newProfile, name);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;
            removeFromTabList(player.getUniqueId());
            sendPacketSafe(playerConnection, packetPlayOutEntityDestroy);
            sendPacketSafe(playerConnection, packetPlayOutPlayerInfoAdd);
            sendPacketSafe(playerConnection, packetPlayOutNamedEntitySpawn);
            scheduleTabListRemoval(player);
        }
    }

    @Override
    public void sendHeadRotationPackets(Location location) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;

            Location npcLocation = getLocation();
            Vector dirBetweenLocations = location.toVector().subtract(npcLocation.toVector());

            npcLocation.setDirection(dirBetweenLocations);

            float yaw = npcLocation.getYaw();
            float pitch = npcLocation.getPitch();

            sendPacketSafe(connection, new PacketPlayOutEntity.PacketPlayOutEntityLook(getEntityId(), (byte) ((yaw % 360.) * 256 / 360), (byte) ((pitch % 360.) * 256 / 360), false));
            sendPacketSafe(connection, new PacketPlayOutEntityHeadRotationWrapper().create(npcLocation, entityId));
        }
    }
}
