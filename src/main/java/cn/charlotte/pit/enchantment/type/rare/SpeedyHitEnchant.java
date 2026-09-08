package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/27 14:34
 */
@WeaponOnly
public class SpeedyHitEnchant extends AbstractEnchantment implements IAttackEntity {
    private final java.util.Map<java.util.UUID, Cooldown> activations = new java.util.HashMap<>();
    @Override
    public String getEnchantName() {
        return "迅捷打击";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "SpeedyHit";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return new Cooldown(getCooldownSeconds(1), TimeUnit.SECONDS);
    }

    private int getCooldownSeconds(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 3;
            case 2:
                return 2;
            default:
                return 1;
        }
    }

    private int getDuration(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 7;
            default:
                return 9;
        }
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7攻击玩家时获得 &b速度 I &7(00:0" + getDuration(enchantLevel) + ")" +
                "/s&7此附魔每 &f" + getCooldownSeconds(enchantLevel) + " &7秒只能触发一次.";
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(target instanceof Player) || cancel.get() || !activations.getOrDefault(attacker.getUniqueId(), new Cooldown(0)).hasExpired()) return;
        activations.put(attacker.getUniqueId(), new Cooldown(getCooldownSeconds(enchantLevel), TimeUnit.SECONDS));
        cn.charlotte.pit.util.combat.CombatPotions.refresh(attacker, new PotionEffect(PotionEffectType.SPEED, 20 * getDuration(enchantLevel), 0));
    }
}
