var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "智能解析";
INTERNAL_NAME = "hsr_erudition_stance_enchant";
RARITY = "NORMAL";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var perStack = 4 + 2 * level;
    return [
        "&7连续命中同一名玩家时叠加 &b解析",
        "&7每层使该次伤害 &c+" + perStack + "% &7(最高 &e3 &7层, &f4秒&7内未命中同一目标则重置)"
    ];
}

function _metaLong(entity, key) {
    if (!entity.hasMetadata(key)) return 0;
    var list = entity.getMetadata(key);
    if (list == null || list.size() <= 0) return 0;
    return list.get(0).asLong();
}

function _metaInt(entity, key) {
    if (!entity.hasMetadata(key)) return 0;
    var list = entity.getMetadata(key);
    if (list == null || list.size() <= 0) return 0;
    return list.get(0).asInt();
}

function _metaString(entity, key) {
    if (!entity.hasMetadata(key)) return "";
    var list = entity.getMetadata(key);
    if (list == null || list.size() <= 0) return "";
    return list.get(0).asString();
}

function _setMeta(entity, key, value) {
    entity.setMetadata(key, new FixedMetadataValue(Pit, value));
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var targetId = target.getUniqueId().toString();
    var lastId = _metaString(attacker, "hsr_erudition_target");
    var until = _metaLong(attacker, "hsr_erudition_until");
    var stacks = _metaInt(attacker, "hsr_erudition_stacks");
    if (now > until || lastId !== targetId) {
        stacks = 0;
    }
    stacks = Math.min(3, stacks + 1);
    _setMeta(attacker, "hsr_erudition_target", targetId);
    _setMeta(attacker, "hsr_erudition_until", now + 4000);
    _setMeta(attacker, "hsr_erudition_stacks", stacks);
    boost_damage.getAndAdd((0.04 + 0.02 * enchant_level) * stacks);
}
