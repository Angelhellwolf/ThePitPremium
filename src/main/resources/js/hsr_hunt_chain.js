var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');
var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "巡猎连斩";
INTERNAL_NAME = "hsr_hunt_chain_enchant";
RARITY = "NORMAL";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 8 + 6 * level;
    return [
        "&7击杀玩家后进入 &b连斩 &f(6秒)",
        "&7期间近战伤害 &c+" + boost + "% &7并获得 &b速度 I",
        "&7连斩期间再次击杀会刷新持续时间"
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
    _setMetaLong(myself, "hsr_hunt_chain_ready", Date.now() + 6000);
    myself.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0), true);
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    if (Date.now() > _metaLong(attacker, "hsr_hunt_chain_ready")) {
        return;
    }
    boost_damage.getAndAdd(0.08 + 0.06 * enchant_level);
}
