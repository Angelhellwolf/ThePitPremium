ENCHANT_NAME = "回合外箭";
INTERNAL_NAME = "hsr_followup_arrow_enchant";
RARITY = "NORMAL";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var trueHearts = (0.25 * level).toFixed(2);
    return [
        "&7天赋: 每 &f3 &7次弓箭命中触发 &b追加攻击",
        "&7额外造成 &c" + trueHearts + "❤ &f真实&7伤害"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var profile = pit_profile(attacker);
    var hit = profile.getBowHit();
    if (hit % 3 !== 0) {
        return;
    }
    final_damage.getAndAdd(0.5 * enchant_level); // 0.25❤ * lvl
}
