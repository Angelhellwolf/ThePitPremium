package cn.charlotte.pit.audit;

import cn.charlotte.pit.PitHook;
import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.perk.AbstractPerk;
import cn.charlotte.pit.perk.PerkFactory;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline catalog export. Scheduler and server APIs are mocks; no gameplay actions are executed. */
public final class GameplayCatalogExport {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("build/reports/gameplay-catalog");
        Files.createDirectories(directory);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class)) {
            Server server = mock(Server.class);
            PluginManager manager = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(manager);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            BukkitScheduler scheduler = mock(BukkitScheduler.class, invocation ->
                    BukkitTask.class.isAssignableFrom(invocation.getMethod().getReturnType()) ? mock(BukkitTask.class) : RETURNS_DEFAULTS.answer(invocation));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(plugin.getResource(anyString())).thenAnswer(call -> GameplayCatalogExport.class.getClassLoader().getResourceAsStream(call.getArgument(0)));
            PitConfig config = mock(PitConfig.class);
            config.disableEnchants = new ArrayList<>();
            when(config.getDisableEnchants()).thenReturn(Collections.emptyList());
            when(config.getCode()).thenReturn("");
            when(plugin.getPitConfig()).thenReturn(config);
            EnchantmentFactor enchantments = new EnchantmentFactor();
            PerkFactory perks = new PerkFactory();
            when(plugin.getEnchantmentFactor()).thenReturn(enchantments);
            when(plugin.getPerkFactory()).thenReturn(perks);
            cn.charlotte.pit.quest.QuestFactory quests = new cn.charlotte.pit.quest.QuestFactory();
            when(plugin.getQuestFactory()).thenReturn(quests);
            Path scripts = directory.resolve("js");
            Files.createDirectories(scripts);
            try (java.util.stream.Stream<Path> bundled = Files.list(Path.of("src/main/resources/js"))) {
                for (Path source : (Iterable<Path>) bundled.filter(path -> path.toString().endsWith(".js"))::iterator)
                    Files.copy(source, scripts.resolve(source.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.lang.reflect.Field bridge = spg.lgdev.iSpigot.class.getField("INSTANCE");
            bridge.set(null, mock(spg.lgdev.iSpigot.class));
            for (String name : new String[] {"loadPerks", "loadEnchants", "loadQuests"}) {
                Method load = PitHook.class.getDeclaredMethod(name);
                load.setAccessible(true);
                load.invoke(PitHook.INSTANCE);
            }
            cn.charlotte.pit.js.JSHandler.INSTANCE.load();
            Player player = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getName()).thenReturn("CatalogReader");
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            PlayerProfile profile = new PlayerProfile(uuid, "CatalogReader");
            profile.setLoaded(true);
            profile.setPrestige(30);
            PlayerProfile.getCacheProfile().put(uuid, profile);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            List<Map<String, Object>> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (AbstractEnchantment enchant : enchantments.getEnchantments()) {
                if (!ids.add(enchant.getNbtName())) throw new IllegalStateException("Duplicate enchant: " + enchant.getNbtName());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "enchantment");
                row.put("class", enchant.getClass().getName());
                row.put("id", enchant.getNbtName());
                row.put("name", enchant.getEnchantName());
                row.put("rarity", enchant.getRarity().name());
                row.put("maxLevel", enchant.getMaxEnchantLevel());
                row.put("equipment", Arrays.asList(enchant.isWeaponOnly() ? "剑" : "", enchant.isBowOnly() ? "弓" : "",
                        enchant.isArmorOnly() ? "护甲" : "", enchant.isRodOnly() ? "鱼竿" : ""));
                Map<Integer, String> descriptions = new LinkedHashMap<>();
                for (int level = 1; level <= enchant.getMaxEnchantLevel(); level++) descriptions.put(level, enchant.getUsefulnessLore(level));
                row.put("descriptions", descriptions);
                rows.add(row);
            }
            ids.clear();
            for (AbstractPerk perk : perks.getPerks()) {
                if (!ids.add(perk.getInternalPerkName())) throw new IllegalStateException("Duplicate perk: " + perk.getInternalPerkName());
                if (perk.getPerkType() == null) throw new IllegalStateException("Missing perk category: " + perk.getInternalPerkName());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "perk");
                row.put("class", perk.getClass().getName());
                row.put("id", perk.getInternalPerkName());
                row.put("name", perk.getDisplayName());
                row.put("category", perk.getPerkType().name());
                row.put("maxLevel", perk.getMaxLevel());
                Map<Integer, List<String>> descriptions = new LinkedHashMap<>();
                for (int level = 0; level <= Math.max(1, perk.getMaxLevel()); level++) {
                    profile.getUnlockedPerkMap().clear();
                    if (level > 0) profile.getUnlockedPerkMap().put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), level));
                    descriptions.put(level, perk.getDescription(player));
                }
                row.put("descriptions", descriptions);
                rows.add(row);
            }
            for (cn.charlotte.pit.quest.AbstractQuest quest : quests.getQuests()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "quest"); row.put("class", quest.getClass().getName());
                row.put("id", quest.getQuestInternalName()); row.put("name", quest.getQuestDisplayName());
                Map<String, Object> levels = new LinkedHashMap<>();
                for (int level = quest.getMinLevel(); level <= quest.getMaxLevel(); level++) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("day", quest.getQuestDescription(level, false));
                    data.put("night", quest.getQuestDescription(level, true));
                    data.put("kills", quest.getTotal(level)); data.put("durationMs", quest.getDuration(level));
                    levels.put(String.valueOf(level), data);
                }
                row.put("descriptions", levels); rows.add(row);
            }
            int medalCount = 0;
            try (java.util.stream.Stream<Path> paths = Files.walk(Path.of("src/main/java/cn/charlotte/pit/medal/impl"))) {
                for (Path path : (Iterable<Path>) paths.filter(file -> file.toString().endsWith(".java"))::iterator) {
                    String className = Path.of("src/main/java").relativize(path).toString().replace('/', '.').replace('\\', '.').replace(".java", "");
                    cn.charlotte.pit.medal.AbstractMedal medal = (cn.charlotte.pit.medal.AbstractMedal) Class.forName(className).getDeclaredConstructor().newInstance();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("kind", "medal"); row.put("class", className); row.put("id", medal.getInternalName());
                    row.put("name", medal.getDisplayName(1)); row.put("maxLevel", medal.getMaxLevel());
                    Map<Integer, String> descriptions = new LinkedHashMap<>();
                    int previous = 0;
                    for (int level = 1; level <= medal.getMaxLevel(); level++) {
                        int requirement = medal.getProgressRequirement(level);
                        if (requirement < previous) throw new IllegalStateException("Decreasing medal threshold: " + className);
                        previous = requirement;
                        descriptions.put(level, medal.getRequirementDescription(level) + " [" + requirement + "]");
                    }
                    row.put("descriptions", descriptions); rows.add(row); medalCount++;
                }
            }
            Files.writeString(directory.resolve("catalog.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(rows), StandardCharsets.UTF_8);
            System.out.println("CATALOG: " + enchantments.getEnchantments().size() + " enchantments; " + perks.getPerks().size() + " perks");
            System.out.println("CATALOG: " + quests.getQuests().size() + " quests; " + medalCount + " medals");
        }
    }
}
