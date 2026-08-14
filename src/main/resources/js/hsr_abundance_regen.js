var FixedMetadataValue = Java.type('org.bukkit.metadata.FixedMetadataValue');

ENCHANT_NAME = "回春赐福";
INTERNAL_NAME = "hsr_abundance_regen_enchant";
RARITY = "RARE";
TYPE = ["ARMOR"];
MAX_LEVEL = 3;

function lore(level) {
    var healHearts = (0.3 * level).toFixed(1);
    return [
        "&7战技: 受到攻击后 &f1秒 &7回复 &a" + healHearts + "❤ &7生命值",
        "&7冷却: &f8秒"
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
    var cdUntil = _metaLong(myself, "hsr_abundance_regen_cd");
    if (now < cdUntil) {
        return;
    }
    _setMetaLong(myself, "hsr_abundance_regen_cd", now + 8000);

    Bukkit.getScheduler().runTaskLater(Pit, new Runnable({
        run: function () {
            if (!myself.isOnline()) {
                return;
            }
            var maxHealth = myself.getMaxHealth();
            if (maxHealth <= 0) {
                return;
            }
            myself.setHealth(Math.min(maxHealth, myself.getHealth() + 0.6 * enchant_level)); // 0.3❤ * lvl
        }
    }), 20);
}

