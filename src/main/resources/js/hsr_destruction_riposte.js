var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "毁灭回敬";
INTERNAL_NAME = "hsr_destruction_riposte_enchant";
RARITY = "RARE";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 6 * level;
    return [
        "&7天赋: 受到攻击后 &f3秒 &7内",
        "&7你造成的伤害 &c+" + boost + "%"
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
    _setMetaLong(myself, "hsr_destruction_riposte_until", Date.now() + 3000);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var until = _metaLong(attacker, "hsr_destruction_riposte_until");
    if (Date.now() > until) {
        return;
    }
    boost_damage.getAndAdd(0.06 * enchant_level);
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var until = _metaLong(attacker, "hsr_destruction_riposte_until");
    if (Date.now() > until) {
        return;
    }
    boost_damage.getAndAdd(0.06 * enchant_level);
}

