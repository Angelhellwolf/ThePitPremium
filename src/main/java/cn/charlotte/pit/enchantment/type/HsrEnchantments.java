package cn.charlotte.pit.enchantment.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.reward.CoinLedger;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.BotEnchantPolicy;
import cn.charlotte.pit.util.combat.CombatContext;
import cn.charlotte.pit.util.combat.PlayerLife;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native implementations of the former bundled HSR script enchantments. */
public final class HsrEnchantments {
    private HsrEnchantments() {
    }

    private abstract static class Base extends AbstractEnchantment {
        private static final int MAX_LEVEL = 3;

        private final String enchantName;
        private final String nbtName;
        private final EnchantmentRarity rarity;

        Base(String enchantName, String nbtName, EnchantmentRarity rarity,
             boolean weapon, boolean bow, boolean armor) {
            this.enchantName = enchantName;
            this.nbtName = nbtName;
            this.rarity = rarity;
            this.weaponOnly = weapon;
            this.bowOnly = bow;
            this.armorOnly = armor;
            this.rodOnly = false;
        }

        @Override
        public final String getEnchantName() {
            return enchantName;
        }

        @Override
        public final int getMaxEnchantLevel() {
            return MAX_LEVEL;
        }

        @Override
        public final String getNbtName() {
            return nbtName;
        }

        @Override
        public final EnchantmentRarity getRarity() {
            return rarity;
        }

        @Override
        public final Cooldown getCooldown() {
            return null;
        }

        final int level(int enchantLevel) {
            return Math.max(1, Math.min(MAX_LEVEL, enchantLevel));
        }

        static long metadataLong(Entity entity, String key) {
            MetadataValue value = metadata(entity, key);
            return value == null ? 0L : value.asLong();
        }

        static int metadataInt(Entity entity, String key) {
            MetadataValue value = metadata(entity, key);
            return value == null ? 0 : value.asInt();
        }

        static String metadataString(Entity entity, String key) {
            MetadataValue value = metadata(entity, key);
            return value == null ? "" : value.asString();
        }

        static void setMetadata(Entity entity, String key, Object value) {
            entity.setMetadata(key, new FixedMetadataValue(ThePit.getInstance(), value));
        }

        static String decimal(double value, int places) {
            return String.format(Locale.ROOT, "%." + places + "f", value);
        }

        private static MetadataValue metadata(Entity entity, String key) {
            for (MetadataValue value : entity.getMetadata(key)) {
                if (value.getOwningPlugin() == ThePit.getInstance()) return value;
            }
            return null;
        }
    }

    public static final class AbundanceRegenEnchant extends Base implements IPlayerDamaged {
        private static final String COOLDOWN_KEY = "hsr_abundance_regen_cd";

        public AbundanceRegenEnchant() {
            super("回春赐福", "hsr_abundance_regen_enchant", EnchantmentRarity.RARE, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7战技: 受到攻击后 &f1秒 &7回复 &a" + decimal(0.3 * level, 1) + "❤ &7生命值"
                    + "/s&7冷却: &f8秒";
        }

        @Override
        public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage,
                                        AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            if (cancel.get() || damage <= 0) return;
            long now = System.currentTimeMillis();
            if (now < metadataLong(myself, COOLDOWN_KEY)) return;
            setMetadata(myself, COOLDOWN_KEY, now + 8_000L);

            Object life = PlayerLife.capture(myself);
            Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                if (!PlayerLife.isCurrent(myself, life) || cancel.get() || myself.getMaxHealth() <= 0) return;
                PlayerUtil.heal(myself, 0.6 * level);
            }, 20L);
        }
    }

    public static final class AbundanceSalvageEnchant extends Base implements IPlayerShootEntity {
        private static final String COOLDOWN_KEY = "hsr_abundance_salvage_cd";

        public AbundanceSalvageEnchant() {
            super("丰饶回收", "hsr_abundance_salvage_enchant", EnchantmentRarity.RARE, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7战技: 箭矢命中玩家时, 回复 &a" + decimal(0.25 * level, 2) + "❤ &7生命值"
                    + "/s&7冷却: &f4秒";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            long now = System.currentTimeMillis();
            if (now < metadataLong(attacker, COOLDOWN_KEY)) return;
            setMetadata(attacker, COOLDOWN_KEY, now + 4_000L);
            if (attacker.getMaxHealth() > 0) PlayerUtil.heal(attacker, 0.5 * level);
        }
    }

    public static final class AimingProtocolEnchant extends Base implements IPlayerShootEntity {
        private static final String TARGET_KEY = "hsr_aiming_target";
        private static final String UNTIL_KEY = "hsr_aiming_until";

        public AimingProtocolEnchant() {
            super("瞄准协议", "hsr_aiming_protocol_enchant", EnchantmentRarity.NORMAL, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7箭矢命中玩家时伤害 &c+" + (6 + 2 * level) + "%"
                    + "/s&7&f3秒&7内再次命中同一目标时改为 &c+" + (10 + 4 * level) + "%"
                    + "/s&7潜行射出时额外 &c+6%";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            long now = System.currentTimeMillis();
            String targetId = target.getUniqueId().toString();
            boolean follow = now <= metadataLong(attacker, UNTIL_KEY)
                    && targetId.equals(metadataString(attacker, TARGET_KEY));
            double boost = follow ? 0.10 + 0.04 * level : 0.06 + 0.02 * level;
            if (CombatContext.shotSneaking(attacker)) boost += 0.06;
            boostDamage.getAndAdd(boost);
            setMetadata(attacker, TARGET_KEY, targetId);
            setMetadata(attacker, UNTIL_KEY, now + 3_000L);
        }
    }

    public static final class DestructionResonanceEnchant extends Base implements IAttackEntity {
        public DestructionResonanceEnchant() {
            super("毁灭共振", "hsr_destruction_resonance_enchant", EnchantmentRarity.RARE, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7战技: 当你的生命值低于等于 &c50% &7时"
                    + "/s&7造成的伤害 &c+" + (6 * level) + "% &7并额外造成 &c"
                    + decimal(0.2 * level, 1) + "❤ &f真实&7伤害";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            double maxHealth = attacker.getMaxHealth();
            if (maxHealth <= 0 || attacker.getHealth() / maxHealth > 0.50) return;
            boostDamage.getAndAdd(0.06 * level);
            finalDamage.getAndAdd(0.4 * level);
        }
    }

    public static final class DestructionRiposteEnchant extends Base
            implements IPlayerDamaged, IAttackEntity, IPlayerShootEntity {
        private static final String UNTIL_KEY = "hsr_destruction_riposte_until";

        public DestructionRiposteEnchant() {
            super("毁灭回敬", "hsr_destruction_riposte_enchant", EnchantmentRarity.RARE, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            return "&7天赋: 受到攻击后 &f3秒 &7内/s&7你造成的伤害 &c+" + (6 * level(enchantLevel)) + "%";
        }

        @Override
        public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage,
                                        AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            setMetadata(myself, UNTIL_KEY, System.currentTimeMillis() + 3_000L);
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            boostIfReady(enchantLevel, attacker, boostDamage);
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            boostIfReady(enchantLevel, attacker, boostDamage);
        }

        private void boostIfReady(int enchantLevel, Player attacker, AtomicDouble boostDamage) {
            if (System.currentTimeMillis() <= metadataLong(attacker, UNTIL_KEY)) {
                boostDamage.getAndAdd(0.06 * level(enchantLevel));
            }
        }
    }

    public static final class EruditionSpreadEnchant extends Base implements IPlayerShootEntity {
        public EruditionSpreadEnchant() {
            super("智能扩散", "hsr_erudition_spread_enchant", EnchantmentRarity.NORMAL, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7箭矢命中玩家时, 对目标 &e4格 &7内其他玩家造成 &c"
                    + decimal(0.25 * level, 2) + "❤ &f真实&7伤害"
                    + "/s&7若范围内存在其他玩家, 该箭伤害 &c+" + (6 + 4 * level) + "%";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            boolean hitOther = false;
            for (Player nearby : PlayerUtil.getNearbyPlayers(target.getLocation(), 4)) {
                if (nearby.getUniqueId().equals(attacker.getUniqueId())
                        || nearby.getUniqueId().equals(target.getUniqueId())
                        || !BotEnchantPolicy.effectAllowed(attacker, nearby)) continue;
                hitOther = true;
                PlayerUtil.damage(attacker, nearby, PlayerUtil.DamageType.TRUE, 0.5 * level, true);
            }
            if (hitOther) boostDamage.getAndAdd(0.06 + 0.04 * level);
        }
    }

    public static final class EruditionStanceEnchant extends Base implements IAttackEntity {
        private static final String TARGET_KEY = "hsr_erudition_target";
        private static final String UNTIL_KEY = "hsr_erudition_until";
        private static final String STACKS_KEY = "hsr_erudition_stacks";

        public EruditionStanceEnchant() {
            super("智能解析", "hsr_erudition_stance_enchant", EnchantmentRarity.NORMAL, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7连续命中同一名玩家时叠加 &b解析"
                    + "/s&7每层使该次伤害 &c+" + (4 + 2 * level)
                    + "% &7(最高 &e3 &7层, &f4秒&7内未命中同一目标则重置)";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            long now = System.currentTimeMillis();
            String targetId = target.getUniqueId().toString();
            int stacks = metadataInt(attacker, STACKS_KEY);
            if (now > metadataLong(attacker, UNTIL_KEY)
                    || !targetId.equals(metadataString(attacker, TARGET_KEY))) {
                stacks = 0;
            }
            stacks = Math.min(3, stacks + 1);
            setMetadata(attacker, TARGET_KEY, targetId);
            setMetadata(attacker, UNTIL_KEY, now + 4_000L);
            setMetadata(attacker, STACKS_KEY, stacks);
            boostDamage.getAndAdd((0.04 + 0.02 * level) * stacks);
        }
    }

    public static final class FollowupArrowEnchant extends Base implements IPlayerShootEntity {
        public FollowupArrowEnchant() {
            super("回合外箭", "hsr_followup_arrow_enchant", EnchantmentRarity.NORMAL, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            return "&7天赋: 每 &f3 &7次弓箭命中触发 &b追加攻击"
                    + "/s&7额外造成 &c" + decimal(0.25 * level(enchantLevel), 2) + "❤ &f真实&7伤害";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (CombatContext.bowHits(attacker) % 3 == 0) {
                finalDamage.getAndAdd(0.5 * level(enchantLevel));
            }
        }
    }

    public static final class HarmonyRepriseEnchant extends Base implements IPlayerKilledEntity {
        public HarmonyRepriseEnchant() {
            super("同谐回响", "hsr_harmony_reprise_enchant", EnchantmentRarity.RARE, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            int reward = 4 * level;
            return "&7终结技: 击杀玩家时, 回复 &a" + decimal(0.5 * level, 1) + "❤ &7生命值"
                    + "/s&7并额外获得 &6+" + reward + " &7硬币 与 &b+" + reward + " &7经验值"
                    + "/s&7金币与经验加成排除 Bot，回复效果保留";
        }

        @Override
        public void handlePlayerKilled(int enchantLevel, Player myself, Entity target,
                                       AtomicDouble coins, AtomicDouble experience) {
            int level = level(enchantLevel);
            if (myself.getMaxHealth() > 0) PlayerUtil.heal(myself, 1.0 * level);
            coins.getAndAdd(4.0 * level);
            experience.getAndAdd(4.0 * level);
        }
    }

    public static final class HarmonyTrackEnchant extends Base implements IPlayerKilledEntity {
        public HarmonyTrackEnchant() {
            super("同谐行迹", "hsr_harmony_track_enchant", EnchantmentRarity.RARE, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int reward = 3 * level(enchantLevel);
            return "&7终结技: 击杀玩家时, 额外获得 &6+" + reward + " &7硬币 与 &b+" + reward + " &7经验值"
                    + "/s&7可与其他加成叠加，击杀 Bot 不获得此加成";
        }

        @Override
        public void handlePlayerKilled(int enchantLevel, Player myself, Entity target,
                                       AtomicDouble coins, AtomicDouble experience) {
            int level = level(enchantLevel);
            coins.getAndAdd(3.0 * level);
            experience.getAndAdd(3.0 * level);
        }
    }

    public static final class HuntAfterimageEnchant extends Base
            implements IPlayerKilledEntity, IAttackEntity, IPlayerShootEntity {
        private static final String READY_KEY = "hsr_hunt_afterimage_ready";

        public HuntAfterimageEnchant() {
            super("巡猎掠影", "hsr_hunt_afterimage_enchant", EnchantmentRarity.RARE, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7终结技: 击杀玩家后获得 &b掠影"
                    + "/s&7掠影: &c速度 I &f(00:0" + (2 + level)
                    + ") &7并使下一次命中伤害 &c+" + (8 * level) + "% &7(持续 &f3秒&7)";
        }

        @Override
        public void handlePlayerKilled(int enchantLevel, Player myself, Entity target,
                                       AtomicDouble coins, AtomicDouble experience) {
            int level = level(enchantLevel);
            myself.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60 + 20 * (level - 1), 0), true);
            setMetadata(myself, READY_KEY, System.currentTimeMillis() + 3_000L);
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            consume(enchantLevel, attacker, boostDamage);
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            consume(enchantLevel, attacker, boostDamage);
        }

        private void consume(int enchantLevel, Player attacker, AtomicDouble boostDamage) {
            long now = System.currentTimeMillis();
            if (now > metadataLong(attacker, READY_KEY)) return;
            setMetadata(attacker, READY_KEY, 0L);
            boostDamage.getAndAdd(0.08 * level(enchantLevel));
        }
    }

    public static final class HuntChainEnchant extends Base implements IPlayerKilledEntity, IAttackEntity {
        private static final String READY_KEY = "hsr_hunt_chain_ready";

        public HuntChainEnchant() {
            super("巡猎连斩", "hsr_hunt_chain_enchant", EnchantmentRarity.NORMAL, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7击杀玩家后进入 &b连斩 &f(6秒)"
                    + "/s&7期间近战伤害 &c+" + (8 + 6 * level) + "% &7并获得 &b速度 I"
                    + "/s&7连斩期间再次击杀会刷新持续时间";
        }

        @Override
        public void handlePlayerKilled(int enchantLevel, Player myself, Entity target,
                                       AtomicDouble coins, AtomicDouble experience) {
            setMetadata(myself, READY_KEY, System.currentTimeMillis() + 6_000L);
            myself.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0), true);
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (System.currentTimeMillis() <= metadataLong(attacker, READY_KEY)) {
                boostDamage.getAndAdd(0.08 + 0.06 * level(enchantLevel));
            }
        }
    }

    public static final class MultimillionaireEnchant extends Base implements IAttackEntity, IPlayerShootEntity {
        public MultimillionaireEnchant() {
            super("千万富翁", "multimillionaire", EnchantmentRarity.RARE, true, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            String boost = level == 1 ? "16.7" : level == 2 ? "33.3" : "50";
            int cost = 20 + 15 * level;
            return "&7攻击玩家时消耗 &6" + cost + " 硬币"
                    + "/s&7使该次伤害 &c+" + boost + "%"
                    + "/s&7硬币不足时不触发";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (!cancel.get() && !target.getUniqueId().equals(attacker.getUniqueId())) {
                trySpend(enchantLevel, attacker, boostDamage);
            }
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (!cancel.get() && !target.getUniqueId().equals(attacker.getUniqueId())) {
                trySpend(enchantLevel, attacker, boostDamage);
            }
        }

        private void trySpend(int enchantLevel, Player attacker, AtomicDouble boostDamage) {
            int level = level(enchantLevel);
            cn.charlotte.pit.data.PlayerProfile profile =
                    cn.charlotte.pit.data.PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
            int cost = 20 + 15 * level;
            if (profile.getCoins() < cost) return;
            CombatContext.afterAccepted(() -> CoinLedger.set(
                    profile,
                    Math.max(0, profile.getCoins() - cost),
                    CoinLedger.Flow.SPEND,
                    "multimillionaire"
            ));
            boostDamage.getAndAdd(level == 1 ? 1.0 / 6.0 : level == 2 ? 1.0 / 3.0 : 0.5);
        }
    }

    public static final class NihilityLagEnchant extends Base implements IPlayerDamaged {
        private static final String COOLDOWN_KEY = "hsr_nihility_lag_cd";

        public NihilityLagEnchant() {
            super("虚无阻滞", "hsr_nihility_lag_enchant", EnchantmentRarity.NORMAL, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7受到玩家攻击时, 该次伤害 &9-" + (4 + 4 * level) + "%"
                    + "/s&7并对攻击者施加 &c缓慢 I &f("
                    + decimal((20 + 10 * level) / 20.0, 1) + "秒) &7(冷却 &f3秒&7)";
        }

        @Override
        @PlayerOnly
        public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage,
                                        AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            boostDamage.getAndAdd(-(0.04 + 0.04 * level));
            long now = System.currentTimeMillis();
            if (now < metadataLong(myself, COOLDOWN_KEY)) return;
            setMetadata(myself, COOLDOWN_KEY, now + 3_000L);
            ((Player) attacker).addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW, 20 + 10 * level, 0), true);
        }
    }

    public static final class PreservationCounterEnchant extends Base implements IPlayerDamaged, IAttackEntity {
        private static final String READY_KEY = "hsr_preservation_counter_ready";
        private static final String COOLDOWN_KEY = "hsr_preservation_counter_cd";

        public PreservationCounterEnchant() {
            super("存护反击", "hsr_preservation_counter_enchant", EnchantmentRarity.RARE, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            return "&7天赋: 受到攻击时, 获得 1 次 &b存护反击"
                    + "/s&7下一次近战命中伤害 &c+" + (15 * level(enchantLevel))
                    + "% &7(持续 &f3秒&7, 冷却 &f7秒&7)";
        }

        @Override
        public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage,
                                        AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            long now = System.currentTimeMillis();
            if (now < metadataLong(myself, COOLDOWN_KEY)) return;
            setMetadata(myself, READY_KEY, now + 3_000L);
            setMetadata(myself, COOLDOWN_KEY, now + 7_000L);
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (System.currentTimeMillis() > metadataLong(attacker, READY_KEY)) return;
            setMetadata(attacker, READY_KEY, 0L);
            boostDamage.getAndAdd(0.15 * level(enchantLevel));
        }
    }

    public static final class QuantumEntangleEnchant extends Base implements IPlayerShootEntity {
        public QuantumEntangleEnchant() {
            super("量子纠缠", "hsr_quantum_entangle_enchant", EnchantmentRarity.RARE, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            return "&7战技: 箭矢命中玩家时, 有 &f" + (8 * level(enchantLevel)) + "% &7几率施加 &c量子迟滞"
                    + "/s&7量子迟滞: &c缓慢 I &f(00:03) &7与 &c虚弱 I &f(00:02)";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            if (Math.random() >= 0.08 * level) return;
            Player player = (Player) target;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0), true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0), true);
        }
    }

    public static final class RiftExecutionEnchant extends Base implements IAttackEntity {
        public RiftExecutionEnchant() {
            super("裂界终结", "hsr_rift_execution_enchant", EnchantmentRarity.RARE, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7终结技: 当目标生命值不高于 &c30% &7时"
                    + "/s&7你造成的伤害 &c+" + (8 * level) + "% &7且额外造成 &c"
                    + decimal(0.2 * level, 1) + "❤ &f真实&7伤害";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            Player player = (Player) target;
            double maxHealth = player.getMaxHealth();
            if (maxHealth <= 0 || player.getHealth() / maxHealth > 0.30) return;
            int level = level(enchantLevel);
            boostDamage.getAndAdd(0.08 * level);
            finalDamage.getAndAdd(0.4 * level);
        }
    }

    public static final class ShieldPulseEnchant extends Base implements IPlayerDamaged {
        private static final String COOLDOWN_KEY = "hsr_shield_pulse_cd";

        public ShieldPulseEnchant() {
            super("护盾脉冲", "hsr_shield_pulse_enchant", EnchantmentRarity.NORMAL, false, false, true);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7受到玩家伤害 &9-" + (4 + 2 * level) + "%"
                    + "/s&7若自身拥有 &6生命吸收&7, 该次伤害额外 &9-" + (8 + 4 * level)
                    + "% &7(冷却 &f3秒&7)";
        }

        @Override
        @PlayerOnly
        public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage,
                                        AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            boostDamage.getAndAdd(-(0.04 + 0.02 * level));
            if (!hasAbsorption(myself)) return;
            long now = System.currentTimeMillis();
            if (now < metadataLong(myself, COOLDOWN_KEY)) return;
            setMetadata(myself, COOLDOWN_KEY, now + 3_000L);
            boostDamage.getAndAdd(-(0.08 + 0.04 * level));
        }

        private boolean hasAbsorption(Player player) {
            try {
                return ((CraftPlayer) player).getHandle().getAbsorptionHearts() > 0;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }

    public static final class StarrailSniperEnchant extends Base implements IPlayerShootEntity {
        public StarrailSniperEnchant() {
            super("星轨狙击", "hsr_starrail_sniper_enchant", EnchantmentRarity.RARE, false, true, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            return "&7箭矢命中距离每满 &e8 &7格, 伤害 &c+" + (6 + 3 * level) + "% &7(最高3层)"
                    + "/s&7距离达到 &e16 &7格时额外造成 &c" + decimal(0.25 * level, 2)
                    + "❤ &f真实&7伤害";
        }

        @Override
        @PlayerOnly
        @cn.charlotte.pit.parm.type.BowOnly
        public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                      AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            double distance = attacker.getLocation().distance(target.getLocation());
            int stacks = Math.min(3, (int) Math.floor(distance / 8.0));
            if (stacks <= 0) return;
            boostDamage.getAndAdd((0.06 + 0.03 * level) * stacks);
            if (distance >= 16.0) finalDamage.getAndAdd(0.5 * level);
        }
    }

    public static final class StellarBrandEnchant extends Base implements IAttackEntity {
        public StellarBrandEnchant() {
            super("星核灼印", "hsr_stellar_brand_enchant", EnchantmentRarity.RARE, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            int level = level(enchantLevel);
            int bonus = level == 1 ? 6 : level == 2 ? 10 : 14;
            return "&7战技: 近战命中玩家时, 有 &f" + (12 * level)
                    + "% &7几率施加 &c燃烧 &f(00:0" + (1 + level) + ")"
                    + "/s&7对处于 &c燃烧 &7的玩家造成的伤害 &c+" + bonus + "%";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            int level = level(enchantLevel);
            Player player = (Player) target;
            if (Math.random() < 0.12 * level) {
                player.setFireTicks(Math.max(player.getFireTicks(), 40 + 20 * (level - 1)));
            }
            if (player.getFireTicks() > 0) {
                boostDamage.getAndAdd(level == 1 ? 0.06 : level == 2 ? 0.10 : 0.14);
            }
        }
    }

    public static final class ToughnessSlashEnchant extends Base implements IAttackEntity {
        public ToughnessSlashEnchant() {
            super("破韧斩击", "hsr_toughness_slash_enchant", EnchantmentRarity.NORMAL, true, false, false);
        }

        @Override
        public String getUsefulnessLore(int enchantLevel) {
            return "&7天赋: 每 &f4 &7次近战命中触发 &b弱点击破"
                    + "/s&7额外造成 &c" + decimal(0.3 * level(enchantLevel), 1)
                    + "❤ &f真实&7伤害, 并施加 &c缓慢 I &f(00:02)";
        }

        @Override
        @PlayerOnly
        public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage,
                                       AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
            if (CombatContext.meleeHits(attacker) % 4 != 0) return;
            int level = level(enchantLevel);
            finalDamage.getAndAdd(0.6 * level);
            ((Player) target).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0), true);
        }
    }
}
