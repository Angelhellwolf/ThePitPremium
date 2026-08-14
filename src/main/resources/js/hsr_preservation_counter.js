var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "存护反击";
INTERNAL_NAME = "hsr_preservation_counter_enchant";
RARITY = "RARE";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 15 * level;
    return [
        "&7天赋: 受到攻击时, 获得 1 次 &b存护反击",
        "&7下一次近战命中伤害 &c+" + boost + "% &7(持续 &f3秒&7, 冷却 &f7秒&7)"
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

function be_damaged(enchant_level, myself, attacker, damage, final_damage, boost_damage, cancel) {
    var now = Date.now();
    var cdUntil = _metaLong(myself, "hsr_preservation_counter_cd");
    if (now < cdUntil) {
        return;
    }
    _setMetaLong(myself, "hsr_preservation_counter_ready", now + 3000);
    _setMetaLong(myself, "hsr_preservation_counter_cd", now + 7000);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var readyUntil = _metaLong(attacker, "hsr_preservation_counter_ready");
    if (now > readyUntil) {
        return;
    }
    _setMetaLong(attacker, "hsr_preservation_counter_ready", 0);
    boost_damage.getAndAdd(0.15 * enchant_level);
}

