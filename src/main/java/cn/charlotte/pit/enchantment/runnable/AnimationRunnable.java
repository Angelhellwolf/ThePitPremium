package cn.charlotte.pit.enchantment.runnable;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.enchantment.menu.MythicWellMenu;
import cn.charlotte.pit.util.menu.Menu;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: EmptyIrony
 * @Date: 2021/2/11 21:10
 */
@Getter
public class AnimationRunnable extends BukkitRunnable {
    private final Map<UUID, AnimationData> animations = new ConcurrentHashMap<>();
    private final List<Location> animationLocations;

    public AnimationRunnable() {
        final PitConfig pitConfig = ThePit.getInstance().getPitConfig();
        final Location loc = pitConfig.getEnchantLocation();
        Location anchor = loc == null ? new Location(Bukkit.getWorlds().get(0), 0, 1, 0) : loc;
        this.animationLocations = ringBelow(resolveTable(anchor));

        this.runTaskTimer(ThePit.getInstance(), 20, 2);

    }

    @Override
    public void run() {
        for (Map.Entry<UUID, AnimationData> entry : animations.entrySet()) {
            AnimationData animationData = entry.getValue();
            if (!animationData.getPlayer().isOnline()
                    || !(Menu.currentlyOpenedMenus.get(animationData.getPlayer().getName()) instanceof MythicWellMenu)) {
                if (animationData.getPlayer().isOnline()) sendReset(animationData.getPlayer());
                animations.remove(entry.getKey(), animationData);
            }
        }

        for (AnimationData data : animations.values()) {
            final List<Location> animationLocations = data.locations;
            Menu menu = Menu.currentlyOpenedMenus.get(data.getPlayer().getName());

            if (data.isFinished()) {
                if (!data.isFinishedBlocksSent()) {
                    for (Location location : animationLocations) {
                        data.player.sendBlockChange(location, Material.STAINED_GLASS, data.color);
                    }
                    data.setFinishedBlocksSent(true);
                }
                continue;
            }

            if (data.isStartEnchanting()) {
                Player player = data.getPlayer();

                if (data.animationTick == 0 || data.animationTick == 1 || data.animationTick == 4 || data.animationTick == 5) {
                    for (Location location : animationLocations) {
                        player.sendBlockChange(location, Material.STAINED_GLASS, data.color);
                    }
                    player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 1.2F);
                } else if (data.animationTick == 2 || data.animationTick == 3 || data.animationTick == 6 || data.animationTick == 7) {
                    for (Location location : animationLocations) {
                        player.sendBlockChange(location, Material.STAINED_GLASS, (byte) 0);
                    }
                    player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 1.2F);
                } else if (data.animationTick <= 23) {
                    int i = data.animationTick % 8;
                    Location location = animationLocations.get(i);
                    if (i == 0) {
                        player.sendBlockChange(animationLocations.get(7), Material.STAINED_GLASS, (byte) 0);
                    } else {
                        player.sendBlockChange(animationLocations.get(i - 1), Material.STAINED_GLASS, (byte) 0);
                    }
                    player.playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1, 0.3F + i * 0.5F);
                    player.sendBlockChange(location, Material.STAINED_GLASS, data.color);
                } else {
                    data.finished = true;
                    menu.updateMenu(player);
                    continue;
                }

                menu.updateMenu(data.player);
            } else {
                if (data.animationTick > 14) {
                    data.animationTick = 0;
                }

                if (data.animationTick % 2 != 0) {
                    data.animationTick++;
                    continue;
                }

                int realTick = data.animationTick / 2;

                // Refresh only changed slots instead of clearing and resending the inventory.
                menu.updateMenu(data.getPlayer());

                Location location = animationLocations.get(realTick);
                if (data.animationTick == 0) {
                    data.player.sendBlockChange(animationLocations.get(7), Material.STAINED_GLASS, (byte) 0);
                } else {
                    data.player.sendBlockChange(animationLocations.get(realTick - 1), Material.STAINED_GLASS, (byte) 0);
                }
                data.player.sendBlockChange(location, Material.STAINED_GLASS, data.color);
            }
            data.animationTick++;
        }
    }

    public void sendReset(Player player) {
        AnimationData data = animations.get(player.getUniqueId());
        for (Location location : data == null ? locations(player) : data.locations) {
            if (player.getWorld() == location.getWorld())
                player.sendBlockChange(location, location.getBlock().getType(), location.getBlock().getData());
        }
    }

    public void sendStart(Player player) {
        for (Location location : locations(player)) {
            if (player.getWorld() != location.getWorld()) continue;
            player.sendBlockChange(location, Material.STAINED_GLASS, (byte) 0);
        }
    }

    private List<Location> locations(Player player) {
        cn.charlotte.pit.junxu.JunxuManager manager = ThePit.getInstance().getJunxuManager();
        cn.charlotte.pit.junxu.JunxuRoom room = manager == null ? null : manager.at(player.getLocation());
        if (room == null) return animationLocations;
        Location first = room.at(14, 24, 13, 0), second = room.at(18, 24, 13, 0);
        Location nearest = first.distanceSquared(player.getLocation()) < second.distanceSquared(player.getLocation()) ? first : second;
        return ringBelow(resolveTable(nearest));
    }

    /** The configured anchor may be above the actual block for the floating item display. */
    static Location resolveTable(Location anchor) {
        Location found = null;
        double distance = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) for (int y = -3; y <= 1; y++) for (int z = -2; z <= 2; z++) {
            Location candidate = anchor.getBlock().getLocation().add(x, y, z);
            if (candidate.getBlock().getType() != Material.ENCHANTMENT_TABLE) continue;
            double here = candidate.distanceSquared(anchor);
            if (here < distance) { distance = here; found = candidate; }
        }
        return found == null ? anchor.getBlock().getLocation().add(0, -1, 0) : found;
    }

    static List<Location> ringBelow(Location table) {
        Location center = new Location(table.getWorld(), table.getBlockX(), table.getBlockY() - 1, table.getBlockZ());
        List<Location> result = new ArrayList<>();
        for (int[] offset : new int[][]{{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}})
            result.add(center.clone().add(offset[0], 0, offset[1]));
        return result;
    }

    @Getter
    @Setter
    public static class AnimationData {
        private final Player player;
        private final List<Location> locations;
        private int animationTick = 0;
        private byte color = (byte) 6;
        private boolean startEnchanting = false;
        private boolean finished = false;
        private boolean finishedBlocksSent = false;

        public AnimationData(Player player) {
            this.player = player;
            this.locations = MythicWellMenu.runnable.locations(player);
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
            if (!finished) {
                this.finishedBlocksSent = false;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AnimationData data = (AnimationData) o;
            return Objects.equals(player.getUniqueId(), data.player.getUniqueId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(player.getUniqueId());
        }
    }
}
