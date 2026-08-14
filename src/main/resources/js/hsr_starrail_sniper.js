ENCHANT_NAME = "星轨狙击";
INTERNAL_NAME = "hsr_starrail_sniper_enchant";
RARITY = "RARE";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 10 * level;
    var trueHearts = (0.2 * level).toFixed(1);
    return [
        "&7战技: 与目标距离大于等于 &f25格 &7时",
        "&7箭矢造成的伤害 &c+" + boost + "% &7且额外造成 &c" + trueHearts + "❤ &f真实&7伤害"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var dist = attacker.getLocation().distance(target.getLocation());
    if (dist < 25.0) {
        return;
    }
    boost_damage.getAndAdd(0.10 * enchant_level);
    final_damage.getAndAdd(0.4 * enchant_level); // 0.2❤ * lvl
}

