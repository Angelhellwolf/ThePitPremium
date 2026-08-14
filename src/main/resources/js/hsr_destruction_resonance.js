ENCHANT_NAME = "毁灭共振";
INTERNAL_NAME = "hsr_destruction_resonance_enchant";
RARITY = "RARE";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 6 * level;
    var trueHearts = (0.2 * level).toFixed(1);
    return [
        "&7战技: 当你的生命值低于等于 &c50% &7时",
        "&7造成的伤害 &c+" + boost + "% &7并额外造成 &c" + trueHearts + "❤ &f真实&7伤害"
    ];
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var maxHealth = attacker.getMaxHealth();
    if (maxHealth <= 0) {
        return;
    }
    if (attacker.getHealth() / maxHealth > 0.50) {
        return;
    }
    boost_damage.getAndAdd(0.06 * enchant_level);
    final_damage.getAndAdd(0.4 * enchant_level); // 0.2❤ * lvl
}

