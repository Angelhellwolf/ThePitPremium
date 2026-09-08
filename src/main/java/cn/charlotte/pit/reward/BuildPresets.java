package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.junxu.JunxuManager;
import cn.charlotte.pit.junxu.JunxuTrainingSettings;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkType;
import cn.charlotte.pit.supporter.SupporterAccount;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Saved preferences never buy, duplicate or upgrade a player's equipment or perks. */
public final class BuildPresets {
    private BuildPresets() { }

    private static PlayerProfile checked(Player player, int number) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!EconomyV2.active() || !profile.isLoaded() || profile.isBot()) throw new IllegalArgumentException("成长系统尚未启用。");
        if (number < 1 || number > GrowthProgress.presetSlots(profile)) throw new IllegalArgumentException("当前身份没有这个预设栏位，已保存的方案仍会保留。");
        return profile;
    }

    public static void save(Player player, int number) {
        PlayerProfile profile = checked(player, number);
        EconomyData.BuildPreset preset = new EconomyData.BuildPreset();
        profile.getChosePerk().forEach((slot, perk) -> {
            if (slot > 0 && slot <= 8 && perk != null) preset.selected.put(slot,
                    new PerkData(perk.getPerkInternalName(), perk.getLevel()));
        });
        preset.autoBuy = new ArrayList<>(profile.getAutoBuyButtons());
        profile.getEconomyData().builds.put(Integer.toString(number), preset);
        player.sendMessage(CC.translate("&a构筑方案 " + number + " 已保存。"));
    }

    public static void apply(Player player, int number) {
        PlayerProfile profile = checked(player, number);
        if (profile.isInArena() || !profile.getCombatTimer().hasExpired()) throw new IllegalArgumentException("返回安全区域并脱离战斗后可应用构筑。");
        if (profile.getCurrentQuest() != null && "KeepSilence".equalsIgnoreCase(profile.getCurrentQuest().getInternalName()))
            throw new IllegalArgumentException("当前挑战禁止更换天赋。");
        if (ThePit.getInstance().getEventFactory().getActiveEpicEvent() != null)
            throw new IllegalArgumentException("请在大型事件结束后应用构筑。");
        EconomyData.BuildPreset saved = profile.getEconomyData().builds.get(Integer.toString(number));
        if (saved == null) throw new IllegalArgumentException("这个栏位尚未保存构筑。");
        Map<String, AbstractPerk> registry = ThePit.getInstance().getPerkFactory().getPerkMap();
        Map<Integer, PerkData> desired = new LinkedHashMap<>();
        Set<String> unique = new HashSet<>();
        for (Map.Entry<Integer, PerkData> entry : saved.selected.entrySet()) {
            int slot = entry.getKey();
            AbstractPerk perk = registry.get(entry.getValue().getPerkInternalName());
            if (perk == null || !unique.add(perk.getInternalPerkName())) throw new IllegalArgumentException("构筑包含不可用或重复的天赋。");
            validateSlot(profile, slot, perk);
            PerkData owned = profile.getBoughtPerkMap().get(perk.getInternalPerkName());
            if (owned == null && "over_drive".equals(perk.getInternalPerkName())) owned = new PerkData("over_drive", 1);
            if (owned == null) throw new IllegalArgumentException("请先在本精通购买：" + perk.getDisplayName());
            desired.put(slot, new PerkData(owned.getPerkInternalName(), owned.getLevel()));
        }
        for (Map.Entry<Integer, PerkData> current : new ArrayList<>(profile.getChosePerk().entrySet())) {
            if (current.getKey() <= 0 || current.getValue() == null || current.getValue().equals(desired.get(current.getKey()))) continue;
            AbstractPerk perk = registry.get(current.getValue().getPerkInternalName());
            if (perk != null) perk.onPerkInactive(player);
            profile.getChosePerk().remove(current.getKey());
        }
        for (Map.Entry<Integer, PerkData> entry : desired.entrySet()) {
            if (entry.getValue().equals(profile.getChosePerk().get(entry.getKey()))) continue;
            profile.getChosePerk().put(entry.getKey(), entry.getValue());
            registry.get(entry.getValue().getPerkInternalName()).onPerkActive(player);
        }
        profile.setAutoBuyButtons(new ArrayList<>(saved.autoBuy));
        player.sendMessage(CC.translate("&a构筑方案 " + number + " 已应用。"));
    }

    public static void validateSlot(PlayerProfile profile, int slot, AbstractPerk perk) {
        if (slot < 1 || slot > 8) throw new IllegalArgumentException("天赋栏位无效。");
        int required = slot == 2 ? 35 : slot == 3 ? 70 : slot == 4 ? 100 : 0;
        if (profile.getLevel() < required || slot == 4 && !profile.getUnlockedPerkMap().containsKey("ExtractPerkSlot")
                || slot == 8 && !profile.getUnlockedPerkMap().containsKey("extra_kill_streak_slot_perk"))
            throw new IllegalArgumentException("构筑使用了尚未开放的天赋栏位。");
        PerkType expected = slot < 5 ? PerkType.PERK : slot == 5 ? PerkType.MEGA_STREAK : PerkType.KILL_STREAK;
        if (perk.getPerkType() != expected || profile.getPrestige() < perk.requirePrestige())
            throw new IllegalArgumentException("天赋类型或精通条件不满足。");
        if (profile.getLevel() < perk.requireLevel()
                && !(expected == PerkType.PERK && profile.getUnlockedPerkMap().containsKey("the_way_perk")))
            throw new IllegalArgumentException("等级不足以使用：" + perk.getDisplayName());
    }

    public static void training(Player player, int number, boolean save) {
        PlayerProfile profile = checked(player, number);
        SupporterAccount account = profile.getSupporterAccount();
        if (account == null || !account.isPermanent() || account.activeTier(System.currentTimeMillis()) == SupporterTier.NONE)
            throw new IllegalArgumentException("神话契约或神话圣契房主可使用训练预设。");
        JunxuManager manager = ThePit.getInstance().getJunxuManager();
        if (manager == null || !manager.isReady()) throw new IllegalArgumentException("军需处尚未准备就绪。");
        String key = Integer.toString(number);
        if (save) {
            JunxuTrainingSettings v = manager.trainingSettings(player.getUniqueId());
            Map<String, String> map = new LinkedHashMap<>();
            map.put("health", Double.toString(v.health)); map.put("armor", v.armor.name()); map.put("weapon", v.weapon.name());
            map.put("mode", v.mode.name()); map.put("route", v.route.name()); map.put("strength", v.strength.name());
            map.put("paused", Boolean.toString(v.paused));
            profile.getEconomyData().trainingPresets.put(key, map);
            player.sendMessage(CC.translate("&a训练方案 " + number + " 已保存。"));
        } else {
            Map<String, String> map = profile.getEconomyData().trainingPresets.get(key);
            if (map == null) throw new IllegalArgumentException("这个栏位尚未保存训练设置。");
            JunxuTrainingSettings v = new JunxuTrainingSettings(Double.parseDouble(map.get("health")),
                    JunxuTrainingSettings.Armor.valueOf(map.get("armor")), JunxuTrainingSettings.Weapon.valueOf(map.get("weapon")),
                    JunxuTrainingSettings.Mode.valueOf(map.get("mode")), JunxuTrainingSettings.Route.valueOf(map.get("route")),
                    JunxuTrainingSettings.Strength.valueOf(map.get("strength")), Boolean.parseBoolean(map.get("paused")));
            manager.configureTraining(player, v);
        }
    }
}
