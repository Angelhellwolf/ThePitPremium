var CombatContext = Java.type('cn.charlotte.pit.util.combat.CombatContext');
var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "瞄准协议";
INTERNAL_NAME = "hsr_aiming_protocol_enchant";
RARITY = "NORMAL";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var first = 6 + 2 * level;
    var follow = 10 + 4 * level;
    return [
        "&7箭矢命中玩家时伤害 &c+" + first + "%",
        "&7&f3秒&7内再次命中同一目标时改为 &c+" + follow + "%",
        "&7潜行射出时额外 &c+6%"
    ];
}

function _metaLong(entity, key) {
    if (!entity.hasMetadata(key)) return 0;
    var list = entity.getMetadata(key);
    if (list == null || list.size() <= 0) return 0;
    return list.get(0).asLong();
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

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var targetId = target.getUniqueId().toString();
    var lastId = _metaString(attacker, "hsr_aiming_target");
    var until = _metaLong(attacker, "hsr_aiming_until");
    var follow = now <= until && lastId === targetId;
    var boost = follow ? (0.10 + 0.04 * enchant_level) : (0.06 + 0.02 * enchant_level);
    if (CombatContext.shotSneaking(attacker)) {
        boost += 0.06;
    }
    boost_damage.getAndAdd(boost);
    _setMeta(attacker, "hsr_aiming_target", targetId);
    _setMeta(attacker, "hsr_aiming_until", now + 3000);
}
