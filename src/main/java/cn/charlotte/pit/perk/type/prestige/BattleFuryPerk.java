package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.Passive;
import cn.charlotte.pit.perk.PerkType;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Passive
public class BattleFuryPerk extends AbstractPerk implements IAttackEntity, IPlayerShootEntity {

    private static final Map<UUID, ComboData> comboDataMap = new HashMap<>();

    @Override
    public String getInternalPerkName() {
        return "battle_fury_perk";
    }

    @Override
    public String getDisplayName() {
        return "愈战愈勇";
    }

    @Override
    public Material getIcon() {
        return Material.BLAZE_POWDER;
    }

    @Override
    public double requireCoins() {
        return 0;
    }

    @Override
    public double requireRenown(int level) {
        return 10 + (level - 1) * 5;
    }

    @Override
    public int requirePrestige() {
        return 1;
    }

    @Override
    public int requireLevel() {
        return 0;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("&7当无伤击中同一个目标达标时,");
        lines.add("&7下一次攻击额外造成伤害.");
        lines.add("&7I: 连击4次后下一击伤害 &c+30%");
        lines.add("&7II: 连击3次后下一击伤害 &c+35%");
        lines.add("&7III: 连击3次后下一击伤害 &c+40%, 且获得 &b速度 II &f(00:04)");
        return lines;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public PerkType getPerkType() {
        return PerkType.PERK;
    }

    @Override
    public void onPerkActive(Player player) {

    }

    @Override
    public void onPerkInactive(Player player) {
        comboDataMap.remove(player.getUniqueId());
    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        processHit(enchantLevel, attacker, (Player) target, boostDamage);
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        processHit(enchantLevel, attacker, (Player) target, boostDamage);
    }

    private void processHit(int level, Player attacker, Player target, AtomicDouble boostDamage) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(attacker.getUniqueId());
        ComboData data = comboDataMap.computeIfAbsent(attacker.getUniqueId(), ignored -> new ComboData());
        long currentDamageMark = profile.getLastDamageAt();
        boolean damagedSinceLastRecord = data.lastDamageAt != currentDamageMark;

        if (damagedSinceLastRecord) {
            data.readyBonus = false;
            data.hits = 0;
        }

        if (data.readyBonus) {
            boostDamage.getAndAdd(getBoost(level));
            data.readyBonus = false;

            if (level >= 3) {
                attacker.removePotionEffect(PotionEffectType.SPEED);
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 4, 1), true);
            }
        }

        if (damagedSinceLastRecord || !target.getUniqueId().equals(data.lastTarget)) {
            data.hits = 1;
        } else {
            data.hits++;
        }

        data.lastDamageAt = currentDamageMark;
        data.lastTarget = target.getUniqueId();

        if (data.hits >= getRequiredHit(level)) {
            data.hits = 0;
            data.readyBonus = true;
        }
    }

    private int getRequiredHit(int level) {
        return level <= 1 ? 4 : 3;
    }

    private double getBoost(int level) {
        if (level <= 1) {
            return 0.30D;
        }
        if (level == 2) {
            return 0.35D;
        }
        return 0.40D;
    }

    private static class ComboData {
        private UUID lastTarget;
        private int hits;
        private long lastDamageAt = -1L;
        private boolean readyBonus;
    }
}
