package cn.charlotte.pit.util.combat;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.perk.AbstractPerk;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import java.util.*;

/** Read-only registry and listener validation against the plugin actually running on the server. */
public final class GameplayRuntimeCheck {
    private GameplayRuntimeCheck() {}

    public static List<String> report(ThePit plugin) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int js = 0;
        for (AbstractEnchantment enchant : plugin.getEnchantmentFactor().getEnchantments()) {
            if (!ids.add(enchant.getNbtName())) errors.add("duplicate enchant " + enchant.getNbtName());
            if (enchant.getClass().getSimpleName().equals("JSEnchantment")) js++;
            for (int level = 1; level <= enchant.getMaxEnchantLevel(); level++) {
                try {
                    String lore = enchant.getEconomyLore(level);
                    if (lore == null || lore.trim().isEmpty()) errors.add("empty lore " + enchant.getNbtName() + ":" + level);
                } catch (Exception failure) { errors.add("lore error " + enchant.getNbtName() + ":" + level); }
            }
        }
        ids.clear();
        for (AbstractPerk perk : plugin.getPerkFactory().getPerks()) {
            if (!ids.add(perk.getInternalPerkName())) errors.add("duplicate perk " + perk.getInternalPerkName());
            if (perk.getPerkType() == null) errors.add("missing perk type " + perk.getInternalPerkName());
        }
        if (!ids.contains("xp_stack")) errors.add("missing xp_stack");
        Set<String> listeners = new HashSet<>();
        for (RegisteredListener listener : HandlerList.getRegisteredListeners(plugin))
            listeners.add(listener.getListener().getClass().getSimpleName());
        for (String name : Arrays.asList("XPStack", "GameEffectListener", "CombatListener", "EnchantmentLoreRefresh", "PlayerLife", "WolfPackEnchant", "TacticalDeploymentShopButton"))
            if (!listeners.contains(name)) errors.add("missing listener " + name);

        List<String> result = new ArrayList<>();
        result.add("GAMEPLAY_CHECK version=consistency-20260908 status=" + (errors.isEmpty() ? "PASS" : "FAIL")
                + " enchants=" + plugin.getEnchantmentFactor().getEnchantments().size() + " js=" + js
                + " perks=" + plugin.getPerkFactory().getPerks().size()
                + " quests=" + plugin.getQuestFactory().getQuests().size()
                + " medals=" + plugin.getMedalFactory().getMedals().size()
                + " listeners=" + listeners.size());
        result.addAll(errors);
        return result;
    }
}
