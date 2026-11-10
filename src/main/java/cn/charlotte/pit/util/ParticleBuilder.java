package cn.charlotte.pit.util;

import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class ParticleBuilder {
    private static final double DEFAULT_VIEW_DISTANCE_SQUARED = 48.0D * 48.0D;

    Location location;
    EnumParticle particle;
    float offsetX;
    float offsetY;
    float offsetZ;
    float velocity;
    int count;

    public ParticleBuilder(Location location, EnumParticle particle) {
        this.location = location;
        this.particle = particle;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
        this.offsetZ = 0.0f;
        this.velocity = 0.0f;
        this.count = 1;
    }

    public void play() {
        PacketPlayOutWorldParticles pwp = new PacketPlayOutWorldParticles(this.particle, true, (float)this.location.getX(), (float)this.location.getY(), (float)this.location.getZ(), this.offsetX, this.offsetY, this.offsetZ, this.velocity, this.count, new int[0]);
        Bukkit.getOnlinePlayers().stream()
                .filter(this::isVisibleTo)
                .forEach(p -> ((CraftPlayer)p).getHandle().playerConnection.sendPacket(pwp));
    }

    public ParticleBuilder setOffsetX(float offsetX) {
        this.offsetX = offsetX;
        return this;
    }

    public ParticleBuilder setOffsetY(float offsetY) {
        this.offsetY = offsetY;
        return this;
    }

    public ParticleBuilder setOffsetZ(float offsetZ) {
        this.offsetZ = offsetZ;
        return this;
    }

    public ParticleBuilder setVelocity(float velocity) {
        this.velocity = velocity;
        return this;
    }

    public ParticleBuilder setCount(int count) {
        this.count = count;
        return this;
    }

    public static void playColored(Location loc, Color color, EnumParticle ep) {
        PacketPlayOutWorldParticles p1 = new PacketPlayOutWorldParticles(ep, true, (float)loc.getX(), (float)loc.getY(), (float)loc.getZ(), (float)color.getRed() / 255.0f, (float)color.getGreen() / 255.0f, (float)color.getBlue() / 255.0f, 1.0f, 0, new int[0]);
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> isVisibleTo(player, loc))
                .forEach(p -> ((CraftPlayer)p).getHandle().playerConnection.sendPacket((Packet)p1));
    }

    private boolean isVisibleTo(Player player) {
        return isVisibleTo(player, this.location);
    }

    private static boolean isVisibleTo(Player player, Location origin) {
        return player != null
                && origin != null
                && origin.getWorld() != null
                && origin.getWorld().equals(player.getWorld())
                && player.getLocation().distanceSquared(origin) <= DEFAULT_VIEW_DISTANCE_SQUARED;
    }

    public static void playColoredDust(Location loc, Color color) {
        ParticleBuilder.playColored(loc, color, EnumParticle.REDSTONE);
    }

    public static void playColoredSpell(Location loc, Color color) {
        ParticleBuilder.playColored(loc, color, EnumParticle.SPELL_MOB);
    }
}
