var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');
var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "虚无迟滞";
INTERNAL_NAME = "hsr_nihility_lag_enchant";
RARITY = "NORMAL";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var chance = 10 * level;
    return [
        "&7天赋: 受到玩家攻击时, 有 &f" + chance + "% &7几率对其施加 &c迟滞",
        "&7迟滞: &c缓慢 I &f(00:02) &7与 &c虚弱 I &f(00:02) &7(冷却 &f6秒&7)"
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
    var now = Date.now();
    var cdUntil = _metaLong(myself, "hsr_nihility_lag_cd");
    if (now < cdUntil) {
        return;
    }
    if (Math.random() >= 0.10 * enchant_level) {
        return;
    }
    _setMetaLong(myself, "hsr_nihility_lag_cd", now + 6000);

    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0), true);
    attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0), true);
}

