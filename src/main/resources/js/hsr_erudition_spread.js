ENCHANT_NAME = "智识扩散";
INTERNAL_NAME = "hsr_erudition_spread_enchant";
RARITY = "NORMAL";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 5 * level;
    return [
        "&7天赋: 箭矢命中玩家时, 若目标 &f3格 &7范围内(不包含你)存在至少 &f1名 &7其他玩家",
        "&7则该次箭矢造成的伤害 &c+" + boost + "%"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var nearby = target.getNearbyEntities(3, 3, 3);
    var count = 0;
    for (var i = 0; i < nearby.size(); i++) {
        var e = nearby.get(i);
        if (e instanceof Player && !e.getUniqueId().equals(attacker.getUniqueId())) {
            count++;
        }
    }
    if (count < 1) {
        return;
    }
    boost_damage.getAndAdd(0.05 * enchant_level);
}
