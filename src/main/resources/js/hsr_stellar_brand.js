ENCHANT_NAME = "星核灼印";
INTERNAL_NAME = "hsr_stellar_brand_enchant";
RARITY = "RARE";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var chance = 12 * level;
    var seconds = 1 + level; // 2..4
    var bonus = (level === 1 ? 6 : (level === 2 ? 10 : 14));
    return [
        "&7战技: 近战命中玩家时, 有 &f" + chance + "% &7几率施加 &c燃烧 &f(00:0" + seconds + ")",
        "&7对处于 &c燃烧 &7的玩家造成的伤害 &c+" + bonus + "%"
    ];
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }

    if (Math.random() < 0.12 * enchant_level) {
        var fireTicks = 40 + 20 * (enchant_level - 1); // 2/3/4s
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
    }

    if (target.getFireTicks() > 0) {
        var bonus = (enchant_level === 1 ? 0.06 : (enchant_level === 2 ? 0.10 : 0.14));
        boost_damage.getAndAdd(bonus);
    }
}

