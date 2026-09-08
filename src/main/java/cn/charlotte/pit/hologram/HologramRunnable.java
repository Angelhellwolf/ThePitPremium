package cn.charlotte.pit.hologram;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.hologram.Hologram;
import cn.charlotte.pit.util.hologram.HologramAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/4 13:25
 */
public class HologramRunnable extends BukkitRunnable {
    private long tick = 0L;

    public HologramRunnable() {
        this.runTaskTimer(ThePit.getInstance(), 4L, 4L);
    }

    @Override
    public void run() {
        HologramListener.hologramMap.entrySet().removeIf(entry -> {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner != null && owner.isOnline()) {
                return false;
            }
            HologramListener.despawnPlayerHolograms(entry.getValue());
            return true;
        });

        for (Player player : Bukkit.getOnlinePlayers()) {
            HologramListener.PlayerHologram hologram = HologramListener.hologramMap.get(player.getUniqueId());
            if (hologram == null) {
                continue;
            }
            Map<String, HologramListener.HologramData> data = hologram.getHologramData();
            for (AbstractHologram abstractHologram : (ThePit.getInstance().getHologramFactory()).loopHologram) {
                String internalName = abstractHologram.getInternalName();
                HologramListener.HologramData hologramData = data.get(internalName);
                if (hologramData == null) {
                    continue;
                }

                int loopTicks = abstractHologram.loopTicks();
                if (loopTicks <= 0) {
                    continue;
                }

                // This runnable itself runs once every four server ticks. Spread each
                // player's holograms across the whole refresh window instead of moving
                // and formatting every player's lines in one periodic spike.
                int phase = Math.floorMod(31 * player.getUniqueId().hashCode() + internalName.hashCode(), loopTicks);
                if (Math.floorMod(this.tick, loopTicks) != phase) {
                    continue;
                }

                List<String> text = abstractHologram.getText(player);
                if (text == null) {
                    text = Collections.emptyList();
                }

                Location baseLocation = abstractHologram.getLocation();
                List<Hologram> holograms = hologramData.getHolograms();
                synchronized (holograms) {
                    for (int i = 0; i < text.size(); i++) {
                        Location lineLocation = baseLocation.clone().add(0.0D, -i * abstractHologram.getHologramHighInterval(), 0.0D);
                        String translatedText = CC.translate(text.get(i));
                        if (holograms.size() <= i) {
                            Hologram holo = HologramAPI.createHologram(lineLocation, translatedText);
                            holo.spawn(Collections.singletonList(player));
                            holograms.add(holo);
                        } else {
                            Hologram holo = holograms.get(i);
                            if (!samePosition(holo.getLocation(), lineLocation)) {
                                holo.setLocation(lineLocation);
                            }
                            if (!Objects.equals(holo.getText(), translatedText)) {
                                holo.setText(translatedText);
                            }
                        }
                    }

                    for (int i = holograms.size() - 1; i >= text.size(); i--) {
                        Hologram extra = holograms.remove(i);
                        try {
                            if (extra.isSpawned()) {
                                extra.deSpawn();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        this.tick++;
    }

    private static boolean samePosition(Location first, Location second) {
        return first != null
                && second != null
                && Objects.equals(first.getWorld(), second.getWorld())
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }
}
