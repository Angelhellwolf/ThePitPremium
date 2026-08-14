ENCHANT_NAME = "瞄准协议";
INTERNAL_NAME = "hsr_aiming_protocol_enchant";
RARITY = "NORMAL";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 6 * level;
    return [
        "&7天赋: 当你处于 &b潜行(瞄准) &7状态时",
        "&7箭矢造成的伤害 &c+" + boost + "%"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    if (!attacker.isSneaking()) {
        return;
    }
    boost_damage.getAndAdd(0.06 * enchant_level);
}

