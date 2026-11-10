package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.FixedRewardData;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.KillRecap;
import cn.charlotte.pit.data.sub.PlayerInv;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.runnable.BountyRunnable;
import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.util.PitProfileUpdater;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.rank.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author: EmptyIrony
 * @Date: 2020/12/30 22:27
 */
@AutoRegister
public class DataListener implements Listener {
    private static DataListener instance;

    private final ScheduledExecutorService executor;

    private final Map<UUID, BukkitRunnable> loadingMap = new ConcurrentHashMap<>();

    public static DataListener getInstance() {
        return instance;
    }

    public DataListener() {
        instance = this;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        JedisPool jedisPool = ThePit.getInstance().getJedis();
        if (jedisPool != null) {
            executor.scheduleAtFixedRate(() -> {
                String databaseName = ThePit.getInstance().getPitConfig().getDatabaseName();

                try(Jedis jedis = jedisPool.getResource()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasMetadata("NPC")) continue;
                        if (loadingMap.containsKey(player.getUniqueId())) {
                            continue;
                        }

                        jedis.expire(
                                "THEPIT_" + databaseName + "_" + player.getUniqueId().toString(),
                                15
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 10L, 10L, TimeUnit.SECONDS);
        }
    }



    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (player.hasMetadata("NPC")) return;
        // Inventory/state can be cleared immediately, but max-health attributes must
        // wait for profile load so modern ViaVersion clients have an entity tracker.
        PlayerUtil.clearPlayer(player, true, true, false);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (!ThePit.getInstance().isEnabled() || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (canLoad(player)) {
                    cancel();
                    load(player);
                }

                //continue waiting
            }
        };

        runnable.runTaskTimerAsynchronously(ThePit.getInstance(), 1L, 20L);

        loadingMap.put(player.getUniqueId(), runnable);

        event.setJoinMessage(null);

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        if (event.getPlayer().hasMetadata("NPC")) return;

        BukkitRunnable loadingFuture = loadingMap.get(event.getPlayer().getUniqueId());
        if (loadingFuture != null) {
            try {
                loadingFuture.cancel();
            } catch (Exception ignored) {

            }
            loadingMap.remove(event.getPlayer().getUniqueId());
            return;
        }

        if (!PlayerUtil.isStaffSpectating(event.getPlayer()) && PlayerProfile.getCacheProfile().containsKey(event.getPlayer().getUniqueId())) {
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());

            if (profile.isLoaded()) {
                if (profile.isScreenShare()) {
                    CC.boardCastWithPermission("&4&l查端时退出! &7玩家 " + LevelUtil.getLevelTagWithRoman(profile.getPrestige(), profile.getLevel()) + " " + RankUtil.getPlayerRealColoredName(event.getPlayer().getUniqueId() + " &7在查端时退出了游戏!"), PlayerUtil.getStaffPermission());
                }

                if (!profile.isTempInvUsing()) {
                    profile.setInventory(PlayerInv.fromPlayerInventory(event.getPlayer().getInventory()));
                }
                profile.setLastLogoutTime(System.currentTimeMillis());

                profile.setTotalPlayedTime(profile.getTotalPlayedTime() + profile.getLastLogoutTime() - profile.getLastLoginTime());
                //reset if data have an error (old bug)
                if (profile.getTotalPlayedTime() > System.currentTimeMillis() - profile.getRegisterTime()) {
                    profile.setTotalPlayedTime(0);
                }

                profile.setLogin(false);
                this.executor.execute(() -> {
                    profile.save(null);
                    Bukkit.getScheduler().runTask(ThePit.getInstance(), () ->
                            PlayerProfile.getCacheProfile().remove(event.getPlayer().getUniqueId(), profile));
                    String databaseName = ThePit.getInstance().getPitConfig().getDatabaseName();

                    JedisPool jedisPool = ThePit.getInstance().getJedis();
                    if (jedisPool != null) {
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.del("THEPIT_" + databaseName + "_" + profile.getUuid());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                });
                final BountyRunnable.AnimationData animationData = BountyRunnable.getAnimationDataMap().get(event.getPlayer().getUniqueId());
                if (animationData != null) {
                    for (BountyRunnable.HologramDisplay hologram : animationData.getHolograms()) {
                        hologram.getHologram().deSpawn();
                    }
                }
                BountyRunnable.getAnimationDataMap().remove(event.getPlayer().getUniqueId());
                KillRecap.removeRecap(event.getPlayer().getUniqueId());
            }
        }
    }

    @EventHandler
    public void onShoot(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getEntity();
            if (arrow.getShooter() instanceof Player) {
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(((Player) arrow.getShooter()).getUniqueId());
                profile.setShootAttack(profile.getShootAttack() + 1);
            }
        } else if (event.getEntity() instanceof FishHook) {
            FishHook hook = (FishHook) event.getEntity();
            if (hook.getShooter() instanceof Player) {
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(((Player) hook.getShooter()).getUniqueId());
                profile.setRodUsed(profile.getRodUsed() + 1);
            }
        }
    }

    public boolean canLoad(Player player) {
        String databaseName = ThePit.getInstance().getPitConfig().getDatabaseName();
        JedisPool jedisPool = ThePit.getInstance().getJedis();
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return "OK".equals(jedis.set(
                        "THEPIT_" + databaseName + "_" + player.getUniqueId().toString(),
                        "locked", "NX", "EX", 30L
                ));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    public void shutdown() {
        for (BukkitRunnable runnable : loadingMap.values()) {
            try {
                runnable.cancel();
            } catch (Exception ignored) {
            }
        }
        loadingMap.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void load(Player player) {
        if (Bukkit.getPlayer(player.getUniqueId()) == player && player.isOnline()) {
            PlayerProfile profile = new PlayerProfile(player.getUniqueId(), player.getName());

            PlayerProfile load = profile.load();

            SupporterService supporterService = SupporterService.get();
            if (supporterService != null) supporterService.loadProfile(load);

            load.setPlayerName(player.getName());
            load.setLowerName(player.getName().toLowerCase());

            if (!player.isOnline()) {
                return;
            }

            PlayerProfile.getCacheProfile().put(player.getUniqueId(), load);

            if (load.getRegisterTime() <= 1) {
                load.setRegisterTime(System.currentTimeMillis());
            }
            load.setLastLoginTime(System.currentTimeMillis());

            if (load.getProfileFormatVersion() == 0) {
                PitProfileUpdater.updateVersion0(load);
            }

            if (ThePit.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                    new PitProfileLoadedEvent(PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())).callEvent();
                });
            }

            if (player.isOnline()) {
                FixedRewardData.Companion.sendMail(load, player);
            }
        } else {
            PlayerProfile.getCacheProfile().remove(player.getUniqueId());
        }

        loadingMap.remove(player.getUniqueId());
    }

}
