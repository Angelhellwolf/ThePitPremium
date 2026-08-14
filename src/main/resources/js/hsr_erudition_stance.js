ENCHANT_NAME = "智识解析";
INTERNAL_NAME = "hsr_erudition_stance_enchant";
RARITY = "NORMAL";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 5 * level;
    return [
        "&7天赋: 当你未处于 &b疾跑 &7状态时",
        "&7你造成的伤害 &c+" + boost + "%"
    ];
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    if (attacker.isSprinting()) {
        return;
    }
    boost_damage.getAndAdd(0.05 * enchant_level);
}

