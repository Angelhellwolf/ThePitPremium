package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.KillRecap;
import cn.charlotte.pit.data.sub.QuestData;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.enchantment.type.normal.SteaksEnchant;
import cn.charlotte.pit.enchantment.type.normal.MixedCombatEnchant;
import cn.charlotte.pit.enchantment.type.rare.EscapePodEnchant;
import cn.charlotte.pit.enchantment.param.event.NotPlayerOnly;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.event.OriginalTimeChangeEvent;
import cn.charlotte.pit.event.PitDamageEvent;
import cn.charlotte.pit.event.PitDamagePlayerEvent;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.game.Game;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.*;
import cn.charlotte.pit.parm.type.BowOnly;
import cn.charlotte.pit.parm.type.ThrowOnly;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkFactory;
import cn.charlotte.pit.quest.AbstractQuest;
import cn.charlotte.pit.quest.QuestFactory;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.combat.SecondaryDamage;
import cn.charlotte.pit.util.combat.CombatContext;
import cn.charlotte.pit.util.combat.TrueDamageRules;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.item.ItemUtil;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.SneakyThrows;
import net.minecraft.server.v1_8_R3.EnchantmentManager;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.MobEffectList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/2 12:40
 */
@AutoRegister
public class GameEffectListener implements Listener {
    private final DecimalFormat numFormatTwo = new DecimalFormat("0.00");

    @SneakyThrows
    public static void processKilled(IPlayerKilledEntity ins, int level, Player killer, Entity target, AtomicDouble coin, AtomicDouble exp) {
        if (level != -1) {
            final Method method = ins.getClass().getMethod("handlePlayerKilled", int.class, Player.class, Entity.class, AtomicDouble.class, AtomicDouble.class);
            if (method.isAnnotationPresent(PlayerOnly.class)) {
                if (target instanceof Player) {
                    Player player = (Player) target;
                    ins.handlePlayerKilled(level, killer, player, coin, exp);
                }
            } else if (method.isAnnotationPresent(NotPlayerOnly.class)) {
                if (!(target instanceof Player)) {
                    ins.handlePlayerKilled(level, killer, target, coin, exp);
                }
            } else {
                ins.handlePlayerKilled(level, killer, target, coin, exp);
            }
        }
    }

    @SneakyThrows
    public static void processBeKilledByEntity(IPlayerBeKilledByEntity ins, int level, Player myself, Entity target, AtomicDouble coin, AtomicDouble exp) {
        if (level != -1) {
            final Method method = ins.getClass().getMethod("handlePlayerBeKilledByEntity", int.class, Player.class, Entity.class, AtomicDouble.class, AtomicDouble.class);
            if (method.isAnnotationPresent(PlayerOnly.class)) {
                if (target instanceof Player) {
                    Player player = (Player) target;
                    ins.handlePlayerBeKilledByEntity(level, myself, player, coin, exp);
                }
            } else if (method.isAnnotationPresent(NotPlayerOnly.class)) {
                if (!(target instanceof Player)) {
                    ins.handlePlayerBeKilledByEntity(level, myself, target, coin, exp);
                }
            } else {
                ins.handlePlayerBeKilledByEntity(level, myself, target, coin, exp);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFired(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK)) {
            event.setCancelled(true);
            Player player = (Player) event.getEntity();
            double damage = event.getFinalDamage();
            if (damage <= 0 || Double.isNaN(damage) || Double.isInfinite(damage)) {
                return;
            }
            if (player.getHealth() <= damage) {
                player.damage(player.getMaxHealth() * 100);
            } else {
                player.setHealth(Math.max(0.1, player.getHealth() - damage));
            }

            PlayerProfile.getPlayerProfileByUuid(player.getUniqueId())
                    .setNoDamageAnimations(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void modifyLeatherArmor(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            if ("kings_helmet".equals(ItemUtil.getInternalName(((Player) event.getEntity()).getInventory().getHelmet()))) {
                //0.12是每点伤害 铁裤和皮革裤子的减免差距，所以减去0.12*伤害值，以增加皮革裤子为铁裤防御
                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) - 0.06 * event.getDamage());
            }

            if ("mythic_leggings".equals(ItemUtil.getInternalName(((Player) event.getEntity()).getInventory().getLeggings()))) {
                if (ItemUtil.getItemStringData(((Player) event.getEntity()).getInventory().getLeggings(), "mythic_color").equals("dark")) {
                    return;
                }
                //0.12是每点伤害 铁裤和皮革裤子的减免差距，所以减去0.12*伤害值，以增加皮革裤子为铁裤防御
                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) - 0.12 * event.getDamage());
            }
        }
    }

    @EventHandler
    public void addEnchantToArrow(EntityShootBowEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player) {
            cn.charlotte.pit.util.combat.ProjectileEnchantments.capture(event.getProjectile(), event.getBow());
            event.getProjectile().setMetadata("pit_shot_sneaking", new FixedMetadataValue(ThePit.getInstance(), ((Player) event.getEntity()).isSneaking()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void completeHitEffects(EntityDamageByEntityEvent event) { CombatContext.complete(event); }

    @EventHandler
    public void onTimeChange(OriginalTimeChangeEvent event) {
        CC.boardCast("Time change to: " + event.getTime());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (SecondaryDamage.applyTrueDamage(event)) return;
        try (CombatContext context = CombatContext.open(event)) {
        boolean secondary = SecondaryDamage.isHit(event);
        if (event.getDamager() instanceof Player && !secondary) {
            //修正伤害
            final Player attacker = (Player) event.getDamager();
            if (attacker.getItemInHand() == null || attacker.getItemInHand().getType() == Material.AIR || attacker.getItemInHand().getType() == Material.FISHING_ROD) {
                event.setDamage(1);
            } else {
                final EntityPlayer entityPlayer = ((CraftPlayer) attacker).getHandle();
                float f = (float) entityPlayer.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue();
                if (f > 9) {
                    event.setDamage(0);
                }
            }
        }


        PerkFactory perkFactory = ThePit.getInstance()
                .getPerkFactory();

        EventFactory eventFactory = ThePit.getInstance().getEventFactory();
        EnchantmentFactor enchantmentFactor = ThePit.getInstance().getEnchantmentFactor();
        QuestFactory questFactory = ThePit.getInstance().getQuestFactory();

        AtomicDouble finalDamage = new AtomicDouble(0);
        AtomicDouble boostDamage = new AtomicDouble(1);
        AtomicBoolean cancel = new AtomicBoolean(false);
        Player damager = null;

        final Game game = ThePit.getInstance().getGame();

        if (event.getDamager() instanceof Player && !secondary) {
            damager = (Player) event.getDamager();

            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(damager.getUniqueId());
            if (NewConfiguration.INSTANCE.getNoobProtect() && profile.getPrestige() <= 0 && profile.getLevel() < NewConfiguration.INSTANCE.getNoobProtectLevel()) {
                boostDamage.getAndAdd(NewConfiguration.INSTANCE.getNoobDamageBoost() - 1);
            }

            //perk handler
            for (IAttackEntity ins : perkFactory.getAttackEntities()) {
                AbstractPerk perk = (AbstractPerk) ins;
                if (game.getDisabledPerks().stream().anyMatch(ignoredPerk -> ignoredPerk.getInternalPerkName().equals(perk.getInternalPerkName()))) {
                    continue;
                }
                int level = perk.getPlayerLevel(damager);
                processAttackEntity(ins, level, damager, event.getEntity(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
            }



            //enchant handler
            //we should check has somber enchant on leggings
            boolean somberFound = PlayerUtil.isEquippingSomber(damager);

            if (!somberFound && event.getEntity() instanceof Player) {
                somberFound = PlayerUtil.isEquippingSomber((Player) event.getEntity());
            }

            //checking damager has marked combo venom?
            boolean comboVenomFound = false;
            if (damager.hasMetadata("combo_venom")) {
                if (damager.getMetadata("combo_venom").get(0).asLong() > System.currentTimeMillis()) {
                    comboVenomFound = true;
                }
            }
            PlayerProfile damagerProfile = PlayerProfile.getPlayerProfileByUuid(damager.getUniqueId());

            //if found, dont process enchant

            for (IAttackEntity ins : enchantmentFactor.getAttackEntities()) {
                AbstractEnchantment enchant = (AbstractEnchantment) ins;
                if (!enchant.isEffectActive(damager, event.getEntity() instanceof Player ? (Player) event.getEntity() : null)) {
                    continue;
                }

                int level = ins instanceof MixedCombatEnchant
                        ? ((MixedCombatEnchant) ins).getMarkedMeleeLevel(damager, event.getEntity())
                        : enchant.getItemEnchantLevel(damager.getItemInHand());
                if (ins instanceof MixedCombatEnchant || (damager.getItemInHand() != null && damager.getItemInHand().getType() != Material.AIR && damager.getItemInHand().getType() != Material.LEATHER_LEGGINGS)) {
                    processAttackEntity(ins, level, damager, event.getEntity(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }
                if (damager.getInventory().getLeggings() != null && damager.getInventory().getLeggings().getType() != Material.AIR) {
                    level = enchant.getItemEnchantLevel(damager.getInventory().getLeggings());
                    processAttackEntity(ins, level, damager, event.getEntity(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }
            }


            for (IAttackEntity ins : questFactory.getAttackEntities()) {
                AbstractQuest quest = (AbstractQuest) ins;
                QuestData currentQuest = profile.getCurrentQuest();
                if (currentQuest != null && currentQuest.getInternalName().equals(quest.getQuestInternalName())) {
                    processAttackEntity(ins, currentQuest.getLevel(), damager, event.getEntity(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }
            }
        } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            damager = (Player) (((Projectile) event.getDamager()).getShooter());
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(damager.getUniqueId());

            if (NewConfiguration.INSTANCE.getNoobProtect() && profile.getPrestige() <= 0 && profile.getLevel() < NewConfiguration.INSTANCE.getNoobProtectLevel()) {
                boostDamage.getAndAdd(NewConfiguration.INSTANCE.getNoobDamageBoost() - 1);
            }

            for (IPlayerShootEntity ins : perkFactory.getPlayerShootEntities()) {
                AbstractPerk perk = (AbstractPerk) ins;
                if (game.getDisabledPerks().stream().anyMatch(disabled -> disabled.getInternalPerkName().equals(perk.getInternalPerkName()))) continue;
                int level = perk.getPlayerLevel(damager);
                processShootEntity(ins, level, damager, event.getEntity(), event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
            }

            //enchant handler
            //we should check has somber enchant on leggings
            boolean somberFound = PlayerUtil.isEquippingSomber(damager);
            if (!somberFound && event.getEntity() instanceof Player) {
                somberFound = PlayerUtil.isEquippingSomber((Player) event.getEntity());
            }
            //checking damager has marked combo venom?
            boolean comboVenomFound = false;
            if (damager.getMetadata("combo_venom") != null) {
                final long now = System.currentTimeMillis();
                final List<MetadataValue> metadata = damager.getMetadata("combo_venom");
                final Optional<MetadataValue> first = metadata.stream()
                        .filter(metadataValue -> metadataValue.getOwningPlugin() instanceof ThePit && now <= metadataValue.asLong())
                        .findFirst();
                if (first.isPresent()) {
                    comboVenomFound = true;
                }
            }
            //if found, dont process enchant

            final Projectile projectile = (Projectile) event.getDamager();
            if (projectile.hasMetadata("enchant")) {
                final MetadataValue value = projectile.getMetadata("enchant").get(0);
                final String enchants = value.asString();
                final String[] split = enchants.split(";");
                for (String enchantStr : split) {
                    final String[] enchStr = enchantStr.split(":", 2);
                    if (enchStr.length != 2) continue;
                    final String enchantName = enchStr[0];
                    final int enchantValue;
                    try { enchantValue = Integer.parseInt(enchStr[1]); }
                    catch (NumberFormatException ignored) { continue; }
                    if (enchantValue <= 0) continue;

                    final AbstractEnchantment enchantment = enchantmentFactor.getEnchantmentMap().get(enchantName);
                    if (!(enchantment instanceof IPlayerShootEntity)) continue;

                    if (!enchantment.isEffectActive(damager, event.getEntity() instanceof Player ? (Player) event.getEntity() : null)) {
                        continue;
                    }

                    this.processShootEntity((IPlayerShootEntity) enchantment, enchantValue, damager, event.getEntity(), event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }
            }

            processArmorShootEnchantments(enchantmentFactor, damager, event.getEntity(),
                    event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);


            for (IPlayerShootEntity ins : questFactory.getPlayerShootEntities()) {
                AbstractQuest quest = (AbstractQuest) ins;
                QuestData currentQuest = profile.getCurrentQuest();
                if (currentQuest != null && currentQuest.getInternalName().equals(quest.getQuestInternalName())) {
                    processShootEntity(ins, currentQuest.getLevel(), damager, event.getEntity(), event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }
            }
        }

        if (event.getEntity() instanceof Player) {

            Player player = (Player) event.getEntity();
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(event.getEntity().getUniqueId());

            if (NewConfiguration.INSTANCE.getNoobProtect() && profile.getPrestige() <= 0 && profile.getLevel() < NewConfiguration.INSTANCE.getNoobProtectLevel()) {
                boostDamage.getAndAdd(NewConfiguration.INSTANCE.getNoobDamageReduce() - 1);
            }

            if (PlayerUtil.isEquippingAngelChestplate(player)) {
                cn.charlotte.pit.enchantment.type.genesis.GuardianEnchant guardian = new cn.charlotte.pit.enchantment.type.genesis.GuardianEnchant();
                if (guardian.isEffectActive(player, CombatContext.playerSource(event.getDamager()))) {
                    int guardianLevel = Math.max(1, Utils.getEnchantLevel(player.getInventory().getChestplate(), "guardian_enchant"));
                    boostDamage.getAndAdd(-0.01 * guardian.getProtectionValue(guardianLevel));
                }
            }

            if (event.getDamager() instanceof Player) {
                damager = (Player) event.getDamager();
                for (IPlayerDamaged ins : perkFactory.getPlayerDamageds()) {
                    AbstractPerk perk = (AbstractPerk) ins;
                    if (game.getDisabledPerks().stream().anyMatch(ignoredPerk -> ignoredPerk.getInternalPerkName().equals(perk.getInternalPerkName()))) {
                        continue;
                    }
                    int level = perk.getPlayerLevel(player);
                    processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }

                //enchant handler
                //we should check has somber enchant on leggings
                boolean somberFound = PlayerUtil.isEquippingSomber(damager);

                if (!somberFound && event.getEntity() instanceof Player) {
                    somberFound = PlayerUtil.isEquippingSomber((Player) event.getEntity());
                }

                //if found, dont process enchant

                for (IPlayerDamaged ins : enchantmentFactor.getPlayerDamageds()) {
                    AbstractEnchantment enchant = (AbstractEnchantment) ins;

                    if (!enchant.isEffectActive(player, damager)) {
                        continue;
                    }

                    //当神话之甲手持时不应该触发附魔
                    int level = enchant.getItemEnchantLevel(player.getItemInHand());
                    if (player.getItemInHand() != null && player.getItemInHand().getType() != Material.AIR && player.getItemInHand().getType() != Material.LEATHER_LEGGINGS) {
                        processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                    if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().getType() != Material.AIR) {
                        level = enchant.getItemEnchantLevel(player.getInventory().getLeggings());
                        processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                }


                for (IPlayerDamaged ins : questFactory.getPlayerDamageds()) {
                    AbstractQuest quest = (AbstractQuest) ins;
                    QuestData currentQuest = profile.getCurrentQuest();
                    if (currentQuest != null && currentQuest.getInternalName().equals(quest.getQuestInternalName())) {
                        processPlayerDamaged(ins, currentQuest.getLevel(), player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                }
            } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
                damager = (Player) ((Projectile) event.getDamager()).getShooter();
                for (IPlayerDamaged ins : perkFactory.getPlayerDamageds()) {
                    AbstractPerk perk = (AbstractPerk) ins;
                    if (game.getDisabledPerks().stream().anyMatch(ignoredPerk -> ignoredPerk.getInternalPerkName().equals(perk.getInternalPerkName()))) {
                        continue;
                    }
                    int level = perk.getPlayerLevel(player);
                    processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                }


                //enchant handler
                //we should check has somber enchant on leggings
                boolean somberFound = PlayerUtil.isEquippingSomber(damager);

                if (!somberFound && event.getEntity() instanceof Player) {
                    somberFound = PlayerUtil.isEquippingSomber((Player) event.getEntity());
                }


                //if found, dont process enchant

                for (IPlayerDamaged ins : enchantmentFactor.getPlayerDamageds()) {
                    AbstractEnchantment enchant = (AbstractEnchantment) ins;
                    if (!enchant.isEffectActive(player, damager)) {
                        continue;
                    }
                    int level = enchant.getItemEnchantLevel(player.getItemInHand());
                    if (player.getItemInHand() != null && player.getItemInHand().getType() != Material.AIR && player.getItemInHand().getType() != Material.LEATHER_LEGGINGS) {
                        processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                    if (player.getInventory().getLeggings() != null && player.getInventory().getLeggings().getType() != Material.AIR) {
                        level = enchant.getItemEnchantLevel(player.getInventory().getLeggings());
                        processPlayerDamaged(ins, level, player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                }


                for (IPlayerDamaged ins : questFactory.getPlayerDamageds()) {
                    AbstractQuest quest = (AbstractQuest) ins;
                    QuestData currentQuest = profile.getCurrentQuest();
                    if (currentQuest != null && currentQuest.getInternalName().equals(quest.getQuestInternalName())) {
                        processPlayerDamaged(ins, currentQuest.getLevel(), player, event.getDamager(), event.getFinalDamage(), finalDamage, boostDamage, cancel);
                    }
                }
            }
        }

        if (cancel.get()) {
            event.setCancelled(true);
            return;
        }

        if (damager != null && !secondary) {
            SteaksEnchant.applySteakBoost(damager, boostDamage);
        }

        // Vanilla fishing hooks can deal zero damage. The fishing enchant adds
        // its normal damage before this stage; do not grant it to every hook.
        event.setDamage(Math.max(event.getDamager() instanceof FishHook ? 0 : 1, event.getDamage())
                * Math.max(0.2, boostDamage.get()) + context.getExtraNormalDamage());
        double convertedDamage = context.convertedDamage(event.getDamage());
        if (convertedDamage > 0) {
            event.setDamage(event.getDamage() - convertedDamage);
            finalDamage.addAndGet(convertedDamage);
        }
        context.applyArmorPenalty();


        if (damager != null) {
            new PitDamageEvent(damager, event.getFinalDamage(), event.getDamage()).callEvent();
        }


        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (damager != null) {
                new PitDamagePlayerEvent(damager, event.getFinalDamage(), event.getDamage(), player).callEvent();
            }

            // Execute-style sentinel damage is deliberately unblockable. Ordinary true damage
            // respects the same resistance and mirror rules as direct ability damage.
            boolean trueResistance = false;
            int mirror = 0;
            if (finalDamage.get() > 0 || context.hasFinishers()) {
                double outgoingScale = cn.charlotte.pit.quest.QuestRules.trueDamageMultiplier(damager) * TrueDamageRules.damageMultiplier(player, damager);
                if (finalDamage.get() > 0 && finalDamage.get() < 1000) finalDamage.set(finalDamage.get() * outgoingScale);
                trueResistance = TrueDamageRules.hasResistance(player, damager);
                if (!trueResistance) mirror = TrueDamageRules.mirrorLevel(player, damager);
                context.applyFinishers(player, event.getFinalDamage(), finalDamage, trueResistance || mirror > 0, outgoingScale);
            }
            if (finalDamage.get() > 0 && finalDamage.get() < 1000) {
                if (trueResistance) {
                    finalDamage.set(0);
                } else {
                    if (mirror > 0) {
                        double reflected = TrueDamageRules.reflectable(player, mirror, finalDamage.get());
                        finalDamage.set(0);
                        final Player mirrorTarget = damager;
                        final Object life = mirrorTarget == null ? null : cn.charlotte.pit.util.combat.PlayerLife.capture(mirrorTarget);
                        if (reflected > 0 && mirrorTarget != null) {
                            Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                                if (!event.isCancelled() && cn.charlotte.pit.util.combat.PlayerLife.isCurrent(mirrorTarget, life)) {
                                    PlayerUtil.damage(player, mirrorTarget, PlayerUtil.DamageType.TRUE, reflected, false);
                                }
                            });
                        }
                    }
                }
            }
            player.setLastDamageCause(event);
            if (damager != null) {
                ((CraftPlayer) player).getHandle().killer = ((CraftPlayer) damager).getHandle();
            }

            final double healthBefore = player.getHealth();
            final double trueDamage = Math.max(0, finalDamage.get());
            final double remainingHealth = healthBefore - trueDamage;
            if (CombatListener.INSTANCE != null) {
                CombatListener.INSTANCE.recordExtraDamage(event,
                        Math.min(healthBefore, trueDamage), healthBefore, remainingHealth <= 0);
            }
            if (remainingHealth <= 0.0D) {
                // Make the original EntityDamageByEntityEvent lethal so Bukkit handles the death/killer attribution.
                // Avoid calling Player#damage() here (it creates a new damage event and can lose/override attribution).
                event.setDamage(player.getHealth() + 10000.0D);
                return;
            }
            player.setHealth(remainingHealth);
            EscapePodEnchant.triggerIfNeeded(player);
        }
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if (profile.getPlayerOption().isDebugDamageMessage()) {
                player.sendMessage(CC.translate("&7受到伤害(Damage/Final Damage): &c" + numFormatTwo.format(event.getDamage()) + "&7/&c" + numFormatTwo.format(event.getFinalDamage())));
            }
        }
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if (profile.getPlayerOption().isDebugDamageMessage()) {
                player.sendMessage(CC.translate("&7造成伤害(Damage/Final Damage): &c" + numFormatTwo.format(event.getDamage()) + "&7/&c" + numFormatTwo.format(event.getFinalDamage())));
                final EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
                final double value = entityPlayer.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).getValue();
                final float enchantDamage = EnchantmentManager.a(entityPlayer.bA(), ((CraftLivingEntity) event.getEntity()).getHandle().getMonsterType());
                final boolean critical = entityPlayer.fallDistance > 0.0F && !entityPlayer.onGround && !entityPlayer.k_() && !entityPlayer.V() && !entityPlayer.hasEffect(MobEffectList.BLINDNESS) && entityPlayer.vehicle == null;
                player.sendMessage(CC.translate("&7基础: &c" + value + "&7,附魔伤害: &c" + enchantDamage + "&7,暴击: &c" + critical));
            }
        }
    }

    }

    void processArmorShootEnchantments(EnchantmentFactor factory, Player shooter, Entity target,
                                       Entity projectile, double damage, AtomicDouble finalDamage,
                                       AtomicDouble boostDamage, AtomicBoolean cancel) {
        ItemStack leggings = shooter.getInventory().getLeggings();
        if (leggings == null || leggings.getType() == Material.AIR) return;
        for (IPlayerShootEntity handler : factory.getPlayerShootEntities()) {
            AbstractEnchantment enchant = (AbstractEnchantment) handler;
            if (!enchant.isEffectActive(shooter, target instanceof Player ? (Player) target : null)) continue;
            int level = enchant.getItemEnchantLevel(leggings);
            if (level > 0) {
                processShootEntity(handler, level, shooter, target, projectile,
                        damage, finalDamage, boostDamage, cancel);
            }
        }
    }

    @EventHandler
    public void onItemDamaged(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        AtomicBoolean atomicBoolean = new AtomicBoolean();
        for (IItemDamage itemDamage : ThePit.getInstance()
                .getEnchantmentFactor()
                .getiItemDamages()) {
            AbstractEnchantment enchant = (AbstractEnchantment) itemDamage;
            int level = enchant.getItemEnchantLevel(item);
            if (level > 0 && enchant.isEffectActive(event.getPlayer(), null))
                itemDamage.handleItemDamaged(level, item, event.getPlayer(), atomicBoolean);
        }
        if (atomicBoolean.get()) {
            event.setCancelled(true);
        }
    }

    @SneakyThrows
    private void processShootEntity(IPlayerShootEntity ins, int level, Player damager, Entity target, Entity damageSource, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (level != -1) {
            Method handleShootEntity = ins.getClass().getMethod("handleShootEntity", int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);

            if (handleShootEntity.isAnnotationPresent(BowOnly.class) && !(damageSource instanceof Arrow)) {
                return;
            }
            if (handleShootEntity.isAnnotationPresent(ThrowOnly.class) && damageSource instanceof Arrow) {
                return;
            }
            if (ins.getClass().isAnnotationPresent(PlayerOnly.class) || handleShootEntity.isAnnotationPresent(PlayerOnly.class)) {
                if (target instanceof Player) {
                    Player player = (Player) target;
                    ins.handleShootEntity(level, damager, player, damage, finalDamage, boostDamage, cancel);
                }
            } else if (ins.getClass().isAnnotationPresent(NotPlayerOnly.class) || handleShootEntity.isAnnotationPresent(NotPlayerOnly.class)) {
                if (!(target instanceof Player)) {
                    ins.handleShootEntity(level, damager, target, damage, finalDamage, boostDamage, cancel);
                }
            } else {
                ins.handleShootEntity(level, damager, target, damage, finalDamage, boostDamage, cancel);
            }
        }
    }

    @SneakyThrows
    private void processPlayerDamaged(IPlayerDamaged ins, int level, Player player, Entity damager, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (level != -1) {
            final Method method = ins.getClass().getMethod("handlePlayerDamaged", int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
            if (method.isAnnotationPresent(PlayerOnly.class) || ins.getClass().isAnnotationPresent(PlayerOnly.class)) {
                Player damagerPlayer = CombatContext.playerSource(damager);
                if (damagerPlayer != null) {
                    ins.handlePlayerDamaged(level, player, damagerPlayer, damage, finalDamage, boostDamage, cancel);
                }
            } else if (method.isAnnotationPresent(NotPlayerOnly.class)) {
                if (!(damager instanceof Player)) {
                    ins.handlePlayerDamaged(level, player, damager, damage, finalDamage, boostDamage, cancel);
                }
            } else {
                ins.handlePlayerDamaged(level, player, damager, damage, finalDamage, boostDamage, cancel);
            }
        }
    }

    @SneakyThrows
    private void processAttackEntity(IAttackEntity ins, int level, Player damager, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (level != -1) {
            final Method method = ins.getClass().getMethod("handleAttackEntity", int.class, Player.class, Entity.class, double.class, AtomicDouble.class, AtomicDouble.class, AtomicBoolean.class);
            if (method.isAnnotationPresent(PlayerOnly.class)) {
                if (target instanceof Player) {
                    Player player = (Player) target;
                    ins.handleAttackEntity(level, damager, player, damage, finalDamage, boostDamage, cancel);
                }
            } else if (method.isAnnotationPresent(NotPlayerOnly.class)) {
                if (!(target instanceof Player) && damager == null) {
                    ins.handleAttackEntity(level, damager, target, damage, finalDamage, boostDamage, cancel);
                }
            } else {
                ins.handleAttackEntity(level, damager, target, damage, finalDamage, boostDamage, cancel);
            }
        }
    }

}
