ENCHANT_NAME = "裂界终结";
INTERNAL_NAME = "hsr_rift_execution_enchant";
RARITY = "RARE";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 8 * level;
    var trueHearts = (0.2 * level).toFixed(1);
    return [
        "&7终结技: 当目标生命值不高于 &c30% &7时",
        "&7你造成的伤害 &c+" + boost + "% &7且额外造成 &c" + trueHearts + "❤ &f真实&7伤害"
    ];
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var maxHealth = target.getMaxHealth();
    if (maxHealth <= 0) {
        return;
    }
    if (target.getHealth() / maxHealth > 0.30) {
        return;
    }

    boost_damage.getAndAdd(0.08 * enchant_level);
    final_damage.getAndAdd(0.4 * enchant_level); // 0.2❤ * lvl
}

