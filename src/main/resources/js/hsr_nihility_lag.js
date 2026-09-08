var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');
var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "虚无阻滞";
INTERNAL_NAME = "hsr_nihility_lag_enchant";
RARITY = "NORMAL";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var reduce = 4 + 4 * level;
    var slowTicks = 20 + 10 * level;
    var seconds = (slowTicks / 20).toFixed(1);
    return [
        "&7受到玩家攻击时, 该次伤害 &9-" + reduce + "%",
        "&7并对攻击者施加 &c缓慢 I &f(" + seconds + "秒) &7(冷却 &f3秒&7)"
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
    if (!(attacker instanceof Player)) {
        return;
    }
    var reduce = 0.04 + 0.04 * enchant_level;
    boost_damage.getAndAdd(-reduce);

    var now = Date.now();
    if (now < _metaLong(myself, "hsr_nihility_lag_cd")) {
        return;
    }
    _setMetaLong(myself, "hsr_nihility_lag_cd", now + 3000);
    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 + 10 * enchant_level, 0), true);
}
