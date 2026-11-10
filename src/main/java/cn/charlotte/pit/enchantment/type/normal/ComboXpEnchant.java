package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.util.combat.CombatContext;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.reward.RewardService;
import cn.charlotte.pit.reward.RewardSource;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

@WeaponOnly
public class ComboXpEnchant extends AbstractEnchantment implements IAttackEntity, IActionDisplayEnchant {
    @Override
    public String getEnchantName() {
        return "强力击: 经验";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "combo_xp_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return ("&7每 &e5 &7次击中获得 &b+" + (12 + enchantLevel * 8) + " 经验值") + "/s" + "&7仅对真人目标触发（排除 Bot）";
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cn.charlotte.pit.reward.HitRewardRules.eligible(attacker, target, damage, cancel)) return;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        if (CombatContext.meleeHits(attacker) % 5 == 0) {
            CombatContext.afterAccepted(() -> RewardService.grant(profile, RewardSource.ENCHANT, 0, 12 + enchantLevel * 8));
        }
    }

    @Override
    public String getText(int level, Player player) {
        return getHitActionText(player, 5);
    }
}
