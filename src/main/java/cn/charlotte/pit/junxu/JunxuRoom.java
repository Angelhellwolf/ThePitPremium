package cn.charlotte.pit.junxu;

import cn.charlotte.pit.supporter.SupporterTier;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

public final class JunxuRoom {
    public final UUID owner;
    public final UUID session = UUID.randomUUID();
    public final int slot;
    public final String ownerName;
    public final World world;
    public final Set<UUID> guests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public SupporterTier tier;
    public cn.charlotte.pit.supporter.SupporterAccount account;
    public long absentSince = -1, reminderDay = -1;
    public boolean warnedTen, warnedOne;
    public volatile boolean open = true;

    public JunxuRoom(UUID owner, String ownerName, int slot, World world, SupporterTier tier) {
        this.owner = owner; this.ownerName = ownerName; this.slot = slot; this.world = world; this.tier = tier;
    }
    public static int originX(int slot) { return slot % 128 * 512; }
    public static int originZ(int slot) { return slot / 128 * 512; }
    public Location at(double x, double y, double z, float yaw) {
        return new Location(world, originX(slot) + x, 48 + y, originZ(slot) + z, yaw, 0);
    }
    public Location arrival() { return at(30.5, 23, 11.5, 0); }
    public boolean contains(Location at) {
        return at != null && at.getWorld() == world && at.getX() >= originX(slot) && at.getX() < originX(slot)+61
                && at.getZ() >= originZ(slot) && at.getZ() < originZ(slot)+61 && at.getY() >= 48 && at.getY() < 110;
    }
    public boolean arena(Location at) {
        return contains(at) && at.getX() >= originX(slot)+17 && at.getX() < originX(slot)+44
                && at.getZ() >= originZ(slot)+34 && at.getZ() < originZ(slot)+57 && at.getY() >= 70 && at.getY() < 79;
    }
    public boolean permits(UUID player) { return open && (owner.equals(player) || guests.contains(player)); }
}
