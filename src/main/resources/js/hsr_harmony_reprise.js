ENCHANT_NAME = "同谐回响";
INTERNAL_NAME = "hsr_harmony_reprise_enchant";
RARITY = "RARE";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var healHearts = (0.5 * level).toFixed(1);
    var reward = 4 * level;
    return [
        "&7终结技: 击杀玩家时, 回复 &a" + healHearts + "❤ &7生命值",
        "&7并额外获得 &6+" + reward + " &7硬币 与 &b+" + reward + " &7经验值"
    ];
}

function killed(enchant_level, myself, target, coins, experience) {
    var maxHealth = myself.getMaxHealth();
    if (maxHealth > 0) {
        myself.setHealth(Math.min(maxHealth, myself.getHealth() + 1.0 * enchant_level)); // 0.5❤ * lvl
    }
    coins.getAndAdd(4.0 * enchant_level);
    experience.getAndAdd(4.0 * enchant_level);
}

