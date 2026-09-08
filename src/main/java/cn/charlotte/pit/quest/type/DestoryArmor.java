package cn.charlotte.pit.quest.type;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.quest.AbstractQuest;
import cn.charlotte.pit.util.PlayerUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/1/22 14:22
 */
public class DestoryArmor extends AbstractQuest implements IPlayerDamaged {
    @Override
    public String getQuestInternalName() {
        return "destroy_armor";
    }

    @Override
    public String getQuestDisplayName() {
        return "破坏武装";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinLevel() {
        return 2;
    }

    @Override
    public List<String> getQuestDescription(int level, boolean isNightQuest) {
        return Arrays.asList(
                "&f护甲提供的防御力降低 &c" + (15 + level * 15) + "%");
    }

    @Override
    public long getDuration(int level) {
        return 5 * 60 * 1000;
    }

    @Override
    public int getTotal(int level) {
        return 10;
    }

    @Override
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cancel.get()) cn.charlotte.pit.util.combat.CombatContext.weakenArmor((15 + enchantLevel * 15) * 0.01);
    }
}
