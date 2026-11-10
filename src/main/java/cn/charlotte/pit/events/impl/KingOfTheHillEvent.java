package cn.charlotte.pit.events.impl;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.events.EventBlockTracker;
import cn.charlotte.pit.events.IEvent;
import cn.charlotte.pit.events.INormalEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.hologram.Hologram;
import cn.charlotte.pit.util.hologram.HologramAPI;
import lombok.Data;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KingOfTheHillEvent implements IEvent, INormalEvent, Listener {

    private static final int EVENT_DURATION_MINUTES = 4;
    private static final int RADIUS = 7;

    private final Map<Block, Material> blocks = new HashMap<>();
    private final Map<Block, Byte> blockData = new HashMap<>();
    @Getter
    private static HillData hillData;
    private boolean isActive = false;
    private org.bukkit.scheduler.BukkitTask endTask;

    @Override
    public String getEventInternalName() {
        return "koth";
    }

    @Override
    public String getEventName() {
        return "&b&l占山为王";
    }

    @Override
    public int requireOnline() {
        return NewConfiguration.INSTANCE.getEventOnlineRequired().get(getEventInternalName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if ("koth".equals(ThePit.getInstance().getEventFactory().getActiveNormalEventName()) && hillData != null) {
            hillData.spawnHologramsForPlayer(event.getPlayer());
        }
    }

    @Override
    public void onActive() {
        isActive = true;
        hillData = new HillData();

        Location location = ThePit.getInstance().getPitConfig().getKothLoc();
        if (location == null) {
            Bukkit.broadcastMessage(CC.translate("&c&l占山为王！ &7活动区域未设置，请联系管理员设置！"));
            deactivateEvent();
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, ThePit.getInstance());
        Bukkit.broadcastMessage(CC.translate("&b&l占山为王！ &7现在你可以在指定区域内获得四倍的&b经验&7与&6硬币&7加成！"));

        spawnKothCircle(location, RADIUS, Material.GOLD_BLOCK);
        scheduleEventDeactivation(EVENT_DURATION_MINUTES);
    }

    private void scheduleEventDeactivation(int minutes) {
        if (endTask != null) endTask.cancel();
        endTask = Bukkit.getScheduler().runTaskLater(
                ThePit.getInstance(),
                this::deactivateEvent,
                20L * 60 * minutes
        );
    }

    private void deactivateEvent() {
        if (!isActive) {
            return;
        }
        if (ThePit.getInstance().getEventFactory().getActiveNormalEvent() == this) {
            ThePit.getInstance().getEventFactory().inactiveEvent(this);
        } else {
            onInactive();
        }
    }

    @Override
    public void onInactive() {
        if (endTask != null) endTask.cancel();
        endTask = null;
        despawnKothCircle();
        HandlerList.unregisterAll(this);
        isActive = false;
    }

    @EventHandler
    public void onKill(PitKillEvent event) {
        if (!event.isProgressionEligible() || !event.isHumanPvp()) return;
        if (isActive && isRegion(event.getKiller())) {
            if (cn.charlotte.pit.reward.EconomyV2.active()) {
                if (!cn.charlotte.pit.reward.KillRewardContext.extras(event.getKiller().getUniqueId())) return;
                double base = cn.charlotte.pit.reward.ProgressionV2.base(
                        cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(event.getKiller().getUniqueId()).getPrestige())
                        * cn.charlotte.pit.listener.CombatListener.eventBoost;
                event.setCoins(event.getCoins() + base);
                event.setExp(event.getExp() + base);
                event.setHillMultiplier(2.0);
            } else {
                event.setCoins(event.getCoins() * 4);
                event.setExp(event.getExp() * 4);
                event.setHillMultiplier(4.0);
            }
        }
    }

    public void spawnKothCircle(Location center, int radius, Material material) {
        int extendedRadius = radius + 1;
        int squaredRadius = extendedRadius * extendedRadius;

        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        List<Block> circleBlocks = new ArrayList<>();
        List<Location> affectedLocations = new ArrayList<>();
        for (int x = centerX - extendedRadius; x <= centerX + extendedRadius; x++) {
            for (int z = centerZ - extendedRadius; z <= centerZ + extendedRadius; z++) {
                if (Math.pow(centerX - x, 2) + Math.pow(centerZ - z, 2) <= squaredRadius) {
                    Block block = world.getBlockAt(x, centerY, z);
                    circleBlocks.add(block);
                    affectedLocations.add(block.getLocation());
                }
            }
        }

        Block beaconBlock = center.clone().add(0, -1, 0).getBlock();
        affectedLocations.add(beaconBlock.getLocation());
        EventBlockTracker.get().captureBlocks(EventBlockTracker.KOTH, affectedLocations);

        blocks.clear();
        blockData.clear();
        for (Block block : circleBlocks) {
            saveBlockData(block);
            block.setType(material);
        }
        setupCentralBeacon(center);
        setupHolograms(center);
    }

    private void saveBlockData(Block block) {
        blocks.put(block, block.getType());
        blockData.put(block, block.getData());
    }

    private void setupCentralBeacon(Location center) {
        Block beaconBlock = center.clone().add(0, -1, 0).getBlock();
        saveBlockData(beaconBlock);
        beaconBlock.setType(Material.BEACON);
    }

    private void setupHolograms(Location center) {
        hillData.setHolograms(
                HologramAPI.createHologram(center.clone().add(0.5, 5.0, 0.5), CC.translate("&b&l占山为王")),
                HologramAPI.createHologram(center.clone().add(0.5, 4.5, 0.5), CC.translate("&e&l4x &b经验值&e/&6硬币"))
        );
        hillData.spawnAllHolograms();
    }

    public void despawnKothCircle() {
        // Restore synchronously so players do not keep seeing the hill after the
        // event ends. The persisted tracker also restores it after a restart or crash.
        EventBlockTracker.get().restoreImmediately(EventBlockTracker.KOTH);
        blocks.forEach((block, type) -> block.setType(type, false));
        blockData.forEach((block, data) -> block.setData(data, false));
        blocks.clear();
        blockData.clear();
        if (hillData != null) {
            hillData.despawnAllHolograms();
        }
    }

    public boolean isRegion(Player player) {
        if (!isActive || player == null) return false;
        Location loc = player.getLocation();
        return isGoldBlock(loc.clone().add(0, -1, 0)) || isGoldBlock(loc.clone().add(0, -2, 0));
    }

    private boolean isGoldBlock(Location location) {
        Block block = location.getBlock();
        return blocks.containsKey(block) && block.getType() == Material.GOLD_BLOCK;
    }

    @Data
    public static class HillData {
        private Hologram firstHologram;
        private Hologram secondHologram;

        public void setHolograms(Hologram first, Hologram second) {
            this.firstHologram = first;
            this.secondHologram = second;
        }

        public void spawnAllHolograms() {
            if (firstHologram != null) firstHologram.spawn();
            if (secondHologram != null) secondHologram.spawn();
        }

        public void despawnAllHolograms() {
            if (firstHologram != null) firstHologram.deSpawn();
            if (secondHologram != null) secondHologram.deSpawn();
        }

        public void spawnHologramsForPlayer(Player player) {
            if (firstHologram != null) firstHologram.spawn(Collections.singletonList(player));
            if (secondHologram != null) secondHologram.spawn(Collections.singletonList(player));
        }
    }
}
