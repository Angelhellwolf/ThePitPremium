var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "巡猎连斩";
INTERNAL_NAME = "hsr_hunt_chain_enchant";
RARITY = "NORMAL";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 8 * level;
    return [
        "&7终结技: 击杀玩家后, 获得 1 次 &b连斩",
        "&7下一次近战命中伤害 &c+" + boost + "% &7(持续 &f4秒&7)"
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

function killed(enchant_level, myself, target, coins, experience) {
    _setMetaLong(myself, "hsr_hunt_chain_ready", Date.now() + 4000);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var readyUntil = _metaLong(attacker, "hsr_hunt_chain_ready");
    if (now > readyUntil) {
        return;
    }
    _setMetaLong(attacker, "hsr_hunt_chain_ready", 0);
    boost_damage.getAndAdd(0.08 * enchant_level);
}

