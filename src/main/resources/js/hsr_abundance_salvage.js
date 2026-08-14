var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "丰饶回收";
INTERNAL_NAME = "hsr_abundance_salvage_enchant";
RARITY = "RARE";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var healHearts = (0.25 * level).toFixed(2);
    return [
        "&7战技: 箭矢命中玩家时, 回复 &a" + healHearts + "❤ &7生命值",
        "&7冷却: &f4秒"
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

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var cdUntil = _metaLong(attacker, "hsr_abundance_salvage_cd");
    if (now < cdUntil) {
        return;
    }
    _setMetaLong(attacker, "hsr_abundance_salvage_cd", now + 4000);

    var maxHealth = attacker.getMaxHealth();
    if (maxHealth <= 0) {
        return;
    }
    attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + 0.5 * enchant_level)); // 0.25❤ * lvl
}

