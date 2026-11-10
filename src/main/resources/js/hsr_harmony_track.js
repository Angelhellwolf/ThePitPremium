ENCHANT_NAME = "同谐行迹";
INTERNAL_NAME = "hsr_harmony_track_enchant";
RARITY = "RARE";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var reward = 3 * level;
    return [
        "&7终结技: 击杀玩家时, 额外获得 &6+" + reward + " &7硬币 与 &b+" + reward + " &7经验值",
        "&7可与其他加成叠加，击杀 Bot 不获得此加成"
    ];
}

function killed(enchant_level, myself, target, coins, experience) {
    coins.getAndAdd(3.0 * enchant_level);
    experience.getAndAdd(3.0 * enchant_level);
}

