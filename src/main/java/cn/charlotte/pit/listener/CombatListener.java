package cn.charlotte.pit.listener;

import cn.charlotte.pit.reward.CombatEconomy;
import cn.charlotte.pit.reward.EconomyV2;
import cn.charlotte.pit.reward.ProgressionV2;
import cn.charlotte.pit.reward.RewardModifiers;
import cn.charlotte.pit.reward.TrainingBudget;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.buff.impl.BountySolventBuff;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.*;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.enchantment.type.op.RealManEnchant;
import cn.charlotte.pit.event.*;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.events.genesis.team.GenesisTeam;
import cn.charlotte.pit.item.AbstractPitItem;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.item.type.LuckyChestplate;
import cn.charlotte.pit.item.type.mythic.MythicBowItem;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.item.type.mythic.MythicSwordItem;
import cn.charlotte.pit.map.kingsquests.item.Cherry;
import cn.charlotte.pit.movement.PlayerMoveHandler;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerAssist;
import cn.charlotte.pit.parm.listener.IPlayerBeKilledByEntity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.type.prestige.RawNumbersPerk;
import cn.charlotte.pit.perk.type.streak.nonpurchased.OverDriveMegaStreak;
import cn.charlotte.pit.perk.type.streak.tothemoon.ToTheMoonMegaStreak;
import cn.charlotte.pit.runnable.ProfileLoadRunnable;
import cn.charlotte.pit.reward.RewardMath;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.BountyRules;
import cn.charlotte.pit.reward.BountyTracker;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.reward.PassiveDeathTracker;
import cn.charlotte.pit.reward.KillRewardContext;
import cn.charlotte.pit.reward.BotRewards;
import cn.charlotte.pit.reward.RewardCalendar;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.combat.SecondaryDamage;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.chat.*;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.item.ItemUtil;
import cn.charlotte.pit.util.random.RandomUtil;
import cn.charlotte.pit.util.rank.RankUtil;
import cn.charlotte.pit.util.time.TimeUtil;
import com.google.common.util.concurrent.AtomicDouble;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.minecraft.server.v1_8_R3.ItemArmor;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 11:16
 */
@AutoRegister
public class CombatListener implements Listener {
    private static final BountySolventBuff bountySolventBuff = new BountySolventBuff();
    public static CombatListener INSTANCE;
    private final Random random = new Random();
    private final DecimalFormat numFormat = new DecimalFormat("0.00");
    private final DecimalFormat intFormat = new DecimalFormat("0");
    public static volatile double eventBoost = 1.0;
    String boostString = " &6(限时加成x" + eventBoost + "倍奖励)";

    private final Set<UUID> processingDeaths = ConcurrentHashMap.newKeySet();
    private final PassiveDeathTracker passiveDeaths = new PassiveDeathTracker(TimeUnit.MINUTES.toMillis(10));
    private final BountyTracker bountyTracker = new BountyTracker();
    private final Map<UUID, PassiveDeathTracker.Result> deathRewardPolicies = new HashMap<>();
    private final Map<EntityDamageEvent, ExtraDamage> extraDamage = new WeakHashMap<>();
    private final Map<UUID, Double> deathTargetMultipliers = new HashMap<>();
    private final Set<UUID> deathEffectsHandled = new HashSet<>();
    private static final long REPEAT_KILL_CLEANUP_INTERVAL_TICKS = TimeUnit.MINUTES.toSeconds(1) * 20L;

    public CombatListener() {
        this.initMoveHandler();
        INSTANCE = this;
        RewardCalendar.start(ThePit.getInstance());
        cn.charlotte.pit.reward.EconomyAudit.start(ThePit.getInstance());
        Bukkit.getScheduler().runTaskTimer(
                ThePit.getInstance(),
                this::cleanupExpiredRepeatKills,
                REPEAT_KILL_CLEANUP_INTERVAL_TICKS,
                REPEAT_KILL_CLEANUP_INTERVAL_TICKS
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A reconnect does not erase the last few no-output deaths. Records expire
        // after inactivity, and any accepted outgoing damage resets the sequence.
    }

    private void initMoveHandler() {
        eventBoost = ThePit.getInstance().getPitConfig().getBooster();
        boostString = " &6(限时加成x" + eventBoost + "倍奖励)";
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onStrike(PitStreakKillChangeEvent event) {
        final PlayerProfile profile = event.getPlayerProfile();

        if (profile.getChosePerk().get(5) == null) {
            return;
        }

        // Dynamic streak broadcast interval: raise threshold when many players are online
        // to reduce chat spam. 5 at low pop, 10 at medium, 15 at high.
        int online = Bukkit.getOnlinePlayers().size();
        int streakInterval;
        if (online >= 80) {
            streakInterval = 15;
        } else if (online >= 50) {
            streakInterval = 10;
        } else {
            streakInterval = 5;
        }

        if (Math.floor(event.getFrom()) % streakInterval != 0 && Math.floor(event.getTo()) % streakInterval == 0 && event.getTo() > 0 && Math.floor(event.getFrom()) != Math.floor(event.getTo())) {
            cn.charlotte.pit.junxu.JunxuMessages.broadcast(profile.getPlayerUuid(), MessageType.STREAK, "&c&l连杀! " + profile.getFormattedName() + " &7已经 &c" + intFormat.format(Math.floor(event.getTo())) + "&7 连杀了!");
        }
    }

    @EventHandler
    public void onTp(PlayerTeleportEvent event) {
        if (PlayerMoveHandler.getCantMoveList().contains(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        PlayerMoveHandler.checkMove(event.getTo(), event.getFrom(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onCombat(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getEntity();
                Player damager = (Player) event.getDamager();

                PlayerProfile playerProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
                PlayerProfile damagerProfile = PlayerProfile.getPlayerProfileByUuid(damager.getUniqueId());

                //BeastMode boost
                if (PlayerUtil.isPlayerChosePerk(player, "beast_mode_mega_streak") && playerProfile.getStreakKills() >= 50) {
                    double boostLevel = Math.floor((playerProfile.getStreakKills() - 50) / 5);
                    event.setDamage(event.getDamage() + 0.1 * boostLevel);
                }

                //DiamondSword Boost
                if (!SecondaryDamage.isHit(event) && damager.getItemInHand() != null && damager.getItemInHand().getType() == Material.DIAMOND_SWORD && "shopItem".equals(ItemUtil.getInternalName(damager.getItemInHand()))) {
                    PlayerProfile targetProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
                    if (targetProfile.getBounty() != 0) {
                        event.setDamage(event.getDamage() * 1.2);
                    }
                }

                //CombatSpade Boost
                if (!SecondaryDamage.isHit(event) && damager.getItemInHand() != null && damager.getItemInHand().getType() == Material.DIAMOND_SPADE && "shopItem".equalsIgnoreCase(ItemUtil.getInternalName(damager.getItemInHand()))) {
                    for (ItemStack is : player.getInventory().getArmorContents()) {
                        if (is != null && is.getType().name().contains("DIAMOND")) {
                            event.setDamage(event.getDamage() + 0.5);
                        }
                    }
                }

                if (player.getItemInHand() != null && !PlayerUtil.isVenom(player) && !PlayerUtil.isEquippingSomber(player)) {
                    int enchantLevel = Utils.getEnchantLevel(player.getItemInHand(), "bruiser_enchant");
                    if (enchantLevel > 0 && player.isBlocking()) {
                        double reduce = enchantLevel >= 3 ? 4.0 : enchantLevel;
                        event.setDamage(Math.max(0, event.getDamage() - reduce));
                    }
                }

                this.handleDamage(event, player, damager, playerProfile, damagerProfile, effectiveDamage(event, player), false);
                playerProfile.setLastDamageAt(System.currentTimeMillis());
            } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
                Player player = (Player) event.getEntity();
                Player damager = (Player) ((Projectile) event.getDamager()).getShooter();

                PlayerProfile playerProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
                PlayerProfile damagerProfile = PlayerProfile.getPlayerProfileByUuid(damager.getUniqueId());

                handleDamage(event, player, damager, playerProfile, damagerProfile, effectiveDamage(event, player), true);

                playerProfile.setLastDamageAt(System.currentTimeMillis());
            } else if (event.getDamager() instanceof TNTPrimed && event.getEntity() instanceof Player) {
                List<MetadataValue> metadata = event.getDamager().getMetadata("internal");
                if (metadata != null && !metadata.isEmpty()) {
                    if ("tnt".equals(metadata.get(0).asString())) {
                        event.setDamage(event.getDamage() * (1 / 5F));
                    } else if ("red_packet".equals(metadata.get(0).asString())) {
                        event.setDamage(0);
                    } else if ("tnt_enchant_item".equals(metadata.get(0).asString())) {
                        Player source = enchantTntSource(event, "tnt_enchant");
                        if (source == null) { event.setCancelled(true); return; }
                        if (PlayerUtil.isEquippingSomber((Player) event.getEntity())) {
                            event.setCancelled(true);
                            return;
                        }
                        List<MetadataValue> tntDamage = event.getDamager().getMetadata("damage");
                        if (tntDamage != null && tntDamage.size() > 0 && tntDamage.get(0).asInt() > 0) {
                            event.setDamage(0);
                            if (PlayerUtil.getDistance(event.getDamager().getLocation(), event.getEntity().getLocation()) > 3) {
                                event.setCancelled(true);
                                return;
                            }
                            if (event.getEntity() instanceof Player) {
                                Player victim = (Player) event.getEntity();
                                double trueDamage = (0.5 + tntDamage.get(0).asInt() * 0.5) * 2;
                                PlayerUtil.damage(source, victim, PlayerUtil.DamageType.TRUE, trueDamage, true);
                            }
                        } else {
                            event.setCancelled(true);
                        }
                    } else if ("insta_boom_enchant_item".equals(metadata.get(0).asString())) {
                        if (enchantTntSource(event, "insta_boom_tnt_enchant") == null) { event.setCancelled(true); return; }
                        if (PlayerUtil.isEquippingSomber((Player) event.getEntity())) {
                            event.setCancelled(true);
                            return;
                        }
                        List<MetadataValue> tntDamage = event.getDamager().getMetadata("damage");
                        if (tntDamage != null && tntDamage.size() > 0) {
                            if (PlayerUtil.getDistance(event.getDamager().getLocation(), event.getEntity().getLocation()) > 4) {
                                event.setCancelled(true);
                                return;
                            }
                            event.setDamage(tntDamage.get(0).asDouble());
                        }
                    }
                }
            }
        }
    }

    private Player enchantTntSource(EntityDamageByEntityEvent event, String enchantId) {
        List<MetadataValue> metadata = event.getDamager().getMetadata("shooter");
        if (metadata.isEmpty()) return null;
        try {
            Player owner = Bukkit.getPlayer(UUID.fromString(metadata.get(0).asString()));
            if (owner == null || owner.isDead() || owner.equals(event.getEntity())) return null;
            if (Utils.getEnchantLevel(owner.getInventory().getLeggings(), enchantId) <= 0
                    || PlayerUtil.isVenom(owner) || PlayerUtil.isEquippingSomber(owner)) return null;
            return owner;
        } catch (IllegalArgumentException invalidOwner) { return null; }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (ThePit.getInstance().getPitBotManager() != null
                && ThePit.getInstance().getPitBotManager().isBot(player)) return;
        ProfileLoadRunnable.getInstance().handleQuit(player);
        if (PlayerProfile.getCacheProfile().containsKey(player.getUniqueId()) && !PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).getCombatTimer().hasExpired()) {
            handlePlayerDeath(player, null, false);
            final PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if (!profile.isTempInvUsing()) {
                profile.setInventory(PlayerInv.fromPlayerInventory(event.getPlayer().getInventory()));
            }
            profile.setBounty(0);
        }
        processingDeaths.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKilled(PlayerDeathEvent event) {
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        if (ThePit.getInstance().getPitBotManager() != null
                && ThePit.getInstance().getPitBotManager().handleDeath(event.getEntity(), event.getEntity().getKiller())) {
            return;
        }
        handlePlayerDeath(event.getEntity(), event.getEntity().getKiller(), true);
    }


    private void handleDamage(EntityDamageByEntityEvent event, Player player, Player damager, PlayerProfile playerProfile, PlayerProfile damagerProfile, double damage, boolean isShoot) {
        if (!damager.getUniqueId().equals(player.getUniqueId())) {
            noteCombatOutput(damager.getUniqueId(), damage);
        }
        playerProfile.setCombatTimer(new Cooldown((playerProfile.getBounty() == 0 ? 24 : 48), TimeUnit.SECONDS));
        damagerProfile.setCombatTimer(new Cooldown((damagerProfile.getBounty() == 0 ? 24 : 48), TimeUnit.SECONDS));

        recordContribution(playerProfile, damager.getUniqueId(), damage);

        playerProfile.setHurtDamage((long) (playerProfile.getHurtDamage() + damage));
        if (isShoot) {
            if (event.getDamager() instanceof Arrow) {
                playerProfile.setBowHurtDamage((long) (playerProfile.getBowHurtDamage() + damage));
                damagerProfile.setBowHit(damagerProfile.getBowHit() + 1);
                damagerProfile.setArrowTotalDamage((long) (damagerProfile.getArrowTotalDamage() + damage));
            } else if (event.getDamager() instanceof FishHook) {
                damagerProfile.setRodHit(damagerProfile.getRodHit() + 1);
            }
        } else {
            playerProfile.setMeleeHurtDamage((long) (playerProfile.getMeleeHurtDamage() + damage));
            if (!SecondaryDamage.isHit(event)) damagerProfile.setMeleeHit(damagerProfile.getMeleeHit() + 1);
            damagerProfile.setMeleeTotalDamage((long) (damagerProfile.getMeleeTotalDamage() + damage));
        }
        damagerProfile.setTotalDamage((long) (damagerProfile.getTotalDamage() + damage));


        int absorptionHearts = (int) (((CraftPlayer) player).getHandle().getAbsorptionHearts() / 2);
        int totalHearts = (int) player.getMaxHealth() / 2;
        int nowHearts = (int) player.getHealth() / 2;
        int damageHearts = (int) damage / 2;

        if (damagerProfile.getPlayerOption().getBarPriority() != PlayerOption.BarPriority.ENCHANT_ONLY) {
            int i;
            StringBuilder builder = new StringBuilder();
            builder.append(RankUtil.getPlayerColoredName(player.getUniqueId()));
            if (PlayerUtil.isVenom(player)) {
                builder.append(" &2");
            } else {
                builder.append(" &4");
            }
            for (i = 0; i < nowHearts; ++i) {
                builder.append("❤");
            }
            if (absorptionHearts > 0) {
                if (absorptionHearts < 25) {
                    builder.append("&e");
                    for (i = 0; i < absorptionHearts; ++i) {
                        builder.append("❤");
                    }
                } else {
                    builder.append("&e❤");
                    builder.append("...").append(absorptionHearts - 2).append("...❤");
                }
            }
            if (PlayerUtil.isVenom(player)) {
                builder.append("&a");
            } else {
                builder.append("&c");
            }
            for (i = 0; i < damageHearts; ++i) {
                builder.append("❤");
            }
            builder.append("&7");
            int heats = totalHearts - nowHearts - damageHearts;
            builder.append("❤".repeat(Math.max(0, heats)));
            if (PlayerUtil.isVenom(damager)) {
                long duration_damager = damager.getMetadata("combo_venom").get(0).asLong() - System.currentTimeMillis();
                builder.append(" &7| &c&l沉默&b&l(我) &d&l")
                        .append(TimeUtil.millisToSeconds(duration_damager))
                        .append("秒&r");
            }
            if (PlayerUtil.isVenom(player)) {
                long duration_player = player.getMetadata("combo_venom").get(0).asLong() - System.currentTimeMillis();
                builder.append(" &7| &c&l沉默&b&l(敌) &d&l")
                        .append(TimeUtil.millisToSeconds(duration_player))
                        .append("秒&r");
            }
            if (PlayerUtil.isPlayerUnlockedPerk(damager, new RawNumbersPerk().getInternalPerkName())) {
                builder.append(" &c").append(this.numFormat.format(damage)).append("HP");
            }
            ActionBarUtil.sendActionBar(damager, String.valueOf(builder));
            player.setMetadata("showing_damage_data", new FixedMetadataValue(ThePit.getInstance(), System.currentTimeMillis()));
        }
        if (player.hasMetadata("backing")) {
            player.sendMessage(CC.translate("&c回城被取消."));
            player.removeMetadata("backing", ThePit.getInstance());
        }


        //handle kill recap - start
        String damagerName = damagerProfile.getFormattedName();
        String playerName = playerProfile.getFormattedName();

        KillRecap.DamageData damagerData = new KillRecap.DamageData();
        damagerData.setTimestamp(System.currentTimeMillis());
        damagerData.setDisplayName(playerName);
        damagerData.setAttack(true);
        damagerData.setMelee(!isShoot);
        damagerData.setDamage(damage);
        damagerData.setAfterHealth(Math.max(player.getHealth() - damage, 0));
        damagerData.setUsedItem(damager.getItemInHand() == null ? null : damager.getItemInHand().clone());
        damagerData.setTimer(new Cooldown(10, TimeUnit.SECONDS));


        List<KillRecap.DamageData> remove = damagerProfile.getKillRecap()
                .getDamageLogs()
                .stream()
                .filter(data -> data.getTimer().hasExpired())
                .collect(Collectors.toList());
        damagerProfile.getKillRecap()
                .getDamageLogs()
                .removeAll(remove);
        damagerProfile.getKillRecap()
                .getDamageLogs()
                .add(damagerData);

        KillRecap.DamageData playerData = new KillRecap.DamageData();
        playerData.setTimestamp(damagerData.getTimestamp());
        playerData.setDisplayName(damagerName);
        playerData.setAttack(false);
        playerData.setMelee(!isShoot);
        playerData.setAfterHealth(Math.max(player.getHealth() - damage, 0));
        playerData.setUsedItem(damager.getItemInHand() == null ? null : damager.getItemInHand().clone());
        playerData.setTimer(new Cooldown(10, TimeUnit.SECONDS));
        playerData.setDamage(damage);

        List<KillRecap.DamageData> shouldRemove = playerProfile.getKillRecap()
                .getDamageLogs()
                .stream()
                .filter(data -> data.getTimer().hasExpired())
                .collect(Collectors.toList());

        List<KillRecap.DamageData> damageLogs = playerProfile.getKillRecap()
                .getDamageLogs();
        damageLogs.removeAll(shouldRemove);
        damageLogs.add(playerData);
        //handle kill recap - end
    }

    private RepeatKillResult trackRepeatKill(UUID killerUuid, UUID victimUuid) {
        if (EconomyV2.active()) return new RepeatKillResult(
                CombatEconomy.deathMultiplier(PlayerProfile.getPlayerProfileByUuid(victimUuid)), true);
        PassiveDeathTracker.Result result = deathRewardPolicies.get(victimUuid);
        if (result == null) result = passiveDeaths.finishLife(victimUuid, true, System.currentTimeMillis());
        return new RepeatKillResult(result.getMultiplier(), true);
    }

    private RepeatKillResult getAssistPolicy(UUID assisterUuid, UUID victimUuid) {
        if (EconomyV2.active()) return new RepeatKillResult(
                CombatEconomy.deathMultiplier(PlayerProfile.getPlayerProfileByUuid(victimUuid)), true);
        PassiveDeathTracker.Result result = deathRewardPolicies.get(victimUuid);
        if (result == null) result = passiveDeaths.current(victimUuid, System.currentTimeMillis());
        return new RepeatKillResult(result.getMultiplier(), true);
    }

    private void cleanupExpiredRepeatKills() {
        passiveDeaths.cleanup(System.currentTimeMillis());
        bountyTracker.cleanup(System.currentTimeMillis());
    }

    void noteCombatOutput(UUID attacker, double acceptedDamage) {
        passiveDeaths.recordOutput(attacker, acceptedDamage, System.currentTimeMillis());
    }

    void noteBountyDamage(UUID attacker, UUID victim, double acceptedDamage) {
        bountyTracker.recordDamage(attacker, victim, acceptedDamage, System.currentTimeMillis());
    }

    public void forgetCombatState(UUID uuid) {
        bountyTracker.finishLife(uuid);
        processingDeaths.remove(uuid);
        passiveDeaths.forget(uuid);
        deathRewardPolicies.remove(uuid);
        deathTargetMultipliers.remove(uuid);
        deathEffectsHandled.remove(uuid);
    }

    public void recordExtraDamage(EntityDamageEvent event, double amount, double healthBefore, boolean lethal) {
        extraDamage.put(event, new ExtraDamage(RewardMath.positive(amount), healthBefore, lethal));
    }

    public boolean hasLethalExtraDamage(EntityDamageEvent event) {
        ExtraDamage extra = extraDamage.get(event);
        return extra != null && extra.lethal;
    }

    private final Map<EntityDamageEvent, Double> creditedDamage = new java.util.WeakHashMap<>();

    public double getCreditedDamage(EntityDamageEvent event, Player victim) {
        if (event.isCancelled()) return 0;
        Double receipt = creditedDamage.get(event);
        if (receipt != null) return receipt;
        ExtraDamage extra = extraDamage.get(event);
        double health = extra == null ? victim.getHealth() : extra.healthBefore;
        double absorption = victim instanceof CraftPlayer ? ((CraftPlayer) victim).getHandle().getAbsorptionHearts() : 0;
        return RewardMath.creditedDamage(health, absorption, CombatDamage.accepted(event),
                extra == null ? 0 : extra.amount, extra != null && extra.lethal);
    }

    private double effectiveDamage(EntityDamageEvent event, Player victim) {
        double damage = getCreditedDamage(event, victim);
        creditedDamage.put(event, damage);
        extraDamage.remove(event);
        return damage;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIndirectCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof TNTPrimed)) return;
        List<MetadataValue> shooter = event.getDamager().getMetadata("shooter");
        if (shooter.isEmpty()) return;
        try {
            Player attacker = Bukkit.getPlayer(UUID.fromString(shooter.get(0).asString()));
            if (attacker != null) {
                recordDirectDamage(attacker, (Player) event.getEntity(),
                        effectiveDamage(event, (Player) event.getEntity()));
            }
        } catch (IllegalArgumentException ignored) { }
    }

    public void recordDirectDamage(Player attacker, Player victim, double amount) {
        amount = RewardMath.positive(amount);
        if (amount == 0 || attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        PlayerProfile victimProfile = PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId());
        PlayerProfile attackerProfile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        if (!victimProfile.isLoaded() || !attackerProfile.isLoaded()) return;
        recordContribution(victimProfile, attacker.getUniqueId(), amount);
        victimProfile.setLastDamageAt(System.currentTimeMillis());
        victimProfile.setCombatTimer(new Cooldown(victimProfile.getBounty() == 0 ? 24 : 48, TimeUnit.SECONDS));
        attackerProfile.setCombatTimer(new Cooldown(attackerProfile.getBounty() == 0 ? 24 : 48, TimeUnit.SECONDS));
        noteCombatOutput(attacker.getUniqueId(), amount);
        ((CraftPlayer) victim).getHandle().killer = ((CraftPlayer) attacker).getHandle();
    }

    private void recordContribution(PlayerProfile victim, UUID attacker, double amount) {
        if (amount <= 0 || !Double.isFinite(amount)) return;
        PlayerProfile attackingProfile = PlayerProfile.getCacheProfile().get(attacker);
        if (!victim.isBot() && attackingProfile != null && !attackingProfile.isBot()) {
            noteBountyDamage(attacker, victim.getPlayerUuid(), amount);
            CombatEconomy.humanDamage(attackingProfile, victim, amount);
        }
        DamageData data = victim.getDamageMap().computeIfAbsent(attacker, DamageData::new);
        data.setDamage(data.getTimer().hasExpired() ? amount : data.getDamage() + amount);
        data.setTimer(new Cooldown(30, TimeUnit.SECONDS));
        if (Bukkit.getPluginManager() != null)
            new cn.charlotte.pit.event.PitDamageAppliedEvent(attacker, victim.getPlayerUuid(), amount).callEvent();
    }

    private static final class ExtraDamage {
        private final double amount;
        private final double healthBefore;
        private final boolean lethal;
        private ExtraDamage(double amount, double healthBefore, boolean lethal) {
            this.amount = amount; this.healthBefore = healthBefore; this.lethal = lethal;
        }
    }

    public void handleKill(Player killer, PlayerProfile killerProfile, LivingEntity player, PlayerProfile playerProfile) {
        try {
            if (!killerProfile.isLoaded() || !playerProfile.isLoaded()) return;
            EconomyV2.migrate(killerProfile);
            EconomyV2.migrate(playerProfile);
            if (killerProfile.isBot() && (playerProfile.isBot() || ThePit.getInstance().getPitBotManager() == null
                    || !ThePit.getInstance().getPitBotManager().ownsProfile(killerProfile))) return;
            boolean botTarget = playerProfile.isBot();
            double botVariableFactor = BotRewards.variableFactor(playerProfile);
            double botReward = botTarget ? BotRewards.amount(killerProfile.getPrestige(), BotRewards.multiplier(eventBoost)) : 0;
            boolean isNight = isNight();
            boolean playerOperatedTarget = player instanceof Player && !playerProfile.isBot();
            RepeatKillResult repeatKill = playerOperatedTarget
                    ? trackRepeatKill(killer.getUniqueId(), player.getUniqueId())
                    : RepeatKillResult.FULL_REWARD;
            boolean progressionEligible = repeatKill.progressionEligible;
            boolean economyRunning = EconomyV2.active() || EconomyV2.observing();
            boolean extraRewards = !economyRunning || CombatEconomy.allowExtras(killerProfile, playerProfile,
                    System.currentTimeMillis(), true);
            boolean trainingTarget = botTarget || economyRunning && !extraRewards;
            double rewardUnits = 1;
            if (economyRunning) {
                rewardUnits = trainingTarget ? CombatEconomy.trainingUnits(playerProfile, player)
                        * (botTarget ? 1 : CombatEconomy.deathMultiplier(playerProfile)) : CombatEconomy.deathMultiplier(playerProfile);
                if (trainingTarget) rewardUnits = TrainingBudget.consume(CombatEconomy.state(killerProfile), rewardUnits, System.currentTimeMillis());
            }
            long rewardLife = CombatEconomy.state(killerProfile).lifeSerial;
            // This is a reward policy for low-interaction deaths, not a bot identity.
            boolean inactiveTarget = repeatKill.multiplier < 1;
            double rareDropMultiplier = repeatKill.multiplier;
            if (EconomyV2.active()) rareDropMultiplier = extraRewards || trainingTarget ? rewardUnits : 0;
            boolean competitiveKill = playerOperatedTarget && !killerProfile.isBot()
                    && (!EconomyV2.active() || extraRewards)
                    && repeatKill.multiplier >= 1
                    && bountyTracker.hasRealFight(killer.getUniqueId(), player.getUniqueId(), System.currentTimeMillis());
            try (KillRewardContext context = botTarget ? KillRewardContext.openBot(killer.getUniqueId())
                    : KillRewardContext.open(killer.getUniqueId(), repeatKill.multiplier)) {
            context.setPreviousStreak(killerProfile.getStreakKills());
            context.setEconomy(rewardUnits, trainingTarget, extraRewards);
            CombatEconomy.contribution(killerProfile, 1, extraRewards || trainingTarget ? rewardUnits : 0);

            final String coloredName = botTarget ? "§7" + playerProfile.getPlayerName()
                    : RankUtil.getPlayerColoredName(player.getUniqueId());

            if (killerProfile.getPlayerOption().getBarPriority() != PlayerOption.BarPriority.ENCHANT_ONLY) {
                ActionBarUtil.sendActionBar(killer, " &a&l击杀! " + coloredName);
            }

            //process drop armor - start
            //fixme: here is an error about async
            if (!botTarget && player instanceof Player && progressionEligible) {
                //只有玩家需要掉落装备
                this.handleItemDrop(killerProfile, killer, (Player) player);
            }
            //process drop armor - end

            // Every accepted kill retains combat effects, streaks and normal death
            // drops. Low-interaction deaths scale money/XP and minted rare drops.
            if (!isNight) {
                killerProfile.setStreakKills(killerProfile.getStreakKills() + 1, progressionEligible);
            }
            killerProfile.setKills(killerProfile.getKills() + 1);

            if (progressionEligible) {
                killerProfile.kingsQuestsData.checkUpdate();
                if (killerProfile.kingsQuestsData.getAccepted()) {
                    if (!killerProfile.kingsQuestsData.getCompleted()) {
                        killerProfile.kingsQuestsData.setKilledPlayer(killerProfile.kingsQuestsData.getKilledPlayer() + 1);
                    }
                }
            }

            double totalXp = 10.0d + killerProfile.getPrestige() * 0.5;
            double totalCoins = 10.0d + killerProfile.getPrestige() * 0.5;

            //process perk - start
            AtomicDouble coinsAtomic = new AtomicDouble(totalCoins);
            AtomicDouble expAtomic = new AtomicDouble(totalXp);
            if (economyRunning) KillRewardContext.beginModifiers(killer.getUniqueId(), ProgressionV2.base(killerProfile.getPrestige()), 0, 0);

            if (progressionEligible) {
                this.handleGameEffect(killerProfile, killer, player, coinsAtomic, expAtomic);
            }
            if (!botTarget && player instanceof Player) {
                dispatchDeathEffectsOnce((Player) player, killer, coinsAtomic, expAtomic);
            }

            if (!isNight && progressionEligible && competitiveKill) {
                //process giving bounty - start
                this.handleAddBounty(killerProfile, killer, player.getUniqueId());
                //process giving bounty - end
            }

            totalCoins = coinsAtomic.get();
            totalXp = expAtomic.get();

            final KillRecap killRecap = this.initializationKillRecap(playerProfile, killerProfile, killer, totalCoins, totalXp);

            //calculation kill reward - start
            AtomicDouble tempCoins = new AtomicDouble(totalCoins);
            AtomicDouble tempExp = new AtomicDouble(totalXp);
            if (botTarget) {
                tempCoins.set(botReward);
                tempExp.set(botReward);
            } else if (progressionEligible) {
                this.calculationKillReward(killerProfile, playerProfile, killRecap, killer, player, tempCoins, tempExp);
            } else {
                // High-frequency targets only retain a small share of the raw base reward.
                tempCoins.set(eventBoost * tempCoins.get());
                tempExp.set(eventBoost * tempExp.get());
            }
            //calculation kill reward - end

            AtomicDouble bountyCoins = new AtomicDouble();
            if (competitiveKill && progressionEligible) {
                handleKillBounty(killerProfile, playerProfile, bountyCoins);
            } else {
                // A bot or noncompetitive death cannot convert a bounty into money.
                if (playerProfile.getBounty() > 0) {
                    ThePit.getInstance().getLogger().info("BOUNTY_FORFEIT player=" + playerProfile.getPlayerName()
                            + " bounty=" + playerProfile.getBounty() + " reason=noncompetitive_death");
                }
                playerProfile.setBounty(0);
            }

            totalCoins = tempCoins.get();
            totalXp = tempExp.get();

            final PitKillEvent event = new PitKillEvent(killer, player, totalCoins, totalXp, progressionEligible);
            event.callEvent();
            totalCoins = RewardMath.positive(event.getCoins());
            totalXp = RewardMath.positive(event.getExp());
            totalCoins *= repeatKill.multiplier;
            totalXp *= repeatKill.multiplier;

            if (botTarget) {
                // Events may observe the kill, but their PvP/global boosters do not multiply training targets.
                totalCoins = botReward;
                totalXp = botReward;
            } else if (isNight) {
                totalCoins *= 0.01;
                totalXp *= 0.01;
            }
            if (EconomyV2.active()) {
                if (trainingTarget) {
                    totalCoins = ProgressionV2.base(killerProfile.getPrestige()) * .5 * eventBoost * rewardUnits * botVariableFactor * (!botTarget && isNight ? .01 : 1);
                    totalXp = totalCoins;
                } else if (!extraRewards) {
                    totalCoins = ProgressionV2.base(killerProfile.getPrestige()) * eventBoost * repeatKill.multiplier;
                    totalXp = totalCoins;
                }
            }
            if (EconomyV2.observing()) {
                double base = ProgressionV2.base(killerProfile.getPrestige()) * eventBoost;
                if (trainingTarget) {
                    double candidate = base * .5 * rewardUnits * botVariableFactor * (!botTarget && isNight ? .01 : 1);
                    KillRewardContext.candidate(candidate, candidate * 4);
                }
                else if (!extraRewards) KillRewardContext.candidate(base * rewardUnits, base * 4 * rewardUnits);
                else {
                    double[] candidate = KillRewardContext.candidate(killer.getUniqueId());
                    if (candidate == null) candidate = new double[]{totalCoins, totalXp * 4};
                    if (event.getHillMultiplier() > 1) { candidate[0] += base; candidate[1] += base * 4; }
                    candidate[0] += killerProfile.getGoldStackAddon() * ProgressionV2.sideScale(killerProfile.getPrestige());
                    candidate[1] += killerProfile.getXpStackAddon() * 4 * ProgressionV2.sideScale(killerProfile.getPrestige());
                    double multiplier = CombatEconomy.deathMultiplier(playerProfile) * (isNight ? .01 : 1);
                    KillRewardContext.candidate(candidate[0] * multiplier, candidate[1] * multiplier);
                }
            }
            double targetMultiplier = botTarget ? 1 : deathTargetMultipliers.getOrDefault(player.getUniqueId(),
                    RewardService.victimMultiplier(playerProfile));
            RewardService.Receipt receipt = RewardService.grant(killerProfile, RewardSource.KILL,
                    totalCoins * targetMultiplier, totalXp * targetMultiplier);
            totalCoins = receipt.getCoins();
            totalXp = receipt.getExperience();
            if (EconomyV2.active() && rewardLife == killerProfile.getEconomyData().lifeSerial) {
                double basePaid = ProgressionV2.base(killerProfile.getPrestige()) * eventBoost * rewardUnits
                        * (trainingTarget ? .5 : ProgressionV2.newcomer(killerProfile.getPrestige())) * botVariableFactor;
                killerProfile.getEconomyData().lifeBaseCoins += Math.min(basePaid, receipt.getCoins());
                killerProfile.getEconomyData().lifeBaseExperience += Math.min(basePaid * 4, receipt.getExperience());
            }
            if (EconomyV2.observing() && rewardLife == CombatEconomy.state(killerProfile).lifeSerial) {
                double basePaid = ProgressionV2.base(killerProfile.getPrestige()) * eventBoost * rewardUnits
                        * (trainingTarget ? .5 : ProgressionV2.newcomer(killerProfile.getPrestige())) * botVariableFactor * (!botTarget && isNight ? .01 : 1);
                CombatEconomy.state(killerProfile).lifeBaseCoins += basePaid;
                CombatEconomy.state(killerProfile).lifeBaseExperience += basePaid * 4;
            }
            // Dedicated receipts are outside PitKillEvent, hill, enchant and newcomer multipliers.
            double specialFactor = isNight ? 0.01 : 1;
            if (competitiveKill && PlayerUtil.isPlayerChosePerk(killer, "high_lander")
                    && killerProfile.getStreakKills() >= 50) {
                totalCoins += RewardService.grant(killerProfile, RewardSource.HIGHLANDER,
                        BountyRules.highlanderBonus(killerProfile.getPrestige(), eventBoost) * specialFactor, 0).getCoins();
            }
            if (bountyCoins.get() > 0) {
                double paidBounty = RewardService.grant(killerProfile, RewardSource.BOUNTY,
                        bountyCoins.get() * specialFactor, 0).getCoins();
                totalCoins += paidBounty;
                ThePit.getInstance().getLogger().info("BOUNTY_CLAIM killer=" + killerProfile.getPlayerName()
                        + " victim=" + playerProfile.getPlayerName() + " coins=" + paidBounty);
            }
            new PitRewardGrantedEvent(killer, player, RewardSource.KILL, totalCoins, totalXp, progressionEligible).callEvent();

            //process quest - start
            if (player instanceof Player && progressionEligible) {
                this.handleQuest(killerProfile, (Player) player);
            }
            //process quest - end

            if (progressionEligible) {
                this.handleGivePlayerKillReward(killer);
            }
            //golden head and vampire - end

            if (!isNight && progressionEligible) {
                //enchant - start
                this.handleMythicItemDrop(killerProfile, killer, player, rareDropMultiplier);
                //enchant - end

                this.handleCherryDrop(killer, rareDropMultiplier);
            }

            //refresh killer highest streak kills
            if (killerProfile.getHighestStreaks() < killerProfile.getStreakKills()) {
                killerProfile.setHighestStreaks((int) killerProfile.getStreakKills());
            }

            killRecap.setTotalCoin(totalCoins);
            killRecap.setTotalExp(totalXp);
            killRecap.setOtherCoin(totalCoins - killRecap.getBaseCoin() - killRecap.getNotStreakCoin()
                    - killRecap.getStreakCoin() - killRecap.getLevelDisparityCoin());
            killRecap.setOtherExp(totalXp - killRecap.getBaseExp() - killRecap.getNotStreakExp()
                    - killRecap.getStreakExp() - killRecap.getLevelDisparityExp());

            //BoardCast msg - start
            this.handleBoardCastMessage(killerProfile, playerProfile, killer, player, totalCoins, totalXp,
                    progressionEligible, repeatKill.multiplier, inactiveTarget, event.getHillMultiplier());
            //BoardCast msg - end
            }
        } catch (Exception e) {
            CC.printError(killer, e);
        }
    }

    private boolean hasPremiumItem(Player player) {
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack == null) {
                continue;
            }
            if (itemStack.getType().name().contains("LEATHER") || itemStack.getType().name().contains("DIAMOND")) {
                return true;
            }
        }

        return false;
    }


    public void handlePlayerDeath(Player player, Player killer, boolean shouldRespawn) {
        if (player == null) {
            return;
        }
        if (!processingDeaths.add(player.getUniqueId())) {
            return;
        }
        boolean scheduledRespawn = false;
        try {
        PlayerProfile playerProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!playerProfile.isLoaded()) return;
        deathTargetMultipliers.put(player.getUniqueId(), RewardService.victimMultiplier(playerProfile));
        if (killer == null) {
            EntityDamageEvent cause = player.getLastDamageCause();
            if (cause instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) cause).getDamager();
                if (damager instanceof Player) {
                    killer = (Player) damager;
                } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                    killer = (Player) ((Projectile) damager).getShooter();
                } else if (damager.hasMetadata("shooter")) {
                    try {
                        killer = Bukkit.getPlayer(UUID.fromString(damager.getMetadata("shooter").get(0).asString()));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
        if (killer != null && killer.getUniqueId().equals(player.getUniqueId())) killer = null;
        if (!playerProfile.isBot()) {
            boolean combatDeath = killer != null || playerProfile.getDamageMap().values().stream()
                    .anyMatch(data -> !data.getTimer().hasExpired() && data.getDamage() > 0);
            CombatEconomy.finishHumanLife(playerProfile, combatDeath, System.currentTimeMillis());
            deathRewardPolicies.put(player.getUniqueId(),
                    passiveDeaths.finishLife(player.getUniqueId(), combatDeath, System.currentTimeMillis()));
        }
        final Player finalKiller = killer;
        if (killer != null && (killer.isOnline() || ThePit.getInstance().getPitBotManager() != null
                && ThePit.getInstance().getPitBotManager().isBot(killer))) {
            PlayerProfile killerProfile = PlayerProfile.getPlayerProfileByUuid(killer.getUniqueId());
            handleKill(killer, killerProfile, player, playerProfile);
        }
        // Also settle unattributed deaths, offline killers, and aborted kill rewards.
        dispatchDeathEffectsOnce(player, killer, new AtomicDouble(), new AtomicDouble());
        playerProfile.setBounty(0);
        bountyTracker.finishLife(player.getUniqueId());

        double respawnTime = playerProfile.getRespawnTime();

        if (!playerProfile.isBot()) {
        PlayerUtil.clearPlayer(player, true, false);

        double mythicProtectChance = 0;

        int divineMiracleEnchantLevel = Utils.getEnchantLevel(player.getInventory().getLeggings(), "divine_miracle_enchant");
        if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) {
            divineMiracleEnchantLevel = 0;
        }
        if (divineMiracleEnchantLevel > 0) {
            mythicProtectChance += 0.15 * divineMiracleEnchantLevel;
        }
        if (PlayerUtil.isPlayerUnlockedPerk(player, "divine_intervention")) {
            mythicProtectChance += 0.05 * PlayerUtil.getPlayerUnlockedPerkLevel(player, "divine_intervention");
        }

        //Promotion effect
        if (PlayerUtil.isPlayerChosePerk(player, "assistant_to_the_streaker") && PlayerUtil.isPlayerUnlockedPerk(player, "promotion")) {
            if (playerProfile.getStreakKills() > 50) {
                if (PlayerUtil.isPlayerChosePerk(player, "over_drive") || PlayerUtil.isPlayerChosePerk(player, "high_lander") || PlayerUtil.isPlayerChosePerk(player, "beast_mode_mega_streak") || PlayerUtil.isPlayerChosePerk(player, "hermit")) {
                    mythicProtectChance = 1;
                }
            }
            if (playerProfile.getStreakKills() > 100) {
                if ((PlayerUtil.isPlayerChosePerk(player, "uber_streak") && playerProfile.getStreakKills() >= 400) || PlayerUtil.isPlayerChosePerk(player, "to_the_moon")) {
                    mythicProtectChance = 1;
                }
            }
        }
        //Funky Feather Item
        ItemLiveDropEvent itemLiveDropEvent = new ItemLiveDropEvent(mythicProtectChance);
        itemLiveDropEvent.callEvent();
        mythicProtectChance = itemLiveDropEvent.getChance();
        if (mythicProtectChance < 1 && !itemLiveDropEvent.isCancelled()) {
            for (int i = 0; i < 9; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if ("funky_feather".equals(ItemUtil.getInternalName(item))) {
                    if (item.getAmount() <= 1) {
                        player.getInventory().setItem(i, new ItemBuilder(Material.AIR).build());
                    } else {
                        item.setAmount(item.getAmount() - 1);
                        player.getInventory().setItem(i, item);
                    }
                    mythicProtectChance = 1;
                    break;
                }
            }
        }

        boolean noProtect = !itemLiveDropEvent.isCancelled() && RandomUtil.hasSuccessfullyByChance(1 - mythicProtectChance);
        int lostMythicLives = 0;
        for (int i = 0; i < 36; i++) {
            cn.charlotte.pit.item.MythicLifeLoss.Result result = cn.charlotte.pit.item.MythicLifeLoss.onDeath(
                    player.getInventory().getItem(i), noProtect, itemLiveDropEvent.isCancelled());
            player.getInventory().setItem(i, result.item);
            lostMythicLives += result.lostLives;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            cn.charlotte.pit.item.MythicLifeLoss.Result result = cn.charlotte.pit.item.MythicLifeLoss.onDeath(
                    armor[i], noProtect, itemLiveDropEvent.isCancelled());
            armor[i] = result.item;
            lostMythicLives += result.lostLives;
        }
        player.getInventory().setArmorContents(armor);
        if (lostMythicLives > 0) new PitItemLivesLostEvent(player, finalKiller, lostMythicLives).callEvent();

        if (lostMythicLives == 0) {
            player.sendMessage(CC.translate("&d&l物品保护! &7由于一个天赋/附魔/物品提供的概率保护,本次死亡没有损失背包内神话物品生命."));
        }
        for (ItemStack itemStack : player.getInventory()) {
            if (ItemUtil.isDeathDrop(itemStack)) {
                player.getInventory().remove(itemStack);
            }
        }

        if (ItemUtil.isDeathDrop(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(new ItemStack(Material.AIR));
        }
        if (ItemUtil.isDeathDrop(player.getInventory().getChestplate())) {
            player.getInventory().setChestplate(new ItemStack(Material.AIR));
        }
        if (ItemUtil.isDeathDrop(player.getInventory().getLeggings())) {
            player.getInventory().setLeggings(new ItemStack(Material.AIR));
        }
        if (ItemUtil.isDeathDrop(player.getInventory().getBoots())) {
            player.getInventory().setBoots(new ItemStack(Material.AIR));
        }

        }
        //process assist - start
        try {
            Map<UUID, DamageData> damageMap = new HashMap<>(playerProfile.getDamageMap());
            List<DamageData> activeDamage = new ArrayList<>();
            for (Map.Entry<UUID, DamageData> entry : damageMap.entrySet()) {
                if (!entry.getValue().getTimer().hasExpired()) {
                    activeDamage.add(entry.getValue());
                }
            }

            double totalDamage = activeDamage
                    .stream()
                    .mapToDouble(DamageData::getDamage)
                    .sum();

            if (totalDamage > 0) {
                this.handleAssist(player, finalKiller, activeDamage, totalDamage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //process assist - end

        KillRecap completedRecap = playerProfile.getKillRecap();
        if (!playerProfile.isBot() && completedRecap.getKiller() != null) {
            completedRecap.completeLog(player);
        }

        //save status - start
        playerProfile.getDamageMap().clear();
        playerProfile.setStreakKills(0);
        playerProfile.setInArena(false);
        playerProfile.setDeaths(playerProfile.getDeaths() + 1);
        if (EconomyV2.active() || EconomyV2.observing()) CombatEconomy.state(playerProfile).newLife();
        playerProfile.setCombatTimer(new Cooldown(0));
        playerProfile.setBountyStreak(0);
        playerProfile.setStrengthNum(0);
        playerProfile.setStrengthTimer(new Cooldown(0));
        //save status - end

        if (!playerProfile.isBot()) {
            InventoryUtil.supplyItems(player);
            if (!playerProfile.isTempInvUsing()) {
                playerProfile.setInventory(PlayerInv.fromPlayerInventory(player.getInventory()));
            }
        }

        if (shouldRespawn) {
            // 设置玩家的初始状态
            player.setGameMode(GameMode.SPECTATOR);
            if (playerProfile.getRespawnTime() <= 1.5) {
                playerProfile.setRespawnTime(0.1d);
            }
            if (respawnTime > 0.1) {
                double finalRespawnTime = respawnTime;
                for (int i = 0; i < finalRespawnTime; i++) {
                    final int remainingTime = (int) (finalRespawnTime - i);

                    int delay = i * 20;
                    Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                        TitleUtil.sendTitle(player, "&c你死了！", "&7将在 &6" + remainingTime + "秒 &7后复活", 5, 5, 20);
                    }, delay);
                }

                Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                    this.doRespawn(player);
                }, (long) (respawnTime * 20L));
            } else {
                Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> doRespawn(player), 1L);
            }
            scheduledRespawn = true;
        }
        } finally {
            deathRewardPolicies.remove(player.getUniqueId());
            deathTargetMultipliers.remove(player.getUniqueId());
            deathEffectsHandled.remove(player.getUniqueId());
            if (!scheduledRespawn) {
                processingDeaths.remove(player.getUniqueId());
            }
        }
    }

    private void doRespawn(Player player) {
        processingDeaths.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }

        Location location = ThePit.getInstance().getPitConfig()
                .getSpawnLocations()
                .get(random.nextInt(ThePit.getInstance().getPitConfig().getSpawnLocations().size()));

        if (ThePit.getInstance().getJunxuManager() != null) {
            Location privateRespawn = ThePit.getInstance().getJunxuManager().respawn(player);
            if (privateRespawn != null) location = privateRespawn;
        }

        Location deployment = cn.charlotte.pit.menu.shop.button.type.TacticalDeploymentShopButton.consumeRespawn(player, location);
        boolean redeployed = deployment != location;
        if (!player.teleport(deployment) && redeployed) { player.teleport(location); redeployed = false; }

        PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).setInArena(redeployed);

        PlayerUtil.clearPlayer(player, true, false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setNoDamageTicks(40);

        PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())
                .applyExperienceToPlayer(player);
        TitleUtil.sendTitle(player, " ", " ", 5, 5, 20);

        for (IPlayerRespawn ins : ThePit.getInstance().getPerkFactory()
                .getPlayerRespawns()) {
            AbstractPerk perk = (AbstractPerk) ins;
            int perkPlayerLevel = perk.getPlayerLevel(player);
            if (perkPlayerLevel != -1) {
                ins.handleRespawn(perkPlayerLevel, player);
            }
        }

        EnchantmentFactor enchantmentFactor = ThePit.getInstance().getEnchantmentFactor();
        for (IPlayerRespawn ins : enchantmentFactor.getPlayerRespawns()) {
            AbstractEnchantment ench = (AbstractEnchantment) ins;

            int level = ench.getItemEnchantLevel(player.getInventory().getLeggings());
            if (level > 0 && ench.isEffectActive(player, null)) {
                ins.handleRespawn(level, player);
            }
        }
    }

    private void handleAssist(Player player, Player killer, List<DamageData> damageData, double totalDamage) {
        PlayerProfile playerProfile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());

        boolean night = isNight();

        for (DamageData data : damageData) {
            if (killer != null && killer.getUniqueId().equals(data.getUuid())) {
                continue;
            }
            if (totalDamage <= 0) return;
            double percentage = RewardMath.contribution(data.getDamage(), totalDamage);
            if (percentage <= 0) continue;
            KillRecap killRecap = playerProfile.getKillRecap();
            KillRecap.AssistData assistData = new KillRecap.AssistData();
            Player assistPlayer = Bukkit.getPlayer(data.getUuid());
            if (assistPlayer == null && ThePit.getInstance().getPitBotManager() != null)
                assistPlayer = ThePit.getInstance().getPitBotManager().getPlayer(data.getUuid());
            if (assistPlayer != null && (assistPlayer.isOnline() || ThePit.getInstance().getPitBotManager() != null
                    && ThePit.getInstance().getPitBotManager().isBot(assistPlayer))) {

                PlayerProfile assistProfile = PlayerProfile.getPlayerProfileByUuid(data.getUuid());
                if (!assistProfile.isLoaded() || assistProfile.isBot() && (playerProfile.isBot()
                        || ThePit.getInstance().getPitBotManager() == null || !ThePit.getInstance().getPitBotManager().ownsProfile(assistProfile))) continue;
                boolean playerOperatedTarget = !playerProfile.isBot();
                RepeatKillResult repeatKill = playerOperatedTarget
                        ? getAssistPolicy(assistPlayer.getUniqueId(), player.getUniqueId())
                        : RepeatKillResult.FULL_REWARD;
                boolean progressionEligible = repeatKill.progressionEligible;
                boolean inactiveTarget = repeatKill.multiplier < 1;
                try (KillRewardContext context = playerProfile.isBot() ? KillRewardContext.openBot(assistPlayer.getUniqueId())
                        : KillRewardContext.open(assistPlayer.getUniqueId(), repeatKill.multiplier)) {

                if (playerProfile.isBot()) {
                    double amount = BotRewards.amount(assistProfile.getPrestige(), BotRewards.multiplier(eventBoost)) * percentage;
                    if (EconomyV2.active() || EconomyV2.observing()) {
                        double units = TrainingBudget.consume(CombatEconomy.state(assistProfile),
                                CombatEconomy.trainingUnits(playerProfile, player) * percentage, System.currentTimeMillis());
                        context.setEconomy(units, true, false);
                        double candidateGold = ProgressionV2.base(assistProfile.getPrestige()) * .5 * eventBoost * units * BotRewards.variableFactor(playerProfile);
                        if (EconomyV2.active()) amount = candidateGold;
                        else KillRewardContext.candidate(candidateGold, candidateGold * 4);
                    }
                    RewardService.Receipt receipt = RewardService.grant(assistProfile, RewardSource.ASSIST, amount, amount);
                    assistProfile.setAssists(assistProfile.getAssists() + 1);
                    new PitRewardGrantedEvent(assistPlayer, player, RewardSource.ASSIST,
                            receipt.getCoins(), receipt.getExperience(), true).callEvent();
                    CC.send(MessageType.COMBAT, assistPlayer, CC.translate("&a&l助攻! &7" + numFormat.format(percentage * 100)
                            + "% 的伤害 &6+" + numFormat.format(receipt.getCoins()) + "硬币 &b+"
                            + numFormat.format(receipt.getExperience()) + "经验值"));
                    continue;
                }

                assistData.setDisplayName(assistProfile.getFormattedName());

                double totalCoins = 10.0d + assistProfile.getPrestige() * 0.5;
                double totalXp = 10.0d + assistProfile.getPrestige() * 0.5;

                if (progressionEligible) {
                    final PitAssistEvent event = new PitAssistEvent(assistPlayer, player, totalCoins, totalXp);
                    event.callEvent();

                    totalCoins = event.getCoins();
                    totalXp = event.getExp();
                }

                assistData.setBaseExp(10.0d + assistProfile.getPrestige() * 0.5);
                assistData.setBaseCoin(10.0d + assistProfile.getPrestige() * 0.5);

                AtomicDouble atomicDouble = new AtomicDouble();
                if (progressionEligible && playerProfile.getStreakKills() != 0 && playerProfile.getStreakKills() % 10 == 0) {
                    totalXp += (playerProfile.getStreakKills() / 10d) * 5;
                    totalCoins += (playerProfile.getStreakKills() / 10d) * 5;
                    atomicDouble.getAndAdd((playerProfile.getStreakKills() / 10d) * 5);
                }

                if (progressionEligible && assistProfile.getStreakKills() != 0 && assistProfile.getStreakKills() % 10 == 0) {
                    totalXp += Math.min(20, (assistProfile.getStreakKills() / 10d) * 5);
                    totalCoins += Math.min(20, (assistProfile.getStreakKills() / 10d) * 5);

                    atomicDouble.getAndAdd((Math.min(20, (assistProfile.getStreakKills() / 10d) * 5)));
                }
                assistData.setStreakCoin(atomicDouble.get());
                assistData.setStreakExp(atomicDouble.get());
                atomicDouble.set(0);

                if (progressionEligible && playerProfile.getPrestige() - assistProfile.getPrestige() > 0) {
                    totalXp += (playerProfile.getPrestige() - assistProfile.getPrestige()) * 7;
                    totalCoins += (playerProfile.getPrestige() - assistProfile.getPrestige()) * 7;

                    atomicDouble.getAndAdd((playerProfile.getPrestige() - assistProfile.getPrestige()) * 7);
                }

                if (progressionEligible && playerProfile.getLevel() - assistProfile.getLevel() >= 30) {
                    totalXp += (playerProfile.getLevel() - assistProfile.getLevel()) / 30d * 3;
                    totalCoins += (playerProfile.getLevel() - assistProfile.getLevel()) / 30d * 3;

                    atomicDouble.getAndAdd((playerProfile.getLevel() - assistProfile.getLevel()) / 30d * 3);
                }


                if (progressionEligible && hasPremiumItem(player)) {
                    if (!hasPremiumItem(assistPlayer)) {
                        totalCoins += 10;
                        totalXp += 10;
                        atomicDouble.getAndAdd(10);
                    }
                }

                assistData.setLevelDisparityExp(atomicDouble.get());
                assistData.setLevelDisparityCoin(atomicDouble.get());

                //process perk - start
                AtomicDouble coinsAtomic = new AtomicDouble(totalCoins);
                AtomicDouble expAtomic = new AtomicDouble(totalXp);
                final Player finalAssister = assistPlayer;
                boolean allowAssistExtras = !EconomyV2.active() && !EconomyV2.observing() || CombatEconomy.allowExtras(assistProfile, playerProfile,
                        System.currentTimeMillis(), false);
                if (EconomyV2.active() || EconomyV2.observing()) {
                    context.setEconomy(percentage * CombatEconomy.deathMultiplier(playerProfile), !allowAssistExtras, allowAssistExtras);
                    KillRewardContext.beginModifiers(assistPlayer.getUniqueId(), ProgressionV2.base(assistProfile.getPrestige()),
                            totalCoins - ProgressionV2.base(assistProfile.getPrestige()), totalXp - ProgressionV2.base(assistProfile.getPrestige()));
                }
                if (progressionEligible) {
                    for (IPlayerAssist ins : ThePit.getInstance().getPerkFactory()
                            .getPlayerAssists()) {
                        AbstractPerk perk = (AbstractPerk) ins;
                        int perkPlayerLevel = perk.getPlayerLevel(assistPlayer);
                        if (perkPlayerLevel != -1) {
                            runKillEffect(perk.getInternalPerkName(), coinsAtomic, expAtomic, () ->
                                    ins.handlePlayerAssist(perkPlayerLevel, finalAssister, player, player.getLastDamage(), (player.getLastDamageCause() == null ? 0 : player.getLastDamageCause().getFinalDamage()), coinsAtomic, expAtomic));
                        }
                    }
                    for (IPlayerAssist ins : ThePit.getInstance().getEnchantmentFactor().getPlayerAssists()) {
                        AbstractEnchantment enchant = (AbstractEnchantment) ins;
                        if (!enchant.isEffectActive(assistPlayer, player)) continue;
                        int level = enchant.getItemEnchantLevel(assistPlayer.getItemInHand());
                        if (level > 0 && assistPlayer.getItemInHand() != null && assistPlayer.getItemInHand().getType() != Material.LEATHER_LEGGINGS) {
                            final int activeLevel = level;
                            runKillEffect(enchant.getNbtName(), coinsAtomic, expAtomic, () ->
                                    ins.handlePlayerAssist(activeLevel, finalAssister, player, player.getLastDamage(), (player.getLastDamageCause() == null ? 0 : player.getLastDamageCause().getFinalDamage()), coinsAtomic, expAtomic));
                        }
                        if (assistPlayer.getInventory().getLeggings() != null && assistPlayer.getInventory().getLeggings().getType() != Material.AIR) {
                            level = enchant.getItemEnchantLevel(assistPlayer.getInventory().getLeggings());
                            if (level > 0) {
                                final int activeLevel = level;
                                runKillEffect(enchant.getNbtName(), coinsAtomic, expAtomic, () ->
                                        ins.handlePlayerAssist(activeLevel, finalAssister, player, player.getLastDamage(), (player.getLastDamageCause() == null ? 0 : player.getLastDamageCause().getFinalDamage()), coinsAtomic, expAtomic));
                            }
                        }
                    }
                }
                assistData.setPercentage(percentage);
                double scale = percentage * repeatKill.multiplier * eventBoost * (night ? 0.01 : 1.0)
                        * deathTargetMultipliers.getOrDefault(player.getUniqueId(), RewardService.victimMultiplier(playerProfile));
                totalCoins = RewardMath.positive(coinsAtomic.get()) * scale;
                totalXp = RewardMath.positive(expAtomic.get()) * scale;
                if (EconomyV2.active()) {
                    RewardModifiers modifiers = KillRewardContext.modifiers();
                    double baseScale = percentage * repeatKill.multiplier * (night ? .01 : 1);
                    if (modifiers != null) {
                        totalCoins = ((coinsAtomic.get() - modifiers.fixedGold()) * eventBoost
                                + modifiers.fixedGold() * ProgressionV2.sideScale(assistProfile.getPrestige())) * baseScale;
                        totalXp = ((expAtomic.get() - modifiers.fixedXp()) * eventBoost
                                + modifiers.fixedXp() * ProgressionV2.sideScale(assistProfile.getPrestige())) * baseScale;
                    }
                    if (!allowAssistExtras) {
                        totalCoins = ProgressionV2.base(assistProfile.getPrestige()) * eventBoost * baseScale;
                        totalXp = totalCoins;
                    }
                    if (!allowAssistExtras) {
                        double units = TrainingBudget.consume(assistProfile.getEconomyData(), percentage
                                * CombatEconomy.trainingUnits(playerProfile, player) * CombatEconomy.deathMultiplier(playerProfile), System.currentTimeMillis());
                        totalCoins = ProgressionV2.base(assistProfile.getPrestige()) * .5 * eventBoost * units * (night ? .01 : 1);
                        totalXp = totalCoins;
                    }
                }
                if (EconomyV2.observing()) {
                    RewardModifiers modifiers = KillRewardContext.modifiers();
                    double candidateScale = percentage * CombatEconomy.deathMultiplier(playerProfile) * (night ? .01 : 1);
                    double side = ProgressionV2.sideScale(assistProfile.getPrestige());
                    double candidateGold = modifiers == null ? totalCoins
                            : ((modifiers.gold() - modifiers.fixedGold()) * eventBoost + modifiers.fixedGold() * side) * candidateScale;
                    double candidateXp = modifiers == null ? totalXp
                            : ((modifiers.experience() - modifiers.fixedXp()) * eventBoost + modifiers.fixedXp() * side) * candidateScale;
                    if (!allowAssistExtras) candidateXp = candidateGold = ProgressionV2.base(assistProfile.getPrestige()) * eventBoost * candidateScale;
                    if (!allowAssistExtras) {
                        double units = TrainingBudget.consume(CombatEconomy.state(assistProfile), percentage
                                * CombatEconomy.trainingUnits(playerProfile, player) * CombatEconomy.deathMultiplier(playerProfile), System.currentTimeMillis());
                        candidateXp = candidateGold = ProgressionV2.base(assistProfile.getPrestige()) * .5 * eventBoost * units * (night ? .01 : 1);
                    }
                    KillRewardContext.candidate(candidateGold, candidateXp * 4);
                }
                double recapScale = scale;

                assistData.setBaseCoin((10.0d + assistProfile.getPrestige() * 0.5) * recapScale);
                assistData.setBaseExp((10.0d + assistProfile.getPrestige() * 0.5) * recapScale);
                assistData.setStreakCoin(assistData.getStreakCoin() * recapScale);
                assistData.setStreakExp(assistData.getStreakExp() * recapScale);
                assistData.setLevelDisparityCoin(assistData.getLevelDisparityCoin() * recapScale);
                assistData.setLevelDisparityExp(assistData.getLevelDisparityExp() * recapScale);
                assistData.setTotalCoin(totalCoins);
                assistData.setTotalExp(totalXp);
                killRecap.getAssistData().add(assistData);

                //Assistant to the streaker effect
                if (progressionEligible) {
                    if (PlayerUtil.isPlayerChosePerk(assistPlayer, "assistant_to_the_streaker") && !isNight()) {
                        CombatEconomy.contribution(assistProfile, Math.floor(100 * percentage) * .01,
                                Math.floor(100 * percentage) * .01 * (allowAssistExtras ? repeatKill.multiplier : 0));
                        assistProfile.setStreakKills(assistProfile.getStreakKills() + Math.floor(100 * percentage) * 0.01);
                    }
                    assistProfile.setAssists(assistProfile.getAssists() + 1);
                }
                RewardService.Receipt receipt = RewardService.grant(assistProfile, RewardSource.ASSIST, totalCoins, totalXp);
                totalCoins = receipt.getCoins();
                totalXp = receipt.getExperience();
                assistData.setTotalCoin(totalCoins);
                assistData.setTotalExp(totalXp);
                new PitRewardGrantedEvent(assistPlayer, player, RewardSource.ASSIST,
                        totalCoins, totalXp, progressionEligible).callEvent();
                assistPlayer.playSound(assistPlayer.getLocation(), Sound.ORB_PICKUP, 1, 1.7F);
                CC.send(MessageType.COMBAT, assistPlayer, CC.translate("&a&l助攻! &7" + numFormat.format(percentage * 100) + "% 的伤害在 " + playerProfile.getFormattedName() + " &6+" + numFormat.format(totalCoins) + "硬币 " + (totalXp > 0 ? "&b+" + numFormat.format(totalXp) + "经验值" : "") + getKillRewardSuffix(repeatKill.multiplier, inactiveTarget)));
                }
            }
        }
    }

    private void handleKillBounty(PlayerProfile killerProfile, PlayerProfile playerProfile, AtomicDouble coin) {
        //handle bounty - start
        int principal = Math.min(BountyRules.HIGHLANDER_CAP, Math.max(0, playerProfile.getBounty()));
        playerProfile.setBounty(0);
        if (principal > 0 && !killerProfile.isBot() && !playerProfile.isBot()
                && ThePit.getInstance().getEventFactory().getActiveEpicEvent() == null
                && bountyTracker.claim(killerProfile.getPlayerUuid(), playerProfile.getPlayerUuid(), System.currentTimeMillis())) {
            String bountyColor = "&6";
            if (ThePit.getInstance().getPitConfig().isGenesisEnable()) {
                if (playerProfile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                    bountyColor = "&b";
                }
                if (playerProfile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                    bountyColor = "&c";
                }
            }
            CC.boardCast(MessageType.BOUNTY, CC.translate("&6&l赏金! " + playerProfile.getFormattedName() + " &7被 " + killerProfile.getFormattedName() + " &7击杀. " + bountyColor + "&l(" + principal + "g)"));
            boolean genesis = ThePit.getInstance().getPitConfig().isGenesisEnable()
                    && killerProfile.getGenesisData().getTier() >= 5;
            // getUuid() is a String, which Bukkit treats as a player name.
            Player killer = Bukkit.getPlayer(killerProfile.getPlayerUuid());
            boolean solvent = killerProfile.getBuffData().getBuff("bounty_solvent").getTier() > 0
                    && killer != null && !UtilKt.hasRealMan(killer);
            coin.addAndGet(BountyRules.payout(principal, genesis, solvent));
            if (principal >= 5000) new cn.charlotte.pit.medal.impl.challenge.MaxBountyHunterMedal().setProgress(killerProfile, 1);
        } else if (principal > 0) {
            ThePit.getInstance().getLogger().info("BOUNTY_FORFEIT player=" + playerProfile.getPlayerName()
                    + " bounty=" + principal + " reason=claim_ineligible_or_cooldown");
        }
        //handle bounty - end
    }

    private void handleQuest(PlayerProfile killerProfile, Player beKilledPlayer) {
        //process quest - start
        QuestData currentQuest = killerProfile.getCurrentQuest();
        if (currentQuest != null && currentQuest.getEndTime() > System.currentTimeMillis()) {
            if (currentQuest.getCurrent() < currentQuest.getTotal()) {
                if (currentQuest.getKilled().add(beKilledPlayer.getUniqueId().toString())) {
                    currentQuest.setCurrent(currentQuest.getCurrent() + 1);
                    if (currentQuest.getCurrent() >= currentQuest.getTotal()) {
                        final Player player = Bukkit.getPlayer(killerProfile.getPlayerUuid());
                        if (player != null) {
                            new PitQuestCompleteEvent(player, currentQuest).callEvent();
                        }
                    }
                }
            } else {
                currentQuest.setCurrent(currentQuest.getTotal());
            }
        }
        //process quest - end
    }

    private void handleCherryDrop(Player player, double dropMultiplier) {
        double normalizedMultiplier = Math.max(0, Math.min(1, dropMultiplier));
        boolean success = RandomUtil.hasSuccessfullyByChance(0.0001 * normalizedMultiplier);
        if (success) {
            player.getInventory().addItem(Cherry.INSTANCE.toItemStack());
        }
    }

    private void handleMythicItemDrop(PlayerProfile killerProfile, Player killer, LivingEntity beKilledPlayer, double dropMultiplier) {
        int enchantPerkLevel = -1;
        PerkData data = killerProfile.getUnlockedPerkMap().get("Mythicism");
        if (data != null && !UtilKt.hasRealMan(killer)) {
            enchantPerkLevel = data.getLevel();
        }
        if (enchantPerkLevel > -1) {
            double normalizedMultiplier = Math.max(0, Math.min(1, dropMultiplier));
            double chance = NewConfiguration.INSTANCE.getMythicDropChance(killer)
                    * (1 + (enchantPerkLevel - 1) * 0.02)
                    * normalizedMultiplier;
            int level = Utils.getEnchantLevel(killer.getInventory().getLeggings(), "pants_radar");
            if (level > 0) {
                chance = (1 + level * 0.3) * chance;
            }
            level = Utils.getEnchantLevel(killer.getItemInHand(), "pants_radar");
            if (level > 0) {
                chance = (1 + level * 0.3) * chance;
            }
            boolean b = RandomUtil.hasSuccessfullyByChance(chance);
            if (b) {
                AbstractPitItem item;
                if (enchantPerkLevel >= 4) {
                    item = (AbstractPitItem) RandomUtil.helpMeToChooseOne(new MythicBowItem(), new MythicSwordItem(), new MythicLeggingsItem());
                } else {
                    item = (AbstractPitItem) RandomUtil.helpMeToChooseOne(new MythicBowItem(), new MythicSwordItem());
                }

                ItemStack itemStack = item.toItemStack();

                if (InventoryUtil.isInvFull(killer.getInventory())) {
                    beKilledPlayer.getWorld().dropItemNaturally(beKilledPlayer.getLocation(), itemStack);
                } else {
                    InventoryUtil.addInvReverse(killer.getInventory(), itemStack);
                }

                CC.send(MessageType.MISC, killer, "&d&l神话武器! &7你在战斗中拾取了掉落的神话物品!");

                //fixme: change to sound system
                new BukkitRunnable() {
                    int task = 0;

                    @Override
                    public void run() {
                        killer.playSound(beKilledPlayer.getLocation(), Sound.NOTE_PLING, 1, 0.1F + (0.5F * task));
                        task++;

                        if (task >= 6) {
                            cancel();
                        }
                    }
                }.runTaskTimer(ThePit.getInstance(), 10, 5);
            }
        }
    }

    private void handleItemDrop(PlayerProfile killerProfile, Player killer, Player beKilledPlayer) {
        try {
            //lucky diamond
            boolean enabledLuckyDiamond = PlayerUtil.isPlayerChosePerk(killer, "LuckyDiamond");

            //drop armor
            for (ItemStack itemStack : beKilledPlayer.getInventory().getArmorContents()) {
                if (itemStack != null && itemStack.getType() != Material.AIR && CraftItemStack.asNMSCopy(itemStack).getItem() instanceof ItemArmor) {
                    if (itemStack.getType().name().contains("HELMET")) {
                        continue;
                    }
                    final Location killerLoc = killer.getLocation().clone();
                    //iron armor
                    if (itemStack.getType().name().contains("IRON")) {
                        int lodbrok = Utils.getEnchantLevel(killer.getInventory().getLeggings(), "lodbrok_enchant");
                        double dropChance = 0.30;
                        if (lodbrok == 1) {
                            dropChance = 0.40;
                        } else if (lodbrok == 2) {
                            dropChance = 0.55;
                        } else if (lodbrok >= 3) {
                            dropChance = 0.75;
                        }
                        if (!RandomUtil.hasSuccessfullyByChance(dropChance)) {
                            continue;
                        }
                        if (enabledLuckyDiamond && RandomUtil.hasSuccessfullyByChance(0.3)) {
                            itemStack = new ItemBuilder(Material.valueOf(itemStack.getType().name().replace("IRON", "DIAMOND")))
                                    .deathDrop(true)
                                    .canDrop(false)
                                    .canSaveToEnderChest(false)
                                    .internalName("lucky_diamond")
                                    .build();
                            //player.getWorld().dropItemNaturally(killer.getLocation(), itemStack);
                            ItemStack finalItemStack = itemStack;

                            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> Bukkit.getPluginManager().callEvent(new PlayerPickupItemEvent(killer, beKilledPlayer.getWorld().dropItemNaturally(killerLoc, finalItemStack), 1)));
                            continue;
                        }
                        ItemStack finalItemStack1 = itemStack;
                        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> beKilledPlayer.getWorld().dropItemNaturally(killer.getLocation(), finalItemStack1));
                        continue;
                    }
                    //diamond armor
                    if (itemStack.getType().name().contains("DIAMOND")) {
                        //drop shop diamond armor only
                        if (itemStack.getType().name().contains("HELMET")) {
                            continue;
                        }
                        if ("shopItem".equals(ItemUtil.getInternalName(itemStack))) {
                            ItemStack finalItemStack2 = itemStack;
                            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> beKilledPlayer.getWorld().dropItemNaturally(killerLoc, finalItemStack2));
                        }
                    }

                }
            }
        } catch (Exception e) {
            CC.printError(killer, e);
            CC.printError(beKilledPlayer, e);
        }
    }

    private void handleAddBounty(PlayerProfile killerProfile, Player killer, UUID victim) {
        if (killerProfile.isBot() || ThePit.getInstance().getEventFactory().getActiveEpicEvent() != null) return;
        int before = Math.max(0, killerProfile.getBounty());
        int cap = BountyRules.cap(PlayerUtil.isPlayerChosePerk(killer, "high_lander") && killerProfile.getStreakKills() >= 50);
        int added = bountyTracker.grow(killer.getUniqueId(), victim, before, cap, System.currentTimeMillis());
        if (added <= 0) return;
        int retained = added;
        if (Utils.getEnchantLevel(killer.getInventory().getLeggings(), "golden_handcuffs_enchant") > 0
                && !PlayerUtil.isVenom(killer) && !PlayerUtil.isSinkingMoonlight(killer)) {
            List<Player> recipients = cn.charlotte.pit.junxu.JunxuMessages.audience(killer.getUniqueId()).stream()
                    .filter(other -> !other.getUniqueId().equals(killer.getUniqueId()) && other.getWorld().equals(killer.getWorld()))
                    .filter(other -> {
                        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(other.getUniqueId());
                        return profile.isLoaded() && !profile.isBot() && profile.isInArena();
                    }).collect(java.util.stream.Collectors.toList());
            int[] capacity = new int[recipients.size()];
            for (int i = 0; i < recipients.size(); i++) {
                Player other = recipients.get(i);
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(other.getUniqueId());
                int ceiling = BountyRules.cap(PlayerUtil.isPlayerChosePerk(other, "high_lander") && profile.getStreakKills() >= 50);
                capacity[i] = Math.max(0, ceiling - profile.getBounty());
            }
            int[] shares = cn.charlotte.pit.reward.BountySharing.distribute(added, capacity, victim.hashCode());
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] <= 0) continue;
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(recipients.get(i).getUniqueId());
                profile.setBounty(profile.getBounty() + shares[i]);
                retained -= shares[i];
                ThePit.getInstance().getLogger().info("BOUNTY_SHARED source=" + killerProfile.getPlayerName()
                        + " recipient=" + profile.getPlayerName() + " added=" + shares[i] + " total=" + profile.getBounty());
            }
        }
        killerProfile.setBounty(before + retained);
        ThePit.getInstance().getLogger().info("BOUNTY_GROWTH player=" + killerProfile.getPlayerName()
                + " victim=" + victim + " added=" + retained + " budget=" + added + " total=" + killerProfile.getBounty());
        if (before / 500 != killerProfile.getBounty() / 500) {
            cn.charlotte.pit.junxu.JunxuMessages.broadcast(killer.getUniqueId(), MessageType.BOUNTY, "&6&l赏金! " + killerProfile.getFormattedName()
                    + " &7当前被以 &6" + killerProfile.getBounty() + "g &7悬赏!");
        }
    }

    private void handleBoardCastMessage(PlayerProfile killerProfile, PlayerProfile playerProfile, Player killer,
                                        LivingEntity beKilledPlayer, double totalCoins, double totalXp,
                                        boolean progressionEligible, double repeatMultiplier, boolean inactiveTarget, double hillMultiplier) {

        if (beKilledPlayer instanceof Player) {
            (((Player) beKilledPlayer)).playSound(beKilledPlayer.getLocation(), Sound.ZOMBIE_INFECT, 1, 1.5F);
        }

        String genesisStatus = "";
        if (progressionEligible && ThePit.getInstance().getPitConfig().isGenesisEnable()
                && killerProfile.getGenesisData().getTeam() != GenesisTeam.NONE) {
            if (killerProfile.getGenesisData().getTeam() == playerProfile.getGenesisData().getTeam()) {
                killerProfile.getGenesisData().setPoints(killerProfile.getGenesisData().getPoints() + 1);
                if (killerProfile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                    genesisStatus = " &b+1活动点数";
                }
                if (killerProfile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                    genesisStatus = " &c+1活动点数";
                }
            } else {
                killerProfile.getGenesisData().setPoints(killerProfile.getGenesisData().getPoints() + 2);
                if (killerProfile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                    genesisStatus = " &b+2活动点数";
                }
                if (killerProfile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                    genesisStatus = " &c+2活动点数";
                }
            }
        }

        String prefix = "击杀";
        final int streakNumberShort = killerProfile.addAndGetStreakNumberShort();
        switch (streakNumberShort) {
            case 2: {
                ThePit.getInstance().getSoundFactory().playSound("double_streak", killer);
                prefix = "双杀";
                break;
            }
            case 3: {
                ThePit.getInstance().getSoundFactory().playSound("triple_streak", killer);
                prefix = "三杀";
                break;
            }
            case 4: {
                ThePit.getInstance().getSoundFactory().playSound("quadra_streak", killer);
                prefix = "四杀";
                break;
            }
            case 5: {
                ThePit.getInstance().getSoundFactory().playSound("streak", killer);
                prefix = "五杀";
                break;
            }
            default: {
                if (streakNumberShort > 5) {
                    prefix = "多杀";
                    ThePit.getInstance().getSoundFactory().playSound("streak", killer);
                } else {
                    killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 1, 1.9F);
                }
            }
        }

        String hillStatus = hillMultiplier > 1 ? " &b(占山为王x" + numFormat.format(hillMultiplier) + ")" : "";
        String targetName = playerProfile.isBot() ? "§7" + playerProfile.getPlayerName() : RankUtil.getPlayerColoredName(beKilledPlayer.getUniqueId());
        String rewardSuffix = playerProfile.isBot() ? "" : getKillRewardSuffix(repeatMultiplier, inactiveTarget);
        if (totalXp > 0) {
            CC.send(MessageType.COMBAT, killer, CC.translate("&a&l" + prefix + "! " + targetName + " &6+" + numFormat.format(totalCoins) + "硬币 &b+" + numFormat.format(totalXp) + "经验值" + genesisStatus + hillStatus + rewardSuffix));
        } else {
            CC.send(MessageType.COMBAT, killer, CC.translate("&a&l" + prefix + "! " + targetName + " &6+" + numFormat.format(totalCoins) + "硬币" + genesisStatus + hillStatus + rewardSuffix));
        }

        String deathString = CC.translate("&c&l死亡! &7被 " + killerProfile.getFormattedName() + " &7击杀.");

        ChatComponentBuilder deathMsg = new ChatComponentBuilder(deathString)
                .append(new ChatComponentBuilder(CC.translate(" &e&l死亡回放"))
                        .setCurrentHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentBuilder(CC.translate("&7点击查看你的死亡回放")).create()))
                        .setCurrentClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/killRecap"))
                        .create());

        if (beKilledPlayer instanceof Player) {
            CC.send(MessageType.COMBAT, (Player) beKilledPlayer, deathMsg.create());
        }

    }

    private void handleGivePlayerKillReward(Player killer) {
        if ("rage_pit".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
            int limit = PlayerUtil.getPlayerHealItemLimit(killer);
            if (limit < 0) {
                limit = 2;
            }
            if (PlayerUtil.getPlayerHealItemAmount(killer) < limit) {
                killer.getInventory().addItem(new ItemBuilder(Material.BAKED_POTATO).name("&c&l愤怒的土豆").internalName("angry_potato").lore("", "&7这个土豆看起来", "&7好像格外暴躁...", "").deathDrop(true)
                        .removeOnJoin(true)
                        .canSaveToEnderChest(false)
                        .canDrop(false)
                        .isHealingItem(true)
                        .canTrade(false).build());
            }
        } else if (PlayerUtil.getAmountOfActiveHealingPerk(killer) == 0) {
            if (Utils.getEnchantLevel(killer.getInventory().getLeggings(), "steaks_enchant") > 0) {
                return;
            }
            if (PlayerUtil.getPlayerHealItemAmount(killer) < PlayerUtil.getPlayerHealItemLimit(killer)) {
                killer.getInventory().addItem(new ItemBuilder(Material.GOLDEN_APPLE).canDrop(false).canSaveToEnderChest(false).deathDrop(true)
                        .removeOnJoin(true)
                        .canSaveToEnderChest(false)
                        .canDrop(false)
                        .isHealingItem(true)
                        .canTrade(false).build());
            }
        }
    }

    private void dispatchDeathEffectsOnce(Player victim, Player killer, AtomicDouble coins, AtomicDouble exp) {
        // Public handleKill callers outside a death transaction have no retained state.
        if (processingDeaths.contains(victim.getUniqueId()) && !deathEffectsHandled.add(victim.getUniqueId())) return;
        handleDeathEffects(victim, killer, coins, exp);
    }

    private void handleDeathEffects(Player victim, Player killer, AtomicDouble coins, AtomicDouble exp) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(victim.getUniqueId());
        if (!profile.isLoaded() || profile.isBot() && (ThePit.getInstance().getPitBotManager() == null
                || !ThePit.getInstance().getPitBotManager().ownsProfile(profile))) return;
        for (IPlayerBeKilledByEntity ins : ThePit.getInstance().getPerkFactory().getPlayerBeKilledByEntities()) {
            AbstractPerk perk = (AbstractPerk) ins;
            int level = perk.getPlayerLevel(victim);
            if (level == -1) continue;
            try {
                if (killer != null) {
                    GameEffectListener.processBeKilledByEntity(ins, level, victim, killer, coins, exp);
                } else if (ins instanceof OverDriveMegaStreak) {
                    ins.handlePlayerBeKilledByEntity(level, victim, null, coins, exp);
                } else if (ins instanceof ToTheMoonMegaStreak) {
                    ((ToTheMoonMegaStreak) ins).rewardDeath(victim);
                }
            } catch (Exception exception) {
                ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "Death perk failed: " + perk.getInternalPerkName(), exception);
            }
        }
        // Enchant/JS death callbacks may require a real killer. Never pass a fake or null target.
        if (killer == null) return;
        for (IPlayerBeKilledByEntity ins : ThePit.getInstance().getEnchantmentFactor().getPlayerBeKilledByEntities()) {
            AbstractEnchantment enchant = (AbstractEnchantment) ins;
            if (!enchant.isEffectActive(victim, killer)) continue;
            ItemStack hand = victim.getItemInHand();
            for (ItemStack item : new ItemStack[] { hand != null && hand.getType() != Material.LEATHER_LEGGINGS ? hand : null, victim.getInventory().getLeggings() }) {
                if (item == null || item.getType() == Material.AIR) continue;
                int level = enchant.getItemEnchantLevel(item);
                if (level <= 0) continue;
                try {
                    GameEffectListener.processBeKilledByEntity(ins, level, victim, killer, coins, exp);
                } catch (Exception exception) {
                    ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                            "Death enchant failed: " + enchant.getClass().getSimpleName(), exception);
                }
            }
        }
    }

    private void handleGameEffect(PlayerProfile killerProfile, Player killer, LivingEntity player, AtomicDouble coinsAtomic, AtomicDouble expAtomic) {
        for (IPlayerKilledEntity ins : ThePit.getInstance().getPerkFactory()
                .getPlayerKilledEntities()) {
            AbstractPerk perk = (AbstractPerk) ins;
            runKillEffect(perk.getInternalPerkName(), coinsAtomic, expAtomic, () -> {
                if (ThePit.getInstance().getGame() != null && ThePit.getInstance().getGame().getDisabledPerks().stream()
                        .anyMatch(disabled -> disabled.getInternalPerkName().equals(perk.getInternalPerkName()))) return;
                int level = perk.getPlayerLevel(killer);
                if (level >= 0) GameEffectListener.processKilled(ins, level, killer, player, coinsAtomic, expAtomic);
            });
        }

        for (IPlayerKilledEntity ins : ThePit.getInstance().getEnchantmentFactor().getPlayerKilledEntities()) {
            AbstractEnchantment enchant = (AbstractEnchantment) ins;


            if (!enchant.isEffectActive(killer, player instanceof Player ? (Player) player : null)) continue;
            ItemStack hand = killer.getItemInHand();
            for (ItemStack item : new ItemStack[] {
                    hand != null && hand.getType() != Material.LEATHER_LEGGINGS ? hand : null,
                    killer.getInventory().getLeggings() }) {
                if (item == null || item.getType() == Material.AIR) continue;
                runKillEffect(enchant.getNbtName(), coinsAtomic, expAtomic, () -> {
                    int level = enchant.getItemEnchantLevel(item);
                    if (level > 0) GameEffectListener.processKilled(ins, level, killer, player, coinsAtomic, expAtomic);
                });
            }
        }

        //Genesis Boost Start
        if (killerProfile.getGenesisData().getTeam() == GenesisTeam.ANGEL && killerProfile.getGenesisData().getBoostTier() > 0) {
            expAtomic.getAndAdd(0.01 * killerProfile.getGenesisData().getBoostTier()
                    * (EconomyV2.active() ? ProgressionV2.base(killerProfile.getPrestige()) : expAtomic.get()));
        }
        if (killerProfile.getGenesisData().getTeam() == GenesisTeam.DEMON && killerProfile.getGenesisData().getBoostTier() > 0) {
            coinsAtomic.getAndAdd(0.01 * killerProfile.getGenesisData().getBoostTier()
                    * (EconomyV2.active() ? ProgressionV2.base(killerProfile.getPrestige()) : coinsAtomic.get()));
        }
        //Genesis Boost End
    }

    private void runKillEffect(String effect, AtomicDouble coins, AtomicDouble experience, Runnable callback) {
        double oldCoins = coins.get(), oldExperience = experience.get();
        try {
            RewardModifiers modifiers = KillRewardContext.modifiers();
            if (EconomyV2.active() && modifiers != null) modifiers.apply(effect, coins, experience, callback);
            else {
                callback.run();
                if (EconomyV2.observing() && modifiers != null) modifiers.observe(effect, oldCoins, oldExperience, coins.get(), experience.get());
            }
            if (!Double.isFinite(coins.get()) || !Double.isFinite(experience.get())) {
                throw new IllegalStateException("Non-finite reward modifier");
            }
        } catch (Exception failure) {
            coins.set(oldCoins); experience.set(oldExperience);
            ThePit.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                    "Kill effect failed; remaining effects and base reward continue: " + effect, failure);
        }
    }

    private void calculationKillReward(PlayerProfile killerProfile, PlayerProfile playerProfile, KillRecap killRecap, Player killer, LivingEntity victim, AtomicDouble totalCoinsAtomic, AtomicDouble totalXpAtomic) {
        double totalXp = totalXpAtomic.get();
        double totalCoins = totalCoinsAtomic.get();

        PerkData data = killerProfile.getUnlockedPerkMap().get("XPPrestigeBoost");
        if (data != null) {
            totalXp += data.getLevel();
        }

        if (killerProfile.getStreakKills() <= 3) {
            totalXp += 4;
            totalCoins += 4;

            killRecap.setNotStreakExp(4);
            killRecap.setNotStreakCoin(4);
        }

        double streakAddon = 0;
        double streakCoinAddon = 0;
        if (playerProfile.getStreakKills() != 0 && playerProfile.getStreakKills() % 10 == 0) {
            totalXp += (playerProfile.getStreakKills() / 10d) * 5;
            totalCoins += (playerProfile.getStreakKills() / 10d) * 5;

            streakAddon += (playerProfile.getStreakKills() / 10d) * 5;
            streakCoinAddon += (playerProfile.getStreakKills() / 10d) * 5;
        }

        if (KillRewardContext.crossedStreak(killer.getUniqueId(), killerProfile.getStreakKills(), 10)) {
            int sweaty = Math.max(
                    Utils.getEnchantLevel(killer.getItemInHand(), "sweaty_enchant"),
                    Utils.getEnchantLevel(killer.getInventory().getLeggings(), "sweaty_enchant")
            );
            double streakCap = 20;
            if (sweaty >= 3) {
                streakCap += 100;
            } else if (sweaty >= 2) {
                streakCap += 50;
            }
            double streakXp = Math.min(streakCap, Math.floor(killerProfile.getStreakKills() / 10d) * 5);
            if (sweaty > 0) {
                streakXp *= (1 + 0.20 * sweaty);
            }
            totalXp += streakXp;
            totalCoins += Math.min(20, (killerProfile.getStreakKills() / 10d) * 5);

            streakAddon += streakXp;
            streakCoinAddon += Math.min(20, (killerProfile.getStreakKills() / 10d) * 5);
        }

        killRecap.setStreakCoin(streakCoinAddon);
        killRecap.setStreakExp(streakAddon);

        double levelAddon = 0;
        if (playerProfile.getPrestige() - killerProfile.getPrestige() > 0) {
            totalXp += (playerProfile.getPrestige() - killerProfile.getPrestige()) * 7;
            totalCoins += (playerProfile.getPrestige() - killerProfile.getPrestige()) * 7;

            levelAddon += (playerProfile.getPrestige() - killerProfile.getPrestige()) * 7;
        }

        if (playerProfile.getLevel() - killerProfile.getLevel() >= 30) {
            totalXp += (playerProfile.getLevel() - killerProfile.getLevel()) / 30d * 3;
            totalCoins += (playerProfile.getLevel() - killerProfile.getLevel()) / 30d * 3;

            levelAddon += (playerProfile.getLevel() - killerProfile.getLevel()) / 30d * 3;
        }

        if (playerProfile.getLevel() <= 10) {
            totalXp -= levelAddon;
            totalCoins -= levelAddon;
            levelAddon = 0;
        } else if (playerProfile.getLevel() <= 30) {
            totalXp -= 0.75 * levelAddon;
            totalCoins -= 0.75 * levelAddon;
            levelAddon = 0.25 * levelAddon;
        } else if (playerProfile.getLevel() <= 60 && playerProfile.getLevel() < killerProfile.getLevel()) {
            totalXp -= 0.5 * levelAddon;
            totalCoins -= 0.5 * levelAddon;
            levelAddon = 0.5 * levelAddon;
        }

        if (victim instanceof Player && hasPremiumItem((Player) victim)) {
            if (!hasPremiumItem(killer)) {
                totalCoins += 10;
                totalXp += 10;

                levelAddon += 10;
            }
        }

        killRecap.setLevelDisparityExp(levelAddon);
        killRecap.setLevelDisparityCoin(levelAddon);

        if (EconomyV2.observing()) {
            RewardModifiers modifiers = KillRewardContext.modifiers();
            if (modifiers != null) {
                double side = ProgressionV2.sideScale(killerProfile.getPrestige());
                double goldGenesis = killerProfile.getGenesisData().getTeam() == GenesisTeam.DEMON
                        ? modifiers.base * .01 * killerProfile.getGenesisData().getBoostTier() : 0;
                double xpGenesis = killerProfile.getGenesisData().getTeam() == GenesisTeam.ANGEL
                        ? modifiers.base * .01 * killerProfile.getGenesisData().getBoostTier() : 0;
                double candidateGold = (modifiers.gold() - modifiers.fixedGold() + goldGenesis) * eventBoost
                        + (modifiers.fixedGold() + totalCoins - totalCoinsAtomic.get()) * side;
                double candidateXp = (modifiers.experience() - modifiers.fixedXp() + xpGenesis) * eventBoost
                        + (modifiers.fixedXp() + totalXp - totalXpAtomic.get()) * side;
                KillRewardContext.candidate(candidateGold, candidateXp * 4);
            }
        }
        if (EconomyV2.active()) {
            RewardModifiers modifiers = KillRewardContext.modifiers();
            double fixedGold = totalCoins - totalCoinsAtomic.get() + (modifiers == null ? 0 : modifiers.fixedGold());
            double fixedXp = totalXp - totalXpAtomic.get() + (modifiers == null ? 0 : modifiers.fixedXp());
            double side = ProgressionV2.sideScale(killerProfile.getPrestige());
            totalCoins = eventBoost * (totalCoins - fixedGold) + fixedGold * side;
            totalXp = eventBoost * (totalXp - fixedXp) + fixedXp * side;
        } else {
            totalCoins = eventBoost * totalCoins;
            totalXp = eventBoost * totalXp;
        }

        totalXpAtomic.set(totalXp);
        totalCoinsAtomic.set(totalCoins);
        //calculation kill reward - end
    }

    private KillRecap initializationKillRecap(PlayerProfile playerProfile, PlayerProfile killerProfile, Player killer, double totalCoins, double totalXp) {
        KillRecap killRecap = playerProfile.getKillRecap();

        killRecap.setKiller(killer.getUniqueId());

        killRecap.getAssistData().clear();
        killRecap.getPerk().clear();

        for (int i = 1; i < 5; i++) {
            PerkData perkData = killerProfile.getChosePerk().get(i);
            if (perkData != null) {
                killRecap.getPerk().add(perkData.getPerkInternalName());
            }
        }

        killRecap.setBaseCoin(totalCoins);
        killRecap.setBaseExp(totalXp);

        return killRecap;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onStreakChangeEvent(PitStreakKillChangeEvent event) {
        EventFactory factory = ThePit.getInstance().getEventFactory();
        if (factory.getActiveEpicEvent() != null) {
            event.setCancelled(true);
        }
    }

    public static boolean isNight() {
        if (!ThePit.getInstance().getPitConfig().isCurfewEnable()) {
            return false;
        }

        final Calendar instance = Calendar.getInstance();
        instance.setTimeInMillis(System.currentTimeMillis());
        final int hour = instance.get(Calendar.HOUR_OF_DAY);

        int start = ThePit.getInstance().getPitConfig().getCurfewStart();
        int end = ThePit.getInstance().getPitConfig().getCurfewEnd();
        return start <= end ? hour >= start && hour <= end : hour >= start || hour <= end;
    }

    private String getKillRewardSuffix(double repeatMultiplier, boolean inactiveTarget) {
        String bonus = eventBoost > 1 ? " &6(全服收益x" + numFormat.format(eventBoost) + ")" : "";
        return inactiveTarget ? bonus + " &7(目标连续无有效输出，收益x" + numFormat.format(repeatMultiplier) + ")" : bonus;
    }

    private static final class RepeatKillResult {
        private static final RepeatKillResult FULL_REWARD = new RepeatKillResult(1.0, true);

        private final double multiplier;
        private final boolean progressionEligible;

        private RepeatKillResult(double multiplier, boolean progressionEligible) {
            this.multiplier = multiplier;
            this.progressionEligible = progressionEligible;
        }
    }
}
