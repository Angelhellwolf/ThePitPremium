var CombatContext = Java.type('cn.charlotte.pit.util.combat.CombatContext');
var PotionEffect = Java.type('org.bukkit.potion.PotionEffect');
var PotionEffectType = Java.type('org.bukkit.potion.PotionEffectType');

ENCHANT_NAME = "破韧斩击";
INTERNAL_NAME = "hsr_toughness_slash_enchant";
RARITY = "NORMAL";
TYPE = ["WEAPON"];
MAX_LEVEL = 3;

function lore(level) {
    var trueHearts = (0.3 * level).toFixed(1);
    return [
        "&7天赋: 每 &f4 &7次近战命中触发 &b弱点击破",
        "&7额外造成 &c" + trueHearts + "❤ &f真实&7伤害, 并施加 &c缓慢 I &f(00:02)"
    ];
}

function attack(enchant_level, attacker, target, damage, final_damage, boost_damage, cancel) {
    if (!(target instanceof Player)) {
        return;
    }
    var profile = pit_profile(attacker);
    var hit = CombatContext.meleeHits(attacker);
    if (hit % 4 !== 0) {
        return;
    }

    final_damage.getAndAdd(0.6 * enchant_level); // 0.3❤ * lvl
    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0), true);
}

