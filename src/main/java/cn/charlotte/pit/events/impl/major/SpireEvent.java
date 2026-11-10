package cn.charlotte.pit.events.impl.major;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.events.EventBlockTracker;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.data.sub.PlayerInv;
import cn.charlotte.pit.event.PitKillEvent;
import cn.charlotte.pit.event.PitPlayerSpawnEvent;
import cn.charlotte.pit.events.*;
import cn.charlotte.pit.item.type.SpireArmor;
import cn.charlotte.pit.item.type.SpireSword;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.medal.impl.challenge.SpireFloorMedal;
import cn.charlotte.pit.movement.PlayerMoveHandler;
import cn.charlotte.pit.runnable.ClearRunnable;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.TitleUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.random.RandomUtil;
import cn.charlotte.pit.util.time.TimeUtil;
import com.boydti.fawe.FaweAPI;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.patterns.SingleBlockPattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.schematic.MCEditSchematicFormat;
import com.sk89q.worldedit.schematic.SchematicFormat;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/21 11:02
 */
public class SpireEvent implements IEvent, IEpicEvent, Listener, IPrepareEvent, ISortedEvent, IScoreBoardInsert {
    private final Map<UUID, PlayerSpireData> dataMap;
    private final Map<UUID, Integer> rankMap = new HashMap<>();
    private final List<UUID> cooldownList = new ArrayList<>();
    private Cooldown timer;
    private BukkitRunnable runnable;
    private long generation;
    private EditSession session;
    private EditSession teleport;
    private EditSession wall;

    public SpireEvent() {
        this.dataMap = new HashMap<>();
    }

    @Override
    public String getEventInternalName() {
        return "spire";
    }

    @Override
    public String getEventName() {
        return "&5&l尖塔夺魁";
    }

    @Override
    public int requireOnline() {
        return NewConfiguration.INSTANCE.getEventOnlineRequired().getOrDefault(getEventInternalName(), 5);
    }

    @Override
    public void onActive() {
        dataMap.clear();
        rankMap.clear();
        cooldownList.clear();
        generation++;
        this.timer = new Cooldown(5, TimeUnit.MINUTES);
        this.runnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (timer.hasExpired()) {
                    cancel();
                    if (SpireEvent.this.equals(ThePit.getInstance().getEventFactory().getActiveEpicEvent())) {
                        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                            ThePit.getInstance()
                                    .getEventFactory()
                                    .inactiveEvent(SpireEvent.this);
                        });
                    }
                }

                refreshCooldownPlayer();
            }
        };

        this.runnable.runTaskTimer(ThePit.getInstance(), 20, 20);

        Bukkit.getPluginManager().registerEvents(this, ThePit.getInstance());

        for (Player player : cn.charlotte.pit.junxu.PublicPitParticipants.online()) {
            player.setMaxHealth(player.getMaxHealth());
            player.setHealth(player.getMaxHealth());

            Location location = ThePit.getInstance().getPitConfig()
                    .getSpawnLocations()
                    .get(RandomUtil.random.nextInt(ThePit.getInstance().getPitConfig().getSpawnLocations().size()));
            player.teleport(location);
            player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1, 0.5F);
        }

        // The schematic and glass wall both keep FAWE undo histories. Clear all
        // temporary player blocks first so those histories cannot preserve and
        // later resurrect placed obsidian.
        ClearRunnable.getClearRunnable().clearAllPlacedBlocks();
        Runnable build = () -> {
            if (this.session != null) {
                this.session.undo(this.session);
                this.session.flushQueue();
            }

            final Location location = ThePit.getInstance().getPitConfig().getSpireLoc();

            final BukkitWorld world = new BukkitWorld(location.getWorld());

            BlockVector vector = new BlockVector(location.getX(), location.getY(), location.getZ());

            session = FaweAPI.getEditSessionBuilder(world).build();

            final MCEditSchematicFormat mcedit = (MCEditSchematicFormat) SchematicFormat.MCEDIT;


            try (InputStream inputStream = ThePit.getInstance().getClass().getClassLoader().getResourceAsStream("spire.schematic")) {
                final CuboidClipboard clipboard = mcedit.load(inputStream);
                final com.sk89q.worldedit.Vector min = vector.add(clipboard.getOffset());
                EventBlockTracker.get().captureRegion(EventBlockTracker.SPIRE, location.getWorld(),
                        min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                        min.getBlockX() + clipboard.getWidth() - 1,
                        min.getBlockY() + clipboard.getHeight() - 1,
                        min.getBlockZ() + clipboard.getLength() - 1);
                clipboard.paste(session, vector, false);

                session.flushQueue();

            } catch (Exception e) {
                throw new IllegalStateException("Failed to construct Spire", e);
            }

            Location middle = ThePit.getInstance().getPitConfig().getRagePitMiddle();
            BlockVector wallVec = new BlockVector(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ());
            EventBlockTracker.get().captureHollowCylinder(EventBlockTracker.SPIRE_WALL, middle,
                    ThePit.getInstance().getPitConfig().getRagePitRadius(),
                    ThePit.getInstance().getPitConfig().getRagePitHeight());
            wall = FaweAPI.getEditSessionBuilder(world).build();
            wall.makeCylinder(wallVec, new SingleBlockPattern(new BaseBlock(BlockID.GLASS)), ThePit.getInstance().getPitConfig().getRagePitRadius(), ThePit.getInstance().getPitConfig().getRagePitHeight(), false);
            wall.flushQueue();

            if (teleport == null) {
                teleport = FaweAPI.getEditSessionBuilder(world).build();

                Location portalA = ThePit.getInstance().getPitConfig().getPortalPosA();
                Location portalB = ThePit.getInstance().getPitConfig().getPortalPosB();
                BlockVector posA = new BlockVector(portalA.getBlockX(), portalA.getBlockY(), portalA.getBlockZ());
                BlockVector posB = new BlockVector(portalB.getBlockX(), portalB.getBlockY(), portalB.getBlockZ());
                EventBlockTracker.get().captureRegion(EventBlockTracker.SPIRE_PORTAL, location.getWorld(),
                        posA.getBlockX(), posA.getBlockY(), posA.getBlockZ(),
                        posB.getBlockX(), posB.getBlockY(), posB.getBlockZ());

                teleport.replaceBlocks(new CuboidRegion(posA, posB), new BlockMask(teleport.getExtent(), new BaseBlock(BlockID.AIR)), new BaseBlock(BlockID.END_PORTAL));
                teleport.flushQueue();
            }
        };
        build.run();
    }

    @EventHandler
    public void onJoinArea(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);

            final Player player = event.getPlayer();

            PlayerSpireData current = dataMap.get(player.getUniqueId());
            if (current != null && current.inArea) return;

            if (!dataMap.containsKey(player.getUniqueId())) {

                final PlayerSpireData data = new PlayerSpireData();
                data.uuid = player.getUniqueId();
                data.name = player.getName();

                dataMap.put(player.getUniqueId(), data);
            }

            final PlayerSpireData data = dataMap.get(player.getUniqueId());

            data.floor = 0;
            data.inArea = true;
            data.soul = 0;

            if (!this.teleportPlayer(player, data)) {
                dataMap.remove(player.getUniqueId());
                return;
            }

            player.closeInventory();
            final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            profile.setInventory(PlayerInv.fromPlayerInventory(player.getInventory()));
            profile.setTempInvUsing(true);

            PlayerUtil.clearPlayer(player, true, true);
            this.giveInvSets(player);
        }
    }

    @EventHandler
    public void onSpawn(PitPlayerSpawnEvent event) {
        final PlayerSpireData data = dataMap.get(event.getPlayer().getUniqueId());
        if (data != null) {
            dataMap.remove(event.getPlayer().getUniqueId());
        }
        final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());


        if (profile.isLoaded()) {
            profile.getInventory()
                    .applyItemToPlayer(event.getPlayer());
            profile.setTempInvUsing(false);
        }
    }

    private void giveInvSets(Player player) {
        final PlayerSpireData data = this.dataMap.get(player.getUniqueId());
        final int floor = data.floor;

        if (floor == 0) {
            final SpireSword sword = new SpireSword(Material.WOOD_SWORD);
            player.getInventory().addItem(sword.toItemStack());
        } else if (floor == 1 || floor == 2) {
            final SpireSword sword = new SpireSword(Material.WOOD_SWORD);
            player.getInventory().addItem(sword.toItemStack());

            final SpireArmor chest = new SpireArmor(Material.LEATHER_CHESTPLATE);
            player.getInventory().setChestplate(chest.toItemStack());
            final SpireArmor leg = new SpireArmor(Material.CHAINMAIL_LEGGINGS);
            player.getInventory().setLeggings(leg.toItemStack());
        } else if (floor == 3 || floor == 4 || floor == 5) {
            final SpireSword sword = new SpireSword(Material.IRON_SWORD);
            player.getInventory().addItem(sword.toItemStack());

            final SpireArmor chest = new SpireArmor(Material.IRON_CHESTPLATE);
            player.getInventory().setChestplate(chest.toItemStack());
            final SpireArmor leg = new SpireArmor(Material.CHAINMAIL_LEGGINGS);
            player.getInventory().setLeggings(leg.toItemStack());
            final SpireArmor boots = new SpireArmor(Material.IRON_BOOTS);
            player.getInventory().setBoots(boots.toItemStack());
        } else if (floor >= 6) {
            final SpireSword sword = new SpireSword(Material.DIAMOND_SWORD);
            player.getInventory().addItem(sword.toItemStack());

            final SpireArmor chest = new SpireArmor(Material.DIAMOND_CHESTPLATE);
            player.getInventory().setChestplate(chest.toItemStack());
            final SpireArmor leg = new SpireArmor(Material.IRON_LEGGINGS);
            player.getInventory().setLeggings(leg.toItemStack());
            final SpireArmor boots = new SpireArmor(Material.DIAMOND_BOOTS);
            player.getInventory().setBoots(boots.toItemStack());
        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final PlayerSpireData data = dataMap.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        dataMap.remove(player.getUniqueId());
        PlayerMoveHandler.getCantMoveList().remove(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (cn.charlotte.pit.junxu.PublicPitParticipants.privateRoom(event.getEntity())) return;
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            if (cooldownList.contains(event.getDamager().getUniqueId()) || cooldownList.contains(event.getEntity().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onKill(PitKillEvent event) {
        if (cn.charlotte.pit.junxu.PublicPitParticipants.privateRoom(event.getKiller())) return;
        if (!event.isProgressionEligible() || !event.isHumanPvp()) return;
        try {
            final Player killer = event.getKiller();
            final PlayerSpireData data = dataMap.get(killer.getUniqueId());
            if (data == null) {
                return;
            }

            //floor up start
            if (data.floor == 5) {
                data.reachedSixFloor = true;
            }

            //玩家升级，给予新的物品以及传送至新的一层
            if (data.floor < SpireTeleport.topFloor(ThePit.getInstance().getPitConfig().getSpireFloorLoc())) {
                data.lastFloorChange = System.currentTimeMillis();
                data.floor++;
            } else {
                new SpireFloorMedal().setProgress(PlayerProfile.getPlayerProfileByUuid(event.getKiller().getUniqueId()), 1);
            }
            data.soul = data.soul + this.getKillRewardSouls(data);
            data.kills++;
            cn.charlotte.pit.reward.MajorEventRewards.mark("spire", killer);
            TitleUtil.sendTitle(killer, CC.translate("&a击杀!"), this.getFloorColor(data.floor) + "第" + (data.floor + 1) + "层", 5, 20, 10);
            killer.playSound(killer.getLocation(), Sound.LEVEL_UP, 1, 1.5F);

            final long scheduledGeneration = generation;
            Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                if (!isCurrentParticipant(killer, data, scheduledGeneration)) return;
                int amount = InventoryUtil.getAmountOfItem(killer, "broken_soul");

                this.handleUpdate(killer, data);

                if (amount < 2) {
                    amount++;
                }

                killer.getInventory().addItem(
                        new ItemBuilder(Material.MAGMA_CREAM)
                                .name("&b断裂的灵魂")
                                .lore(
                                        "&7事件物品",
                                        "",
                                        "&7使用后获得效果:",
                                        "&3抗性提升 II (0:03)",
                                        "&74&c❤&7 回复",
                                        "&73&6❤&7 伤害吸收")
                                .internalName("broken_soul")
                                .amount(amount)
                                .removeOnJoin(true)
                                .canDrop(false)
                                .canTrade(false)
                                .canSaveToEnderChest(false)
                                .build()
                );
            }, 1L);
            //floor up end

            LivingEntity target1 = event.getTarget();
            final Player target;
            final PlayerSpireData targetData;
            if (target1 instanceof Player) {
                target = (Player) event.getTarget();
                targetData = dataMap.get(target.getUniqueId());
                if (targetData == null) {
                    return;
                }
            } else  {
                return;
            }


            targetData.floor--;
            if (targetData.floor < 0) {
                targetData.floor = 0;
                targetData.lastFloorChange = System.currentTimeMillis();
                targetData.soul = targetData.soul - Math.min(targetData.soul, this.getDeadPunishmentSouls(targetData));

                TitleUtil.sendTitle(target, CC.translate("&c被击杀"), this.getFloorColor(data.floor) + "第" + (data.floor + 1) + "层", 5, 10, 20);
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(ThePit.getInstance(), () -> {
                if (isCurrentParticipant(target, targetData, scheduledGeneration)) {
                    this.handleUpdate(target, targetData);
                }
            }, 2L);

            Bukkit.getScheduler().runTaskAsynchronously(ThePit.getInstance(), this::refreshRank);
        } catch (Exception e) {
            CC.printErrorWithCode(event.getKiller(), e);
        }
    }

    private int getArenaOnline(int floor) {
        int online = 0;
        for (Map.Entry<UUID, PlayerSpireData> entry : this.dataMap.entrySet()) {
            final PlayerSpireData value = entry.getValue();
            if (value.inArea) {
                if (value.floor == floor) {
                    online++;
                }
            }
        }
        return online;
    }

    private void refreshRank() {
        final Map<UUID, Integer> map = new HashMap<>();

        final AtomicInteger rank = new AtomicInteger(this.dataMap.size());
        this.dataMap.values()
                .stream()
                .sorted(Comparator.comparingInt(data -> data.soul))
                .forEach(data -> {
                    map.put(data.uuid, rank.getAndDecrement());
                });

        this.rankMap.clear();
        this.rankMap.putAll(map);
    }

    private boolean teleportPlayer(Player player, PlayerSpireData data) {
        final List<Location> loc = ThePit.getInstance()
                .getPitConfig()
                .getSpireFloorLoc();

        Location destination = data.floor >= 0 && data.floor <= SpireTeleport.topFloor(loc)
                ? SpireTeleport.findSafeLocation(loc.get(data.floor), data.floor, RandomUtil.random) : null;
        if (destination == null) {
            ThePit.getInstance().getLogger().warning("No safe Spire destination for floor " + (data.floor + 1));
            player.sendMessage(CC.translate("&c本层没有安全的传送点，请联系管理员检查尖塔楼层配置。"));
            return false;
        }
        if (!player.teleport(destination)) return false;
        player.setVelocity(new org.bukkit.util.Vector());
        player.setFallDistance(0);
        return true;
    }

    private boolean isCurrentParticipant(Player player, PlayerSpireData data, long scheduledGeneration) {
        return scheduledGeneration == generation && player.isOnline() && data.inArea
                && dataMap.get(player.getUniqueId()) == data;
    }

    private void handleUpdate(Player player, PlayerSpireData data) {
        PlayerUtil.clearPlayer(player, true, true);
        this.giveInvSets(player);
        final long scheduledGeneration = generation;
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (!isCurrentParticipant(player, data, scheduledGeneration)) return;
            if (!this.teleportPlayer(player, data)) return;

            if (data.floor >= 4) {
                if (this.getArenaOnline(data.floor) >= 2) {
                    this.cooldownList.add(player.getUniqueId());
//                    PlayerMoveHandler.getCantMoveList().add(player);
                }
            }
        });
    }

    private int getKillRewardSouls(PlayerSpireData data) {
        switch (data.floor) {
            case 4:
            case 5: {
                return 5;
            }
            case 6: {
                return 8;
            }
            case 7: {
                return 10;
            }
            case 8: {
                return 25;
            }
            default: {
                return 1;
            }
        }
    }

    private int getDeadPunishmentSouls(PlayerSpireData data) {
        switch (data.floor) {
            case 4:
            case 5: {
                return -3;
            }
            case 6: {
                return -5;
            }
            case 7: {
                return -6;
            }
            case 8: {
                return -20;
            }
            default: {
                return 0;
            }
        }
    }

    @Override
    public void onInactive() {
        generation++;
        HandlerList.unregisterAll(this);
        if (this.runnable != null) this.runnable.cancel();
        EventBlockTracker.get().restoreImmediately(EventBlockTracker.SPIRE_PORTAL);
        EventBlockTracker.get().restoreImmediately(EventBlockTracker.SPIRE_WALL);
        EventBlockTracker.get().restoreImmediately(EventBlockTracker.SPIRE);
        session = null;
        teleport = null;
        wall = null;
        Runnable restorePlayers = () -> {
            cn.charlotte.pit.junxu.PublicPitParticipants.online().forEach(player -> {
                final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
                if (profile.isTempInvUsing()) {
                    profile.getInventory().applyItemToPlayer(player);
                    profile.setTempInvUsing(false);
                }

                List<Location> spawns = ThePit.getInstance().getPitConfig().getSpawnLocations();
                if (!spawns.isEmpty()) player.teleport(spawns.get(RandomUtil.random.nextInt(spawns.size())));
            });
        };
        restorePlayers.run();

        for (UUID uuid : cooldownList) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) PlayerMoveHandler.getCantMoveList().remove(player);
        }
        cooldownList.clear();

        CC.boardCast(CC.CHAT_BAR);
        CC.boardCast("&6&l天坑事件结束: " + this.getEventName() + "&6&l!");

        for (Player player : cn.charlotte.pit.junxu.PublicPitParticipants.online()) {
            final PlayerSpireData data = dataMap.get(player.getUniqueId());

            final int rank = rankMap.containsKey(player.getUniqueId()) ? (rankMap.get(player.getUniqueId()) + 1) : -1;
            final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());

            double rewardCoins = 0;
            int rewardRenown = 0;
            if (rank > 0 && rank <= 3) {
                rewardCoins += 2000;
                rewardRenown += 2;
            } else if (rank > 0 && rank <= 20) {
                rewardCoins += 500;
                rewardRenown += 1;
            } else {
                rewardCoins += 100;
            }
            if (data != null && data.reachedSixFloor) {
                rewardRenown++;
            }
            if (ThePit.getInstance().getPitConfig().isGenesisEnable() && profile.getGenesisData().getTier() >= 5 && rewardRenown > 0) {
                rewardRenown++;
            }
            int enchantBoostLevel = cn.charlotte.pit.enchantment.type.rare.PaparazziEnchant.getUsableLevel(player);
            if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) {
                enchantBoostLevel = 0;
            }
            if (enchantBoostLevel > 0 && !cn.charlotte.pit.reward.EconomyV2.active()) {
                rewardCoins += 0.5 * enchantBoostLevel * rewardCoins;
                rewardRenown += Math.floor(0.5 * enchantBoostLevel * rewardRenown);
                MythicLeggingsItem mythicLeggings = new MythicLeggingsItem();
                mythicLeggings.loadFromItemStack(player.getInventory().getLeggings());
                if (mythicLeggings.isEnchanted()) {
                    if (mythicLeggings.getMaxLive() > 0 && mythicLeggings.getLive() <= 2) {
                        player.getInventory().setLeggings(new ItemStack(Material.AIR));
                    } else {
                        mythicLeggings.setLive(mythicLeggings.getLive() - 2);
                        player.getInventory().setLeggings(mythicLeggings.toItemStack());
                    }
                }
            }
            if (PlayerUtil.isPlayerUnlockedPerk(player, "self_confidence")) {
                if (rank > 0 && rank <= 5) {
                    rewardCoins += 5000;
                } else if (rank > 0 && rank <= 10) {
                    rewardCoins += 2500;
                } else if (rank > 0 && rank <= 15) {
                    rewardCoins += 1000;
                }
            }
            if (data == null) {
                rewardCoins = 100;
                rewardRenown = 0;
            }
            Map<UUID, Integer> economicRanks = new HashMap<>();
            rankMap.forEach((id, value) -> economicRanks.put(id, value + 1));
            cn.charlotte.pit.reward.MajorEventRewards.Result payout = cn.charlotte.pit.reward.MajorEventRewards.settle(
                    player, "spire", rewardCoins, rewardRenown, rank, economicRanks, data != null && data.reachedSixFloor, enchantBoostLevel);
            rewardCoins = payout.coins; rewardRenown = payout.renown;
            profile.setRenown(profile.getRenown() + rewardRenown);

            profile.kingsQuestsData.checkUpdate();
            if (profile.kingsQuestsData.getAccepted()) {
                if (!profile.kingsQuestsData.getCompleted()) {
                    profile.kingsQuestsData.setCollectedRenown(profile.kingsQuestsData.getCollectedRenown() + rewardRenown);
                }
            }

            player.sendMessage(CC.translate("&6你的奖励: &6+" + String.format(java.util.Locale.ROOT, "%.2f", rewardCoins) + "硬币 &e+" + rewardRenown + "声望"));
            if (rank > 0) {
                player.sendMessage(CC.translate("&6&l你: &7收集了总计 &b" + (data == null ? 0 : data.soul) + " &7的灵魂 (排名#" + rank + ")"));
            }
            if (data != null && data.reachedSixFloor) {
                player.sendMessage(CC.translate("&6&l额外奖励: &a&l成功! &7最高进入过第六层或更高!"));
            } else {
                player.sendMessage(CC.translate("&6&l额外奖励: &c&l失败! &7你没能进入过第六层!"));
            }
            if (rankMap.size() >= 3) {
                Map<Integer, Player> rankPlayerMap = new HashMap<>();
                for (Map.Entry<UUID, Integer> entry : rankMap.entrySet()) {
                    if (entry.getValue() <= 3) {
                        rankPlayerMap.put(entry.getValue(), Bukkit.getPlayer(entry.getKey()));
                    }
                }

                player.sendMessage(CC.translate("&6顶级玩家: "));
                for (int i = 1; i < 4; i++) {
                    final Player top = rankPlayerMap.get(i);
                    if (top != null && top.isOnline()) {
                        final PlayerSpireData spireData = dataMap.get(top.getUniqueId());
                        PlayerProfile topProfile = PlayerProfile.getPlayerProfileByUuid(top.getUniqueId());

                        player.sendMessage(CC.translate(" &e&l#" + (i) + " " + topProfile.getFormattedName() + " &7收集了 &b" + spireData.soul + "&b灵魂"));
                    }
                }
            }
        }
        CC.boardCast(CC.CHAT_BAR);
        dataMap.clear();
        rankMap.clear();
    }

    private void refreshCooldownPlayer() {
        final ArrayList<UUID> list = new ArrayList<>(cooldownList);
        final long now = System.currentTimeMillis();
        for (UUID uuid : list) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                final PlayerSpireData data = dataMap.get(uuid);
                if (data != null) {
                    if (now - data.lastFloorChange >= 3 * 1000) {
                        cooldownList.remove(player.getUniqueId());
                        PlayerMoveHandler.getCantMoveList().remove(player);
                    } else {
                        //fixme: player still can move on here
                        final long lastTime = (3 * 1000 - (now - data.lastFloorChange)) / 1000;
                        TitleUtil.sendTitle(player, CC.translate("&a获得击杀"), this.getFloorColor(data.floor) + "第" + (data.floor + 1) + CC.translate("层&e(" + lastTime + ")"), 5, 20, 5);
                    }
                } else {
                    cooldownList.remove(player.getUniqueId());
                    PlayerMoveHandler.getCantMoveList().remove(player);
                }
            } else {
                cooldownList.remove(uuid);
                if (player != null) {
                    PlayerMoveHandler.getCantMoveList().remove(player);
                }
            }
        }
    }

    @Override
    public void onPreActive() {

    }

    public String getDisplayFloor(UUID uuid) {
        final PlayerSpireData data = this.dataMap.get(uuid);
        if (data == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(CC.translate("&" + this.getFloorColor(data.floor).getChar()));

        switch (data.floor) {
            case 0: {
                builder.append("壹");
                break;
            }
            case 1: {
                builder.append("贰");
                break;
            }
            case 2: {
                builder.append("叁");
                break;
            }
            case 3: {
                builder.append("肆");
                break;
            }
            case 4: {
                builder.append("伍");
                break;
            }
            case 5: {
                builder.append("陆");
                break;
            }
            case 6: {
                builder.append("柒");
                break;
            }
            case 7: {
                builder.append("捌");
                break;
            }
            case 8: {
                builder.append("玖");
                break;
            }
        }

        return builder.toString();
    }

    private ChatColor getFloorColor(int floor) {
        //todo: 根据地图颜色适配chat color;
        switch (floor) {

            case 0:
                return ChatColor.RED;
            case 1:
                return ChatColor.YELLOW;
            case 2:
                return ChatColor.GREEN;
            case 3:
                return ChatColor.AQUA;
            case 4:
                return ChatColor.BLUE;
            case 5:
                return ChatColor.DARK_PURPLE;
            case 6:
                return ChatColor.LIGHT_PURPLE;
            case 7:
                return ChatColor.WHITE;
            case 8:
                return ChatColor.GRAY;
            default: {
                return ChatColor.WHITE;
            }
        }
    }

    @Override
    public int getRank(Player player) {
        return this.rankMap.getOrDefault(player.getUniqueId(), -1);
    }

    @Override
    public List<String> insert(Player player) {
        List<String> line = new ArrayList<>();
        line.add("&f剩余时间: &a" + TimeUtil.millisToTimer(this.timer.getRemaining()));
        final PlayerSpireData data = this.dataMap.get(player.getUniqueId());
        if (data == null) {
            line.add("&f灵魂: &b0");
            return line;
        }


        line.add("&f楼层: " + this.getDisplayFloor(player.getUniqueId()));
        line.add("&f灵魂: &b" + data.soul + (this.getRank(player) > 0 ? " &7[&e#" + this.getRank(player) + "&7]" : ""));

        return line;
    }

    public static class PlayerSpireData {
        private UUID uuid;
        private String name;
        private int kills;
        private int soul;
        private long lastFloorChange;

        private boolean reachedSixFloor;

        private int floor;
        private boolean inArea;
    }
}
