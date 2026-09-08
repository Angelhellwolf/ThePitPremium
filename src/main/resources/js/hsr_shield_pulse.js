var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "护盾脉冲";
INTERNAL_NAME = "hsr_shield_pulse_enchant";
RARITY = "NORMAL";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var base = 4 + 2 * level;
    var pulse = 8 + 4 * level;
    return [
        "&7受到玩家伤害 &9-" + base + "%",
        "&7若自身拥有 &6生命吸收&7, 该次伤害额外 &9-" + pulse + "% &7(冷却 &f3秒&7)"
    ];
}

function _metaLong(entity, key) {
    if (!entity.hasMetadata(key)) return 0;
    var list = entity.getMetadata(key);
    if (list == null || list.size() <= 0) return 0;
    return list.get(0).asLong();
}

function _setMetaLong(entity, key, value) {
    entity.setMetadata(key, new FixedMetadataValue(Pit, value));
}

function _absorption(player) {
    try {
        return player.getHandle().getAbsorptionHearts();
    } catch (e) {
        return 0;
    }
}

function be_damaged(enchant_level, myself, attacker, damage, final_damage, boost_damage, cancel) {
    if (!(attacker instanceof Player)) {
        return;
    }
    boost_damage.getAndAdd(-(0.04 + 0.02 * enchant_level));
    if (_absorption(myself) <= 0) {
        return;
    }
    var now = Date.now();
    if (now < _metaLong(myself, "hsr_shield_pulse_cd")) {
        return;
    }
    _setMetaLong(myself, "hsr_shield_pulse_cd", now + 3000);
    boost_damage.getAndAdd(-(0.08 + 0.04 * enchant_level));
}
