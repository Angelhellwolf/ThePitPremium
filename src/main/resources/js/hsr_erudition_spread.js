var PlayerUtil = Java.type('cn.charlotte.pit.util.PlayerUtil');
ENCHANT_NAME = "智能扩散";
INTERNAL_NAME = "hsr_erudition_spread_enchant";
RARITY = "NORMAL";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var boost = 6 + 4 * level;
    var splash = (0.25 * level).toFixed(2);
    return [
        "&7箭矢命中玩家时, 对目标 &e4格 &7内其他玩家造成 &c" + splash + "❤ &f真实&7伤害",
        "&7若范围内存在其他玩家, 该箭伤害 &c+" + boost + "%"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var nearby = PlayerUtil.getNearbyPlayers(target.getLocation(), 4);
    var hitOther = false;
    for (var i = 0; i < nearby.size(); i++) {
        var entity = nearby.get(i);
        if (!(entity instanceof Player)) {
            continue;
        }
        if (entity.getUniqueId().equals(attacker.getUniqueId()) || entity.getUniqueId().equals(target.getUniqueId())) {
            continue;
        }
        hitOther = true;
        var health = entity.getHealth();
        var splash = 0.5 * enchant_level;
        PlayerUtil.damage(attacker, entity, PlayerUtil.DamageType.TRUE, splash, true);
    }
    if (hitOther) {
        boost_damage.getAndAdd(0.06 + 0.04 * enchant_level);
    }
}
