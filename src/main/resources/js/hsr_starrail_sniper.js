ENCHANT_NAME = "星轨狙击";
INTERNAL_NAME = "hsr_starrail_sniper_enchant";
RARITY = "RARE";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var perChunk = 6 + 3 * level;
    var trueHearts = (0.25 * level).toFixed(2);
    return [
        "&7箭矢命中距离每满 &e8 &7格, 伤害 &c+" + perChunk + "% &7(最高3层)",
        "&7距离达到 &e16 &7格时额外造成 &c" + trueHearts + "❤ &f真实&7伤害"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var dist = attacker.getLocation().distance(target.getLocation());
    var stacks = Math.min(3, Math.floor(dist / 8.0));
    if (stacks <= 0) {
        return;
    }
    boost_damage.getAndAdd((0.06 + 0.03 * enchant_level) * stacks);
    if (dist >= 16.0) {
        final_damage.getAndAdd(0.5 * enchant_level);
    }
}
