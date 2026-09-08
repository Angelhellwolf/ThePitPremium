var CombatContext = Java.type('cn.charlotte.pit.util.combat.CombatContext');
ENCHANT_NAME = "千万富翁";
INTERNAL_NAME = "multimillionaire";
RARITY = "RARE";
TYPE = ["WEAPON", "BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = level === 1 ? "16.7" : (level === 2 ? "33.3" : "50");
    var cost = 20 + 15 * level;
    return [
        "&7攻击玩家时消耗 &6" + cost + " 硬币",
        "&7使该次伤害 &c+" + boost + "%",
        "&7硬币不足时不触发"
    ];
}

function _trySpend(enchant_level, attacker, boost_damage) {
    var profile = pit_profile(attacker);
    var cost = 20 + 15 * enchant_level;
    if (profile.getCoins() < cost) {
        return;
    }
    CombatContext.afterAccepted(new Runnable({ run: function() { profile.setCoins(Math.max(0, profile.getCoins() - cost)); } }));
    var boost = enchant_level === 1 ? 1.0 / 6.0 : (enchant_level === 2 ? 1.0 / 3.0 : 0.5);
    boost_damage.getAndAdd(boost);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!cancel.get() && target instanceof Player && !target.getUniqueId().equals(attacker.getUniqueId())) {
        _trySpend(enchant_level, attacker, boost_damage);
    }
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!cancel.get() && target instanceof Player && !target.getUniqueId().equals(attacker.getUniqueId())) {
        _trySpend(enchant_level, attacker, boost_damage);
    }
}
