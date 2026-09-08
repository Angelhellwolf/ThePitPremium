package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/29 19:01
 */
@cn.charlotte.pit.perk.Passive
public class HeresyPerk extends AbstractPerk implements cn.charlotte.pit.parm.listener.IAttackEntity, cn.charlotte.pit.parm.listener.IPlayerShootEntity {
    @Override
    public String getInternalPerkName() {
        return "heresy_perk";
    }

    @Override
    public String getDisplayName() {
        return "邪术";
    }

    @Override
    public Material getIcon() {
        return Material.COAL;
    }

    @Override
    public double requireCoins() {
        return 0;
    }

    @Override
    public double requireRenown(int level) {
        return level * 10;
    }

    @Override
    public int requirePrestige() {
        return 2;
    }

    @Override
    public int requireLevel() {
        return 0;
    }

    @Override
    public List<String> getDescription(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add("&7获得对抗神话物品的力量.");
        int nextLevel = PlayerUtil.getPlayerUnlockedPerkLevel(player, this.getInternalPerkName()) + 1;
        switch (nextLevel) {
            case 1: {
                lines.add("&7解锁后:");
                lines.add("  &8- &7玩法解锁: &2任务 &7- &9夜幕模式");
                lines.add("  &8- &7合成解锁: &5暗黑之甲");
                break;
            }
            case 2: {
                lines.add("&7下一等级:");
                lines.add("  &8- &7攻击穿着 &6皮革护甲 &7的玩家造成的伤害 &c+5%");
                lines.add("  &8- &7降低合成暗黑之甲的 &5暗聚块 &7消耗: &f4 ➝ &d3");
                break;
            }
            case 3: {
                lines.add("&7下一等级:");
                lines.add("  &8- &7允许你将 &5暗黑之甲 &7提升至 &eII 阶");
                lines.add("  &8- &7降低合成暗黑之甲的 &5暗聚块 &7消耗: &f3 ➝ &d2");
                lines.add("  &8- &7暴怒之甲需另行解锁 &4极度愤怒");
                break;
            }
        }
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
    @cn.charlotte.pit.enchantment.param.event.PlayerOnly
    public void handleAttackEntity(int level, Player attacker, org.bukkit.entity.Entity target, double damage,
            com.google.common.util.concurrent.AtomicDouble trueDamage, com.google.common.util.concurrent.AtomicDouble boost,
            java.util.concurrent.atomic.AtomicBoolean cancel) {
        if (level < 2 || cancel.get() || !(target instanceof Player)) return;
        for (org.bukkit.inventory.ItemStack item : ((Player) target).getInventory().getArmorContents()) {
            if (item != null && item.getType().name().startsWith("LEATHER_")) {
                boost.addAndGet(0.05);
                return;
            }
        }
    }

    @Override
    @cn.charlotte.pit.enchantment.param.event.PlayerOnly
    @cn.charlotte.pit.parm.type.BowOnly
    public void handleShootEntity(int level, Player attacker, org.bukkit.entity.Entity target, double damage,
            com.google.common.util.concurrent.AtomicDouble trueDamage, com.google.common.util.concurrent.AtomicDouble boost,
            java.util.concurrent.atomic.AtomicBoolean cancel) {
        handleAttackEntity(level, attacker, target, damage, trueDamage, boost, cancel);
    }
}
