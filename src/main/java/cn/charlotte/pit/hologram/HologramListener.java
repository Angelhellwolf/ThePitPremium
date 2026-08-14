package cn.charlotte.pit.hologram;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.hologram.Hologram;
import cn.charlotte.pit.util.hologram.HologramAPI;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/4 13:09
 */
@AutoRegister
public class HologramListener implements Listener {
    public static final Map<UUID, PlayerHologram> hologramMap = new ConcurrentHashMap<>();

    @EventHandler
    @SneakyThrows
    public void onLoad(PitProfileLoadedEvent event) {
        Bukkit.getScheduler()
                .runTask(ThePit.getInstance(), () -> {
                    Player player = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
                    if (player != null && player.isOnline()) {
                        PlayerHologram old = hologramMap.remove(player.getUniqueId());
                        despawnPlayerHolograms(old);

                        PlayerHologram playerHologram = new PlayerHologram(new ArrayList<>());
                        for (AbstractHologram hologram : (ThePit.getInstance().getHologramFactory()).loopHologram) {
                            handleHologramCreate(player, playerHologram, hologram);
                        }
                        for (AbstractHologram hologram : (ThePit.getInstance().getHologramFactory()).normalHologram) {
                            handleHologramCreate(player, playerHologram, hologram);
                        }
                        hologramMap.put(player.getUniqueId(), playerHologram);
                    }
                });
    }

    private void handleHologramCreate(Player player, PlayerHologram playerHologram, AbstractHologram hologram) {
        List<Hologram> holograms = Collections.synchronizedList(new ArrayList<>());
        List<String> text = hologram.getText(player);
        if (text == null) {
            text = Collections.emptyList();
        }
        for (int i = 0; i < text.size(); i++) {
            String line = text.get(i);
            Hologram holo = HologramAPI.createHologram(hologram.getLocation().clone().add(0.0D, -i * hologram.getHologramHighInterval(), 0.0D), CC.translate(line));
            holo.spawn(Collections.singletonList(player));
            holograms.add(holo);
        }
        playerHologram.hologramData.put(hologram.getInternalName(), new HologramData(holograms, hologram.getInternalName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler()
                .runTask(ThePit.getInstance(), () -> {
                    PlayerHologram playerHologram = hologramMap.remove(event.getPlayer().getUniqueId());
                    despawnPlayerHolograms(playerHologram);
                });
    }

    public static void despawnPlayerHolograms(PlayerHologram playerHologram) {
        if (playerHologram == null) {
            return;
        }

        for (HologramData datum : playerHologram.hologramData.values()) {
            List<Hologram> holograms = datum.holograms;
            if (holograms == null) {
                continue;
            }
            synchronized (holograms) {
                for (Hologram hologram : holograms) {
                    try {
                        if (hologram.isSpawned()) {
                            hologram.deSpawn();
                        }
                    } catch (Exception ignored) {
                    }
                }
                holograms.clear();
            }
        }
    }

    @Getter
    public static class PlayerHologram {
        private final Map<String, HologramListener.HologramData> hologramData = new ConcurrentHashMap<>();

        public PlayerHologram(List<HologramListener.HologramData> hologramData) {
            for (HologramListener.HologramData data : hologramData) {
                this.hologramData.put(data.internalName, data);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof PlayerHologram)) {
                return false;
            }
            PlayerHologram other = (PlayerHologram)o;
            if (!other.canEqual(this)) {
                return false;
            }
            Object this$hologramData = getHologramData();
            Object other$hologramData = other.getHologramData();
            return Objects.equals(this$hologramData, other$hologramData);
        }

        protected boolean canEqual(Object other) {
            return other instanceof PlayerHologram;
        }

        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $hologramData = getHologramData();
            result = result * 59 + (($hologramData == null) ? 43 : $hologramData.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "HologramListener.PlayerHologram(hologramData=" + getHologramData() + ")";
        }
    }

    @Setter
    @Getter
    public static class HologramData {
        private List<Hologram> holograms;

        private String internalName;

        public HologramData(List<Hologram> holograms, String internalName) {
            this.holograms = holograms;
            this.internalName = internalName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof HologramData)) {
                return false;
            }
            HologramData other = (HologramData)o;
            if (!other.canEqual(this)) {
                return false;
            }
            Object this$holograms = getHolograms();
            Object other$holograms = other.getHolograms();
            if (!Objects.equals(this$holograms, other$holograms)) {
                return false;
            }
            Object this$internalName = getInternalName();
            Object other$internalName = other.getInternalName();
            return Objects.equals(this$internalName, other$internalName);
        }

        protected boolean canEqual(Object other) {
            return other instanceof HologramData;
        }

        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $holograms = getHolograms();
            result = result * 59 + (($holograms == null) ? 43 : $holograms.hashCode());
            Object $internalName = getInternalName();
            result = result * 59 + (($internalName == null) ? 43 : $internalName.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "HologramListener.HologramData(holograms=" + getHolograms() + ", internalName=" + getInternalName() + ")";
        }
    }
}
