package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.DroppedEntityData;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.data.sub.PlacedBlockData;
import cn.charlotte.pit.medal.impl.challenge.ObsidianBreakMedal;
import cn.charlotte.pit.mode.Mode;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.runnable.ClearRunnable;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @Author: EmptyIrony
 * @Date: 2020/12/30 22:48
 */
@AutoRegister
public class ProtectListener implements Listener {

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().getEntities().clear();
    }

    @EventHandler
    public void onPhysic(BlockPhysicsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPortalEnter(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onPlace(BlockPlaceEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            if (!profile.isInArena()) {
                event.setCancelled(true);
                return;
            }

            if ("rage_pit".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
                event.setCancelled(true);
                return;
            }

            if ("pungent_kill_streak".equals(ItemUtil.getInternalName(event.getItemInHand()))) {
                event.setCancelled(true);
                return;
            }

            Location location = event.getBlock().getLocation();

            if (location.getBlockY() >= ThePit.getInstance().getPitConfig().getArenaHighestY()) {
                event.setCancelled(true);
                return;
            }

            if (event.getBlockReplacedState() != null && event.getBlockReplacedState().getType() != Material.AIR) {
                event.setCancelled(true);
                return;
            }

            for (int i = -3; i < 3; i++) {
                for (int j = -3; j < 3; j++) {
                    for (int k = -3; k < 3; k++) {
                        if (location.clone().add(i, j, k).getBlock().getType() == Material.WATER_LILY || location.clone().add(i, j, k).getBlock().getType() == Material.WATER || location.clone().add(i, j, k).getBlock().getType() == Material.STATIONARY_WATER) {
                            event.setCancelled(true);
                            event.getPlayer().sendMessage(CC.translate("&c你无法修改此处方块!"));
                            return;
                        }
                    }
                }
            }

            //tnt block check
            if (event.getBlockPlaced().getType() == Material.TNT) {
                if ("tnt_enchant_item".equals(ItemUtil.getInternalName(event.getItemInHand()))) {
                    event.setCancelled(true);
                    PlayerUtil.takeOneItemInHand(event.getPlayer());
                    if (PlayerUtil.isVenom(event.getPlayer()) || PlayerUtil.isEquippingSomber(event.getPlayer())) {
                        return;
                    }
                    TNTPrimed tntPrimed = (TNTPrimed) event.getPlayer().getWorld().spawnEntity(event.getBlockPlaced().getLocation(), EntityType.PRIMED_TNT);
                    tntPrimed.setFuseTicks(30);

                    tntPrimed.setMetadata("internal", new FixedMetadataValue(ThePit.getInstance(), "tnt_enchant_item"));
                    tntPrimed.setMetadata("shooter", new FixedMetadataValue(ThePit.getInstance(), event.getPlayer().getUniqueId().toString()));
                    int enchantLevel = Utils.getEnchantLevel(event.getPlayer().getInventory().getLeggings(), "tnt_enchant");
                    tntPrimed.setMetadata("damage", new FixedMetadataValue(ThePit.getInstance(), Math.max(0, enchantLevel)));
                    return;
                }
                if ("insta_boom_tnt_enchant".equals(ItemUtil.getInternalName(event.getItemInHand()))) {
                    event.setCancelled(true);
                    PlayerUtil.takeOneItemInHand(event.getPlayer());
                    if (PlayerUtil.isVenom(event.getPlayer()) || PlayerUtil.isEquippingSomber(event.getPlayer())) {
                        return;
                    }
                    TNTPrimed tntPrimed = (TNTPrimed) event.getPlayer().getWorld().spawnEntity(event.getBlockPlaced().getLocation(), EntityType.PRIMED_TNT);
                    tntPrimed.setFuseTicks(0);

                    tntPrimed.setMetadata("internal", new FixedMetadataValue(ThePit.getInstance(), "insta_boom_enchant_item"));
                    tntPrimed.setMetadata("shooter", new FixedMetadataValue(ThePit.getInstance(), event.getPlayer().getUniqueId().toString()));
                    int enchantLevel = Utils.getEnchantLevel(event.getPlayer().getInventory().getLeggings(), "insta_boom_tnt_enchant");
                    tntPrimed.setMetadata("damage", new FixedMetadataValue(ThePit.getInstance(), Math.max(0, enchantLevel) * 0.5));

                    return;
                }
            }

            Material placedType = event.getBlockPlaced().getType();
            long baseTime = 120L;
            if (placedType == Material.COBBLESTONE || placedType == Material.STONE) {
                baseTime = 30L;
            } else if (placedType == Material.OBSIDIAN) {
                baseTime = 60L;
            } else if (placedType == Material.WOOD) {
                baseTime = 30L;
            }
            long existTime = baseTime;
            for (Map.Entry<Integer, PerkData> entry : profile.getChosePerk().entrySet()) {
                if (entry.getValue().getPerkInternalName().equals("BuilderBattleBoost")) {
                    existTime = (long) (baseTime * (1 + (0.6 * entry.getValue().getLevel())));
                }
            }
            /*
            if (ThePit.getInstance().getPitConfig().getArenaHighestY() - location.getY() <= 20) {
                existTime = 12L;
                ActionBarUtil.sendActionBar(event.getPlayer(), "&c你正在高处建筑,方块会更快消失!");
            }

             */
            Block placed = event.getBlockPlaced();
            final byte placedData = placed.getData();
            final long lifetime = existTime;
            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                if (event.isCancelled() || placed.getType() != placedType || placed.getData() != placedData) return;
                ClearRunnable.getClearRunnable().placeBlock(
                    placed.getLocation(),
                    new Cooldown(lifetime, TimeUnit.SECONDS),
                    placedType,
                    placedData
            );
            });
        }
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent e) {
        if (ThePit.mode == Mode.Normal) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private void onPlace(PlayerBucketEmptyEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            if (!profile.isInArena()) {
                event.setCancelled(true);
                return;
            }

            Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
            if (location.getBlock().getType() != Material.AIR
                    || location.getBlockY() >= ThePit.getInstance().getPitConfig().getArenaHighestY()
                    || "rage_pit".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
                event.setCancelled(true);
                return;
            }

            for (int i = -3; i < 3; i++) {
                for (int j = -3; j < 3; j++) {
                    for (int k = -3; k < 3; k++) {
                        if (location.clone().add(i, j, k).getBlock().getType() == Material.WATER_LILY || location.clone().add(i, j, k).getBlock().getType() == Material.WATER || location.clone().add(i, j, k).getBlock().getType() == Material.STATIONARY_WATER) {
                            event.setCancelled(true);
                            event.getPlayer().sendMessage(CC.translate("&c你无法修改此处方块!"));
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackBucket(PlayerBucketEmptyEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (profile.isEditingMode()) return;
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (!event.isCancelled() && target.isLiquid()) {
                ClearRunnable.getClearRunnable().placeBlock(target.getLocation(), new Cooldown(120, TimeUnit.SECONDS));
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void protectBucketFill(PlayerBucketFillEvent event) {
        if (PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId()).isEditingMode()) return;
        ClearRunnable clear = ClearRunnable.getClearRunnable();
        PlacedBlockData placement = clear.getTrackedPlacement(event.getBlockClicked().getLocation());
        if (placement == null || !clear.isTrackedPlacement(placement)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trackLiquidFlow(BlockFromToEvent event) {
        if (!event.getBlock().isLiquid()) return;
        ClearRunnable clear = ClearRunnable.getClearRunnable();
        PlacedBlockData source = clear.getTrackedPlacement(event.getBlock().getLocation());
        if (source == null || !clear.isTrackedPlacement(source)) return;
        Block target = event.getToBlock();
        // Temporary water/lava must never replace arena blocks or natural water.
        if (target.getType() != Material.AIR) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (!event.isCancelled() && target.isLiquid()) {
                clear.placeBlock(target.getLocation(), source.getCooldown());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !isBreakablePlacement(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !isBreakablePlacement(block));
    }

    private boolean isBreakablePlacement(Block block) {
        ClearRunnable clear = ClearRunnable.getClearRunnable();
        return clear != null && clear.isTrackedPlacement(clear.getTrackedPlacement(block.getLocation()));
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Item || event.getEntity() instanceof Arrow) {
            DroppedEntityData data = new DroppedEntityData();
            data.setTimer(new Cooldown(30, TimeUnit.SECONDS));
            data.setEntity(event.getEntity());

            ClearRunnable.getClearRunnable()
                    .getEntityData()
                    .add(data);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Allow plugin-spawned entities (King's Quest chickens, NPCs, event mobs, etc.)
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) event.getRightClicked();
            if (stand.getHelmet() != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            if (PlayerUtil.isStaffSpectating((Player) event.getEntity())) {
                event.setCancelled(true);
                return;
            }
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getEntity().getUniqueId());
            if (!profile.isInArena()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getEntity().getUniqueId());
            if (!profile.isInArena()) {
                event.setCancelled(true);
            }
        }

        if (event.getDamager() instanceof Projectile) {
            if (((Projectile) event.getDamager()).getShooter() instanceof Player) {
                Player shooter = (Player) (((Projectile) event.getDamager()).getShooter());
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(shooter.getUniqueId());
                if (!profile.isInArena()) {
                    event.setCancelled(true);
                }
            }
        }

        if (event.getDamager() instanceof Player) {
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getDamager().getUniqueId());
            if (!profile.isInArena()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBreak(BlockBreakEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (profile.isEditingMode()) {
            return;
        }
        Block block = event.getBlock();

        Optional<PlacedBlockData> first = ClearRunnable.getClearRunnable()
                .getPlacedBlock()
                .stream()
                .filter(placedBlockData -> placedBlockData.getLocation().equals(block.getLocation()))
                .filter(ClearRunnable.getClearRunnable()::isTrackedPlacement)
                .findFirst();

        if (!first.isPresent()) {
            if (!profile.isEditingMode()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getBlock().getType() == Material.OBSIDIAN) {
            new ObsidianBreakMedal().addProgress(profile, 1);
            int level = Utils.getEnchantLevel(event.getPlayer().getInventory().getLeggings(), "purple_gold");
            if (PlayerUtil.isEquippingSomber(event.getPlayer()) || PlayerUtil.isVenom(event.getPlayer())) {
                level = 0;
            }
            if (level > 0) {
                Material toolType = event.getPlayer().getItemInHand() == null ? Material.AIR : event.getPlayer().getItemInHand().getType();
                if (toolType != Material.GOLD_PICKAXE) {
                    int gold;
                    switch (level) {
                        case 1:
                            gold = 7;
                            break;
                        case 2:
                            gold = 11;
                            break;
                        default:
                            gold = 15;
                            break;
                    }
                    RewardService.grant(profile, RewardSource.LOOT, gold, 0);
                    if (level >= 2) {
                        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (level >= 3 ? 3 : 2) * 20, 2), true);
                    }
                }
            }
        }

        ClearRunnable.getClearRunnable().removePlacedBlock(first.get());
        event.setCancelled(true);
        if (block.getType() != Material.AIR) {
            block.setType(Material.AIR, false);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    private void onBedEnter(PlayerBedEnterEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onShear(PlayerShearEntityEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onEditBook(PlayerEditBookEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onSignChange(SignChangeEvent event) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getPlayer().getUniqueId());
        if (!profile.isEditingMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onFoodLevel(FoodLevelChangeEvent event) {
        if (ThePit.mode == Mode.Normal) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    private void onChunkUnload(ChunkUnloadEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onShoot(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Arrow) {
            Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), projectile::remove, 20 * 5);
        }
    }
}
