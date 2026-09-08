package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.reward.CoinTransfers;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.WeaponOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/7 18:00
 */
@WeaponOnly
public class PitPocketEnchant extends AbstractEnchantment implements IAttackEntity {

    private final Map<UUID, Cooldown> COOLDOWN = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "天坑钱包";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "pit_pocket_enchant";
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
        return ("&7攻击真人玩家时最多偷取对方 &6" + getSteal(enchantLevel) + " 硬币 &7(" + getCooldownSeconds(enchantLevel) + "秒冷却)") + "/s" + "&7仅对真人目标触发（排除 Bot）";
    }

    private int getSteal(int enchantLevel) {
        return 10 + 5 * enchantLevel;
    }

    private int getCooldownSeconds(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 25;
            case 2:
                return 20;
            default:
                return 13;
        }
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!(target instanceof Player) || cancel.get() || damage <= 0) return;
        cn.charlotte.pit.util.combat.CombatContext.afterAccepted(() -> {
        COOLDOWN.putIfAbsent(attacker.getUniqueId(), new Cooldown(0));
        Player targetPlayer = (Player) target;
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        PlayerProfile targetProfile = PlayerProfile.getPlayerProfileByUuid(targetPlayer.getUniqueId());
        if (COOLDOWN.get(attacker.getUniqueId()).hasExpired()) {
            double steal = CoinTransfers.transfer(targetProfile, profile, getSteal(enchantLevel));
            if (steal <= 0) return;
            COOLDOWN.put(attacker.getUniqueId(), new Cooldown(getCooldownSeconds(enchantLevel), TimeUnit.SECONDS));
            attacker.sendMessage(CC.translate("&6&l偷取硬币! &7你从 " + targetProfile.getFormattedName() + " &7处偷取了 &6" + steal + " 硬币 &7!"));
        }
        });
    }

}
