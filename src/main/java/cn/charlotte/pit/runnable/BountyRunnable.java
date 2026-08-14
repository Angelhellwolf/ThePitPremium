package cn.charlotte.pit.runnable;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.events.genesis.team.GenesisTeam;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.hologram.Hologram;
import cn.charlotte.pit.util.hologram.HologramAPI;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 17:33
 */
public class BountyRunnable implements Runnable {
    private static final int MAX_ACTIVE_HOLOGRAMS_PER_PLAYER = 8;
    private static final double REVIEW_DISTANCE = 48.0D;
    private static final double REVIEW_DISTANCE_SQUARED = REVIEW_DISTANCE * REVIEW_DISTANCE;
    private static final double HIDE_DISTANCE_SQUARED = 8.0D * 8.0D;
    private final Random random = new Random();
    private static final Map<UUID, AnimationData> animationDataMap = new ConcurrentHashMap<>();

    public static Map<UUID, AnimationData> getAnimationDataMap() {
        return animationDataMap;
    }

    @Override
    public void run() {
        List<UUID> shouldRemoveData = new ArrayList<>();
        for (Map.Entry<UUID, AnimationData> entry : animationDataMap.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                List<HologramDisplay> holograms = entry.getValue().holograms;
                synchronized (holograms) {
                    holograms.forEach(s -> s.hologram.deSpawn());
                    holograms.clear();
                }
                shouldRemoveData.add(entry.getKey());
            }
        }
        shouldRemoveData.forEach(animationDataMap::remove);

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if (profile.getBounty() >= 500 || profile.getBounty() < 0) {
                animationDataMap.putIfAbsent(player.getUniqueId(), new AnimationData());
                String color = "&6";
                if (ThePit.getInstance().getPitConfig().isGenesisEnable()) {
                    if (profile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                        color = "&b";
                    }
                    if (profile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                        color = "&c";
                    }
                }
                playAnimation(player, profile.getBounty(), color);
            } else {
                AnimationData animationData = animationDataMap.get(player.getUniqueId());
                if (animationData != null) {
                    List<HologramDisplay> holograms = animationData.holograms;
                    synchronized (holograms) {
                        for (HologramDisplay hologram : holograms) {
                            hologram.hologram.deSpawn();
                        }
                        holograms.clear();
                    }
                }
            }
        }
    }


    @SneakyThrows
    private void playAnimation(Player player, int bounty, String color) {
        AnimationData animationData = animationDataMap.get(player.getUniqueId());
        List<HologramDisplay> holograms = animationData.holograms;

        if (animationData.spawnCooldown.hasExpired()) {
            Location playerLocation = player.getLocation();
            double x = generatorLocDouble();
            double z = generatorLocDouble();
            List<Player> reviewers = new ArrayList<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target == null || !target.isOnline()) {
                    continue;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (!target.getWorld().equals(player.getWorld())) {
                    continue;
                }
                double distanceSquared = target.getLocation().distanceSquared(playerLocation);
                if (distanceSquared > REVIEW_DISTANCE_SQUARED) {
                    continue;
                }
                if (PlayerProfile.getPlayerProfileByUuid(target.getUniqueId()).getPlayerOption().isBountyHiddenWhenNear()
                        && distanceSquared < HIDE_DISTANCE_SQUARED) {
                    continue;
                }
                reviewers.add(target);
            }

            if (!reviewers.isEmpty()) {
                Hologram newHologram = HologramAPI.createHologram(
                        playerLocation.clone().add(x, 0, z),
                        CC.translate(color + "&l" + bounty + "g")
                );
                newHologram.spawn(reviewers);
                synchronized (holograms) {
                    while (holograms.size() >= MAX_ACTIVE_HOLOGRAMS_PER_PLAYER) {
                        HologramDisplay removed = holograms.remove(0);
                        try {
                            removed.hologram.deSpawn();
                        } catch (Exception ignored) {
                        }
                    }
                    holograms.add(new HologramDisplay(newHologram, x, z));
                }
            }

            animationData.spawnCooldown = new Cooldown(650);
        }

        List<HologramDisplay> shouldRemove = new ArrayList<>();
        List<HologramDisplay> snapshot;
        synchronized (holograms) {
            snapshot = new ArrayList<>(holograms);
        }
        for (HologramDisplay hologram : snapshot) {
            Location location = player.getLocation().clone();
            location.setX(location.getX() + hologram.boostX);
            location.setY(hologram.getHologram().getLocation().getY() + 0.25);
            location.setZ(location.getZ() + hologram.boostZ);
            hologram.getHologram().setLocation(location);
            if (hologram.timer.hasExpired()) {
                hologram.hologram.deSpawn();
                shouldRemove.add(hologram);
            }
        }
        if (!shouldRemove.isEmpty()) {
            synchronized (holograms) {
                holograms.removeAll(shouldRemove);
            }
        }
    }

    private double generatorLocDouble() {
        if (random.nextBoolean()) {
            return random.nextDouble();
        } else {
            return -random.nextDouble();
        }
    }

    public static class AnimationData {
        private final List<HologramDisplay> holograms;
        private Cooldown spawnCooldown;

        public AnimationData() {
            this.holograms = Collections.synchronizedList(new ArrayList<>());
            this.spawnCooldown = new Cooldown(0);
        }

        public List<HologramDisplay> getHolograms() {
            return holograms;
        }

        public Cooldown getSpawnCooldown() {
            return spawnCooldown;
        }
    }

    @Getter
    public static class HologramDisplay {
        private final Hologram hologram;
        private final double boostX;
        private final double boostZ;
        private final Cooldown timer;

        public HologramDisplay(Hologram hologram, double boostX, double boostZ) {
            this.hologram = hologram;
            this.boostX = boostX;
            this.boostZ = boostZ;
            this.timer = new Cooldown(660, TimeUnit.MILLISECONDS);
        }
    }
}
