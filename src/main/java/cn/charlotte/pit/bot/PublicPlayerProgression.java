package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.menu.perk.normal.buy.button.PerkBuyButton;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.perk.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Shopping decisions are made at spawn, while purchases use the existing player rules. */
final class PublicPlayerProgression {
    private final ThePit plugin;
    private final List<List<String>> preferences = new ArrayList<>();
    private final List<String> renownPriorities;
    private final boolean automatic;
    private final Map<String, AbstractPerk> perks = new HashMap<>();

    PublicPlayerProgression(ThePit plugin) {
        this.plugin = plugin;
        automatic = plugin.getConfig().getBoolean("public-simulated-players.auto-progression", true);
        String[][] defaults = {{"Strength", "Vampire", "Gladiator", "SafetySecond"},
                {"Vampire", "Gladiator", "Strength", "SafetySecond"}, {"Gladiator", "Strength", "Vampire", "SafetySecond"}};
        List<Map<?, ?>> configured = plugin.getConfig().getMapList("public-simulated-players.profiles");
        for (int slot = 0; slot < 3; slot++) {
            Object names = slot < configured.size() ? configured.get(slot).get("preferred-perks") : null;
            List<String> list = new ArrayList<>();
            if (names instanceof List<?>) for (Object name : (List<?>) names) list.add(String.valueOf(name));
            preferences.add(list.isEmpty() ? Arrays.asList(defaults[slot]) : list);
        }
        List<String> priorities = plugin.getConfig().getStringList("public-simulated-players.renown-priorities");
        renownPriorities = priorities.isEmpty() ? Arrays.asList("Tenacity", "extra_heart_perm_perk", "Dirty", "first_strike") : priorities;
        for (AbstractPerk perk : plugin.getPerkFactory().getPerks()) perks.put(perk.getInternalPerkName(), perk);
    }

    static int availableSlots(int level) { return level >= 100 ? 4 : level >= 70 ? 3 : level >= 35 ? 2 : 1; }

    void prepare(PublicPlayerState state, Player player) {
        // PitHook populates the perk factory after onEnable; do not retain its initial empty registry.
        if (perks.isEmpty()) for (AbstractPerk perk : plugin.getPerkFactory().getPerks()) perks.put(perk.getInternalPerkName(), perk);
        PlayerProfile profile = state.profile;
        if (automatic) {
            if (PrestigeProgression.advance(player)) plugin.getLogger().info("PUBLIC_PRESTIGE name=" + state.identity.name + " prestige=" + profile.getPrestige());
            for (String name : renownPriorities) {
                AbstractPerk perk = perks.get(name);
                if (perk != null && RenownPurchase.purchase(player, perk)) {
                    plugin.getLogger().info("PUBLIC_RENOWN name=" + state.identity.name + " perk=" + name + " remaining=" + profile.getRenown());
                    break;
                }
            }
        }
        // Reapply permanent stats to this new entity; these hooks use named modifiers, not additive copies.
        for (PerkData unlocked : profile.getUnlockedPerkMap().values()) {
            AbstractPerk perk = perks.get(unlocked.getPerkInternalName());
            if (perk != null && perk.getClass().isAnnotationPresent(Passive.class)) {
                if (unlocked.getLevel() > 1) perk.onUpgrade(player); else perk.onUnlock(player);
            }
        }
        Set<String> activated = new HashSet<>();
        if (automatic) {
            int slot = 1;
            for (String name : preferences.get(state.identity.slot)) {
                if (slot > availableSlots(profile.getLevel())) break;
                AbstractPerk perk = perks.get(name);
                if (perk == null || perk.getPerkType() != PerkType.PERK || perk.getClass().isAnnotationPresent(Passive.class)
                        || perk.requireLevel() > profile.getLevel() && !profile.getUnlockedPerkMap().containsKey("the_way_perk")
                        || perk.requirePrestige() > profile.getPrestige()) continue;
                Integer existing = null;
                for (Map.Entry<Integer, PerkData> chosen : profile.getChosePerk().entrySet()) {
                    if (name.equals(chosen.getValue().getPerkInternalName())) { existing = chosen.getKey(); break; }
                }
                // Keep a legal equipped perk in its own slot, avoiding duplicate activation or purchase.
                if (existing != null) { slot = Math.max(slot, existing + 1); continue; }
                PerkData before = profile.getChosePerk().get(slot);
                new PerkBuyButton(perk, slot, PerkType.PERK).clicked(player, 0, ClickType.LEFT, -1, null);
                PerkData after = profile.getChosePerk().get(slot);
                if (after != null && name.equals(after.getPerkInternalName()) && !Objects.equals(before, after)) {
                    activated.add(name);
                    plugin.getLogger().info("PUBLIC_PERK name=" + state.identity.name + " perk=" + name + " slot=" + slot + " coins=" + profile.getCoins());
                    slot++;
                }
            }
        }
        profile.getChosePerk().putIfAbsent(5, new PerkData("over_drive", 1));
        for (PerkData chosen : profile.getChosePerk().values()) {
            AbstractPerk perk = perks.get(chosen.getPerkInternalName());
            if (perk != null && !activated.contains(chosen.getPerkInternalName())) perk.onPerkActive(player);
        }
        for (IPlayerRespawn hook : plugin.getPerkFactory().getPlayerRespawns()) {
            int level = ((AbstractPerk) hook).getPlayerLevel(player);
            if (level >= 0) hook.handleRespawn(level, player);
        }
        // Player item grants put helmets into inventory; a simulated player equips them itself.
        for (int index = 0; index < 36; index++) {
            ItemStack item = player.getInventory().getItem(index);
            if (item == null || !item.getType().name().endsWith("_HELMET")) continue;
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null || helmet.getType() == Material.AIR) {
                player.getInventory().setHelmet(item); player.getInventory().setItem(index, null); break;
            }
        }
        double maxHealth = profile.getMaxHealth();
        player.setMaxHealth(maxHealth); player.setHealth(maxHealth);
        profile.applyExperienceToPlayer(player);
    }
}
