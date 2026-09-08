package cn.charlotte.pit.reward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only declared monetary effects are reformatted; combat damage and health numbers are never rewritten. */
public final class EconomyLore {
    private static final Pattern XP = Pattern.compile("(\\+?)(\\d+(?:\\.\\d+)?)(\\s*(?:&[0-9a-fk-or])*\\s*经验(?:值)?)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ENCHANT_XP = new HashSet<>(Arrays.asList("combo_xp_enchant", "xp_bump_enchant",
            "hsr_harmony_track_enchant", "hsr_harmony_reprise_enchant", "hsr_harmony_track", "hsr_harmony_reprise"));
    private static final Set<String> FIXED_ENCHANT = new HashSet<>(Arrays.asList("hermes", "moctezuma_enchant", "sierra_enchant",
            "hedge_fund_enchant", "critically_rich_enchant", "strike_gold_enchant", "pebble_enchant", "purple_gold"));
    private static final Set<String> PERK_XP = new HashSet<>(Arrays.asList("explicious", "ice_cube_kill_streak", "second_apple",
            "super_streaker", "ElGatoBoost", "Olympus", "recon_perk"));
    private static final Set<String> PERK_FIXED = new HashSet<>(Arrays.asList("arquebusier", "khanate_kill_streak", "GoldMiner",
            "BountyHunter", "trickle_down_perk", "yummy_perk", "gold_nano_factory_kill_streak"));
    private static final Set<String> STREAK_WINDOWS = new HashSet<>(Arrays.asList("arquebusier", "khanate_kill_streak",
            "explicious", "second_apple", "super_streaker", "rn_gesus_kill_streak", "gold_nano_factory_kill_streak"));
    private EconomyLore() { }

    public static String enchant(String key, String original) {
        if (!EconomyV2.active() || original == null) return original;
        String result = ENCHANT_XP.contains(key) ? experience(original) : original;
        if (ENCHANT_XP.contains(key) || FIXED_ENCHANT.contains(key))
            result += "/s&7以上为基础奖励，随精通成长";
        if (result.contains("经验上限") && "sweaty_enchant".equals(key))
            result = result.replace("+100", "+400").replace("+50", "+200");
        return result;
    }

    public static List<String> perk(String key, List<String> original) {
        if (!EconomyV2.active() || original == null) return original;
        List<String> result = new ArrayList<>();
        for (String line : original) {
            if (STREAK_WINDOWS.contains(key) && line.contains("Bot") && (line.contains("金币") || line.contains("经验"))) continue;
            if (PERK_XP.contains(key)) line = experience(line);
            if ("to_the_moon".equals(key)) line = line.replace("1–100", "4–400");
            result.add(line);
        }
        if (STREAK_WINDOWS.contains(key)) result.add("&7金币与经验按对应连杀段的真人贡献结算");
        if (PERK_XP.contains(key) || PERK_FIXED.contains(key) || "to_the_moon".equals(key))
            result.add("&7固定奖励为基础值，随精通成长");
        return result;
    }

    private static String experience(String line) {
        Matcher matcher = XP.matcher(line); StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            double amount = Double.parseDouble(matcher.group(2)) * 4;
            String value = amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + value + matcher.group(3)));
        }
        matcher.appendTail(result); return result.toString();
    }
}
