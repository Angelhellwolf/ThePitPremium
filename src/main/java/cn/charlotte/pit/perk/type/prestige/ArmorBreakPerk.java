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
        lines.add("&7命中目标穿戴护甲超过3件时,");
        lines.add("&7I:  附带 &c0.5❤ &f必中&7伤害");
        lines.add("&7II: 附带 &c0.75❤ &f必中&7伤害");
        lines.add("&7III: 附带 &c1❤ &f必中&7伤害");
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
        if (countEquippedArmor((Player) target) > 3) {
            finalDamage.getAndAdd(getTrueDamage(enchantLevel));
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (countEquippedArmor((Player) target) > 3) {
            finalDamage.getAndAdd(getTrueDamage(enchantLevel));
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

    private double getTrueDamage(int level) {
        if (level <= 1) {
            return 1.0D; // 0.5❤
        }
        if (level == 2) {
            return 1.5D; // 0.75❤
        }
        return 2.0D; // 1❤
    }
}
