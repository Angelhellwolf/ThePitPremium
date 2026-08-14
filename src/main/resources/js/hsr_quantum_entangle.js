var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "量子纠缠";
INTERNAL_NAME = "hsr_quantum_entangle_enchant";
RARITY = "RARE";
TYPE = ["BOW"];
MAX_LEVEL = 3;

function lore(level) {
    var chance = 8 * level;
    return [
        "&7战技: 箭矢命中玩家时, 有 &f" + chance + "% &7几率施加 &c量子迟滞",
        "&7量子迟滞: &c缓慢 I &f(00:03) &7与 &c虚弱 I &f(00:02)"
    ];
}

function shoot(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    if (Math.random() >= 0.08 * enchant_level) {
        return;
    }
    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 0), true);
    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0), true);
}

