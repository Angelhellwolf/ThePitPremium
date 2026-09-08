package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class RetroGravityMicrocosmEnchant extends AbstractEnchantment implements IPlayerDamaged, IAttackEntity {

    private static final Map<UUID, Map<UUID, Integer>> hitCount = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> lastAirHit = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> bonusUntil = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> bonusStacks = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "反重力微宇宙";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "rgm_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        String extra = "";
        if (enchantLevel >= 2) {
            extra += "/s&7并在30秒内对其额外造成 &c1.5❤ &f真实&7伤害 (可叠加)";
        }
        if (enchantLevel >= 3) {
            extra += "/s&7并对其造成 &c0.5❤ &7的&f真实&7伤害";
        }
        return "&7连续三次被同一名未落地的玩家击中时恢复自身 &c1.25❤" + extra
                + "/s&71秒内的滞空连击不增加计数,落地攻击会重置计数.";
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(attacker instanceof Player)) {
            return;
        }
        Player damager = (Player) attacker;
        Map<UUID, Integer> counts = hitCount.computeIfAbsent(myself.getUniqueId(), k -> new HashMap<>());
        Map<UUID, Long> last = lastAirHit.computeIfAbsent(myself.getUniqueId(), k -> new HashMap<>());
        if (damager.isOnGround()) {
            counts.remove(damager.getUniqueId());
            last.remove(damager.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = last.get(damager.getUniqueId());
        if (previous != null && now - previous < 1000L) {
            last.put(damager.getUniqueId(), now);
            return;
        }
        last.put(damager.getUniqueId(), now);
        int count = counts.getOrDefault(damager.getUniqueId(), 0) + 1;
        if (count >= 3) {
            counts.remove(damager.getUniqueId());
            PlayerUtil.heal(myself, 2.5);
            if (enchantLevel >= 2) {
                Map<UUID, Long> until = bonusUntil.computeIfAbsent(myself.getUniqueId(), k -> new HashMap<>());
                Map<UUID, Integer> stacks = bonusStacks.computeIfAbsent(myself.getUniqueId(), k -> new HashMap<>());
                int previousStacks = until.getOrDefault(damager.getUniqueId(), 0L) > now ? stacks.getOrDefault(damager.getUniqueId(), 0) : 0;
                until.put(damager.getUniqueId(), now + 30000L);
                stacks.put(damager.getUniqueId(), previousStacks + 1);
            }
            if (enchantLevel >= 3) {
                PlayerUtil.damage(myself, damager, PlayerUtil.DamageType.TRUE, 1.0, true);
            }
        } else {
            counts.put(damager.getUniqueId(), count);
        }
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (enchantLevel < 2 || !(target instanceof Player)) {
            return;
        }
        Map<UUID, Long> until = bonusUntil.get(attacker.getUniqueId());
        Map<UUID, Integer> stacks = bonusStacks.get(attacker.getUniqueId());
        if (until == null || stacks == null) {
            return;
        }
        Long expire = until.get(target.getUniqueId());
        if (expire == null || expire < System.currentTimeMillis()) {
            return;
        }
        int stack = stacks.getOrDefault(target.getUniqueId(), 0);
        finalDamage.getAndAdd(3.0 * stack);
    }
}
