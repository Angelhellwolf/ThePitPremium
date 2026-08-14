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
                HologramListener.HologramData hologramData = data.get(abstractHologram.getInternalName());
                if (hologramData == null) {
                    continue;
                }

                int loopTicks = abstractHologram.loopTicks();
                if (loopTicks <= 0 || this.tick % loopTicks != 0L) {
                    continue;
                }

                List<String> text = abstractHologram.getText(player);
                if (text == null) {
                    text = Collections.emptyList();
                }

                List<Hologram> holograms = hologramData.getHolograms();
                synchronized (holograms) {
                    for (int i = 0; i < text.size(); i++) {
                        Location lineLocation = abstractHologram.getLocation().clone().add(0.0D, -i * abstractHologram.getHologramHighInterval(), 0.0D);
                        String translatedText = CC.translate(text.get(i));
                        if (holograms.size() <= i) {
                            Hologram holo = HologramAPI.createHologram(lineLocation, translatedText);
                            holo.spawn(Collections.singletonList(player));
                            holograms.add(holo);
                        } else {
                            holograms.get(i).setLocation(lineLocation);
                        }
                        holograms.get(i).setText(translatedText);
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
}
