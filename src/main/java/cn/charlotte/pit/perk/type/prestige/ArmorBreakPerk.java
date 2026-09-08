package cn.charlotte.pit.perk.type.prestige;

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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Passive
public class ArmorBreakPerk extends AbstractPerk implements IAttackEntity, IPlayerShootEntity {
    @Override
    public String getInternalPerkName() {
        return "armor_break_perk";
    }

    @Override
    public String getDisplayName() {
        return "破甲";
    }

    @Override
    public Material getIcon() {
        return Material.IRON_AXE;
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
        lines.add("&7命中穿戴四件护甲的玩家时,");
        lines.add("&7I / II / III: 普通伤害 &c+5% / +10% / +15%");
        lines.add("&7近战与弓箭均生效,受护甲与抗性减免");
        lines.add("&7不再附加真实或必中伤害");
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

    }

    @Override
    @PlayerOnly
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cancel.get() && enchantLevel > 0 && countEquippedArmor((Player) target) == 4) {
            boostDamage.getAndAdd(getDamageBonus(enchantLevel));
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cancel.get() && enchantLevel > 0 && countEquippedArmor((Player) target) == 4) {
            boostDamage.getAndAdd(getDamageBonus(enchantLevel));
        }
    }

    private int countEquippedArmor(Player player) {
        int count = 0;
        for (ItemStack itemStack : player.getInventory().getArmorContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                count++;
            }
        }
        return count;
    }

    private double getDamageBonus(int level) {
        if (level <= 1) {
            return 0.05D;
        }
        if (level == 2) {
            return 0.10D;
        }
        return 0.15D;
    }
}
