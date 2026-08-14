ENCHANT_NAME = "护盾脉冲";
INTERNAL_NAME = "hsr_shield_pulse_enchant";
RARITY = "NORMAL";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var reduce = 5 * level;
    return [
        "&7天赋: 当你的生命值高于等于 &a80% &7时",
        "&7受到的&f普通&7伤害 &a-" + reduce + "% &7(&f真实&7伤害不受影响)"
    ];
}

function be_damaged(enchant_level, myself, attacker, damage, final_damage, boost_damage, cancel) {
    var maxHealth = myself.getMaxHealth();
    if (maxHealth <= 0) {
        return;
    }
    if (myself.getHealth() / maxHealth < 0.80) {
        return;
    }
    boost_damage.getAndAdd(-0.05 * enchant_level);
}

