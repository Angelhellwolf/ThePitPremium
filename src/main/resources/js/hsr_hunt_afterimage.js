var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');
var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "巡猎掠影";
INTERNAL_NAME = "hsr_hunt_afterimage_enchant";
RARITY = "RARE";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 8 * level;
    var seconds = 2 + level; // 3..5
    return [
        "&7终结技: 击杀玩家后获得 &b掠影",
        "&7掠影: &c速度 I &f(00:0" + seconds + ") &7并使下一次命中伤害 &c+" + boost + "% &7(持续 &f3秒&7)"
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
    var duration = 60 + 20 * (enchant_level - 1); // 3/4/5s
    myself.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0), true);
    _setMetaLong(myself, "hsr_hunt_afterimage_ready", Date.now() + 3000);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var readyUntil = _metaLong(attacker, "hsr_hunt_afterimage_ready");
    if (now > readyUntil) {
        return;
    }
    _setMetaLong(attacker, "hsr_hunt_afterimage_ready", 0);
    boost_damage.getAndAdd(0.08 * enchant_level);
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var now = Date.now();
    var readyUntil = _metaLong(attacker, "hsr_hunt_afterimage_ready");
    if (now > readyUntil) {
        return;
    }
    _setMetaLong(attacker, "hsr_hunt_afterimage_ready", 0);
    boost_damage.getAndAdd(0.08 * enchant_level);
}

