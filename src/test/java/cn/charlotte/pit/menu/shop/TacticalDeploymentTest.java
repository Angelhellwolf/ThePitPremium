package cn.charlotte.pit.menu.shop;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.EconomyData;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.menu.shop.button.type.TacticalDeploymentShopButton;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.junit.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TacticalDeploymentTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private PlayerProfile profile;
    private Player player;
    private World world;
    private EventFactory events;
    @Before public void setup() {
        bukkit = mockStatic(Bukkit.class); pit = mockStatic(ThePit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(CraftItemFactory.instance());
        ThePit plugin = mock(ThePit.class); PitConfig config = mock(PitConfig.class); events = mock(EventFactory.class);
        pit.when(ThePit::getInstance).thenReturn(plugin);
        when(plugin.getPitConfig()).thenReturn(config); when(plugin.getEventFactory()).thenReturn(events);
        world = mock(World.class); UUID worldId = UUID.randomUUID(); when(world.getUID()).thenReturn(worldId);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        when(config.getSpawnLocations()).thenReturn(List.of(new Location(world, 0, 107, 0)));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            Block block = mock(Block.class); int y = call.getArgument(1);
            when(block.getType()).thenReturn(y == 69 ? Material.STONE : Material.AIR); return block;
        });
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location at = call.getArgument(0); return world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        });
        profile = new PlayerProfile(UUID.randomUUID(), "DeploymentCheck"); profile.setLoaded(true); profile.setPrestige(8);
        PlayerProfile.getCacheProfile().put(profile.getPlayerUuid(), profile);
        player = mock(Player.class); when(player.getUniqueId()).thenReturn(profile.getPlayerUuid()); when(player.getWorld()).thenReturn(world);
    }
    @After public void cleanup() { PlayerProfile.getCacheProfile().remove(profile.getPlayerUuid()); pit.close(); bukkit.close(); }

    @Test public void purchasedUnlockExposesTheActualDeviceInTheShop() {
        TacticalDeploymentShopButton button = (TacticalDeploymentShopButton) new ShopMenu().getButtons(player).get(22);
        assertEquals(Material.AIR, button.getDisplayButtonItem(player).getType());
        profile.getUnlockedPerkMap().put("tactical_insertions_perk", new PerkData("tactical_insertions_perk", 1));
        assertEquals(Material.BLAZE_ROD, button.getDisplayButtonItem(player).getType());
        assertEquals(100, button.getPrice(player));
    }

    @Test public void persistedMarkerIsConsumedExactlyOnceAndUnsafeGroundFallsBack() throws Exception {
        EconomyData.Deployment point = new EconomyData.Deployment();
        point.world = world.getUID().toString(); point.x = 10.5; point.y = 70; point.z = 10.5;
        profile.getEconomyData().deployment = point;
        ObjectMapper mapper = new ObjectMapper();
        profile.setEconomyData(mapper.readValue(mapper.writeValueAsBytes(profile.getEconomyData()), EconomyData.class));
        Location fallback = new Location(world, 0, 107, 0);
        Location destination = TacticalDeploymentShopButton.consumeRespawn(player, fallback);
        assertEquals(10.5, destination.getX(), 0); assertEquals(70, destination.getY(), 0);
        assertNull(profile.getEconomyData().deployment);
        assertSame(fallback, TacticalDeploymentShopButton.consumeRespawn(player, fallback));
        point.y = 100; profile.getEconomyData().deployment = point;
        assertSame(fallback, TacticalDeploymentShopButton.consumeRespawn(player, fallback));
        assertNull(profile.getEconomyData().deployment);
    }

    @Test public void usingOneDeviceConsumesOneItemAndRepeatedInteractionCannotConsumeAnother() {
        profile.getUnlockedPerkMap().put("tactical_insertions_perk", new PerkData("tactical_insertions_perk", 1));
        profile.setInArena(true);
        when(player.getLocation()).thenReturn(new Location(world, 10.5, 70, 10.5));
        TacticalDeploymentShopButton button = new TacticalDeploymentShopButton();
        org.bukkit.inventory.ItemStack held = new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD, 2);
        when(player.getItemInHand()).thenReturn(held);
        org.bukkit.event.player.PlayerInteractEvent event = new org.bukkit.event.player.PlayerInteractEvent(player,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, held, null, org.bukkit.block.BlockFace.SELF);
        try (MockedStatic<cn.charlotte.pit.util.item.ItemUtil> items = mockStatic(cn.charlotte.pit.util.item.ItemUtil.class)) {
            items.when(() -> cn.charlotte.pit.util.item.ItemUtil.getInternalName(held)).thenReturn("tactical_deployment");
            button.use(event); button.use(event);
        }
        assertNotNull(profile.getEconomyData().deployment);
        assertEquals(10.5, profile.getEconomyData().deployment.x, 0);
        verify(player, times(1)).setItemInHand(argThat(item -> item != null && item.getAmount() == 1));
    }
}
