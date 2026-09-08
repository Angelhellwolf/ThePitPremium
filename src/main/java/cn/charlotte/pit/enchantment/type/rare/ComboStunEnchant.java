package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.util.combat.CombatContext;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/27 17:01
 */
@WeaponOnly
public class ComboStunEnchant extends AbstractEnchantment implements IAttackEntity, IActionDisplayEnchant {

    private final DecimalFormat numFormat = new DecimalFormat("0.0");
    private final HashMap<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "强力击: 失衡";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "combo_stun_enchant";
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
        return "&7每 &e5 &7次攻击对敌人施加 &8缓慢 VIII&7, &8挖掘疲劳 XI &7与 &a跳跃提升 I &7(持续" + numFormat.format(getDuration(enchantLevel)) + "秒) (对每名玩家8秒冷却)";
    }

    private double getDuration(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 0.5;
            case 2:
                return 0.8;
            default:
                return 1.2;
        }
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(target instanceof Player) || cancel.get()) return;
        Player victim = (Player) target;
        UUID key = victim.getUniqueId();
        if (cooldown.getOrDefault(key, new Cooldown(0)).hasExpired() && CombatContext.meleeHits(attacker) % 5 == 0) {
            cooldown.put(key, new Cooldown(8, TimeUnit.SECONDS));
            int ticks = (int) Math.round(getDuration(enchantLevel) * 20);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 7), true);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, ticks, 10), true);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 0), true);
        }
    }

    @Override
    public String getText(int level, Player player) {
        int hit = (player.getItemInHand() != null && player.getItemInHand().getType() == Material.BOW ? CombatContext.bowHits(player) : CombatContext.meleeHits(player));
        int require = 5;
        if (cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)).hasExpired()) {
            return (hit % require == 0 ? "&a&l✔" : "&e&l" + (require - hit % require));
        } else {
            return getCooldownActionText(cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)));
        }
    }
}
