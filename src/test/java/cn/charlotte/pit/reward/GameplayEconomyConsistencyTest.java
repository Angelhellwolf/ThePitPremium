package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.data.sub.QuestData;
import cn.charlotte.pit.enchantment.type.normal.SharpnessEnchant;
import cn.charlotte.pit.item.MythicLifeLoss;
import cn.charlotte.pit.item.type.LuckyChestplate;
import cn.charlotte.pit.item.type.mythic.MythicSwordItem;
import cn.charlotte.pit.listener.CombatListener;
import cn.charlotte.pit.listener.PlayerListener;
import cn.charlotte.pit.perk.type.prestige.FishClubPerk;
import cn.charlotte.pit.quest.QuestRules;
import cn.charlotte.pit.quest.type.DryBlood;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.random.RandomUtil;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GameplayEconomyConsistencyTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private MockedStatic<PlayerUtil> players;
    private Player player;
    private PlayerProfile profile;

    @Before public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(manager);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        pit = mockStatic(ThePit.class);
        ThePit plugin = mock(ThePit.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        players = mockStatic(PlayerUtil.class);
        player = mock(CraftPlayer.class, call -> {
            if (call.getMethod().getName().equals("getHealth")) {
                if (call.getMethod().getReturnType() == int.class) return 20;
                return 20.0;
            }
            return RETURNS_DEFAULTS.answer(call);
        });
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        profile = new PlayerProfile(id, "EconomyConsistencyTester");
        profile.setLoaded(true);
        PlayerProfile.getCacheProfile().put(id, profile);
    }

    @After public void tearDown() {
        if (profile != null) PlayerProfile.getCacheProfile().remove(profile.getPlayerUuid());
        if (players != null) players.close();
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    @Test public void protectedItemsDoNotInheritAnUnprotectedItemsException() {
        ItemStack specialItem = new ItemStack(Material.GOLD_CHESTPLATE), normalItem = new ItemStack(Material.GOLD_SWORD);
        LuckyChestplate special = spy(new LuckyChestplate());
        special.getEnchantments().put(new SharpnessEnchant(), 1);
        special.setLive(2);
        doReturn(specialItem).when(special).toItemStack();
        MythicSwordItem normal = new MythicSwordItem();
        normal.getEnchantments().put(new SharpnessEnchant(), 1);
        normal.setLive(4);
        try (MockedStatic<MythicUtil> items = mockStatic(MythicUtil.class)) {
            items.when(() -> MythicUtil.getMythicItem(specialItem)).thenReturn(special);
            items.when(() -> MythicUtil.getMythicItem(normalItem)).thenReturn(normal);
            assertEquals(1, MythicLifeLoss.onDeath(specialItem, false, false).lostLives);
            assertEquals(0, MythicLifeLoss.onDeath(normalItem, false, false).lostLives);
            assertEquals(4, normal.getLive());
        }
    }

    @Test public void theLastItemLifeIsConsumedAndOrdinaryEquipmentRemainsOrdinary() {
        ItemStack original = new ItemStack(Material.GOLD_SWORD), ordinary = new ItemStack(Material.IRON_BOOTS);
        MythicSwordItem sword = new MythicSwordItem();
        sword.getEnchantments().put(new SharpnessEnchant(), 1); sword.setLive(1);
        try (MockedStatic<MythicUtil> items = mockStatic(MythicUtil.class)) {
            items.when(() -> MythicUtil.getMythicItem(original)).thenReturn(sword);
            MythicLifeLoss.Result result = MythicLifeLoss.onDeath(original, true, false);
            assertEquals(1, result.lostLives);
            assertEquals(Material.AIR, result.item.getType());
            assertSame(ordinary, MythicLifeLoss.onDeath(ordinary, true, false).item);
        }
    }

    @Test public void deathResetsOnlyThatItemsJewelProgressEvenWhenItsLifeIsProtected() {
        ItemStack original = new ItemStack(Material.GOLD_SWORD);
        MythicSwordItem sword = spy(new MythicSwordItem());
        sword.setLive(4); sword.setHiddenJewelKills(116); sword.setHeartripperShards(17);
        doReturn(original).when(sword).toItemStack();
        try (MockedStatic<MythicUtil> items = mockStatic(MythicUtil.class)) {
            items.when(() -> MythicUtil.getMythicItem(original)).thenReturn(sword);
            assertEquals(0, MythicLifeLoss.onDeath(original, false, false).lostLives);
            assertEquals(0, sword.getHiddenJewelKills());
            assertEquals(17, sword.getHeartripperShards());
            assertEquals(4, sword.getLive());
        }
    }

    @Test public void changingNightModeDoesNotChangeAnAlreadyAcceptedQuestRewardType() {
        QuestData quest = new QuestData();
        quest.setNightQuest(true); profile.setNightQuestEnable(false);
        assertTrue(QuestRules.isNight(profile, quest));
        quest.setNightQuest(false); profile.setNightQuestEnable(true);
        assertFalse(QuestRules.isNight(profile, quest));
    }

    @Test public void nightQuestQuotaAccountsForEachPromisedVileIncludingQuestMaster() {
        assertEquals(1, QuestRules.completionCost(profile, 3, false));
        assertEquals(2, QuestRules.completionCost(profile, 3, true));
        profile.getUnlockedPerkMap().put("quest_master_perk", new PerkData("quest_master_perk", 1));
        assertEquals(3, QuestRules.completionCost(profile, 3, true));
        assertEquals(2, QuestRules.completionCost(profile, 1, true));
    }

    @Test public void driedBloodSubtractsHalfAHeartPerLevel() {
        QuestData quest = new QuestData(); quest.setInternalName("dried_blood"); quest.setLevel(2);
        profile.setCurrentQuest(quest);
        cn.charlotte.pit.event.PitRegainHealthEvent event = new cn.charlotte.pit.event.PitRegainHealthEvent(player, 5);
        new DryBlood().onRegain(event);
        assertEquals(3, event.getAmount(), 0.000001);
    }

    @Test public void fishingClubDoesNotGenerateASecondBaseCatchForNonMembers() {
        PlayerFishEvent event = mock(PlayerFishEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getState()).thenReturn(PlayerFishEvent.State.CAUGHT_FISH);
        try (MockedStatic<RewardService> rewards = mockStatic(RewardService.class)) {
            new FishClubPerk().onFishing(event);
            rewards.verifyNoInteractions();
            verify(player, never()).getWorld();
            verify(event, never()).getCaught();
        }
    }

    @Test public void fishingSpeedRequiresTierFourAndTierTwoAwardsItsOwnExperience() {
        PlayerFishEvent event = mock(PlayerFishEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getState()).thenReturn(PlayerFishEvent.State.FISHING);
        Fish hook = mock(Fish.class); when(event.getHook()).thenReturn(hook); when(hook.getBiteChance()).thenReturn(0.1);
        FishClubPerk perk = new FishClubPerk();
        profile.getUnlockedPerkMap().put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), 3));
        perk.onFishing(event); verify(hook, never()).setBiteChance(anyDouble());
        profile.getUnlockedPerkMap().put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), 4));
        perk.onFishing(event); verify(hook).setBiteChance(0.12);
        profile.getUnlockedPerkMap().put(perk.getInternalPerkName(), new PerkData(perk.getInternalPerkName(), 2));
        when(event.getState()).thenReturn(PlayerFishEvent.State.CAUGHT_FISH);
        try (MockedStatic<RandomUtil> random = mockStatic(RandomUtil.class); MockedStatic<RewardService> rewards = mockStatic(RewardService.class)) {
            perk.onFishing(event);
            rewards.verify(() -> RewardService.grant(profile, RewardSource.FISHING, 0, 25));
        }
    }

    @Test public void botOlympusPotionKeepsItsEffectsWithoutPayingConsumptionExperience() {
        ItemStack potion = new ItemStack(Material.POTION);
        net.minecraft.server.v1_8_R3.ItemStack nms = mock(net.minecraft.server.v1_8_R3.ItemStack.class);
        NBTTagCompound tag = new NBTTagCompound(), extra = new NBTTagCompound();
        extra.setString("internal", "perk_olympus"); extra.setInt("botReward", 1); tag.set("extra", extra);
        when(nms.getTag()).thenReturn(tag);
        try (MockedStatic<UtilKt> items = mockStatic(UtilKt.class); MockedStatic<RewardService> rewards = mockStatic(RewardService.class)) {
            items.when(() -> UtilKt.reflectGetNmsItem(any(ItemStack.class))).thenReturn(nms);
            PlayerListener listener = new PlayerListener();
            listener.onPlayerEat(new PlayerItemConsumeEvent(player, potion));
            rewards.verifyNoInteractions();
            verify(player, times(3)).addPotionEffect(any(PotionEffect.class), eq(true));
            extra.setInt("botReward", 0);
            listener.onPlayerEat(new PlayerItemConsumeEvent(player, potion));
            rewards.verify(() -> RewardService.grant(profile, RewardSource.PERK, 0, 27));
        }
    }
}
