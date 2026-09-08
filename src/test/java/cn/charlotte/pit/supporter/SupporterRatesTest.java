package cn.charlotte.pit.supporter;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.api.PitInternalHook;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.item.MythicColor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SupporterRatesTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ThePit> pit;
    private Player player;
    private PlayerProfile profile;
    private UUID id;
    private double[] old;

    @Before
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        pit = mockStatic(ThePit.class);
        PitInternalHook api = mock(PitInternalHook.class);
        when(api.getPitSupportPermission()).thenReturn("pit.vip");
        pit.when(ThePit::getApi).thenReturn(api);
        player = mock(Player.class);
        id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("RateTest");
        bukkit.when(() -> Bukkit.getPlayerExact("RateTest")).thenReturn(player);
        profile = new PlayerProfile(id, "RateTest");
        profile.setLoaded(true);
        PlayerProfile.getCacheProfile().put(id, profile);
        NewConfiguration config = NewConfiguration.INSTANCE;
        old = new double[]{config.getSupporterBaseDropChance(), config.getSupporterMythicDropChance(),
                config.getSupporterSacredDropChance(), config.getSupporterBaseRareChance(),
                config.getSupporterMythicRareChance(), config.getSupporterSacredRareChance(), config.getLuckGem()};
        config.setSupporterBaseDropChance(0.006);
        config.setSupporterMythicDropChance(0.0075);
        config.setSupporterSacredDropChance(0.0125);
        config.setSupporterBaseRareChance(0.006);
        config.setSupporterMythicRareChance(0.0075);
        config.setSupporterSacredRareChance(0.0125);
        config.setLuckGem(0.30);
    }

    @After
    public void tearDown() {
        PlayerProfile.getCacheProfile().remove(id);
        if (old != null) {
            NewConfiguration config = NewConfiguration.INSTANCE;
            config.setSupporterBaseDropChance(old[0]); config.setSupporterMythicDropChance(old[1]);
            config.setSupporterSacredDropChance(old[2]); config.setSupporterBaseRareChance(old[3]);
            config.setSupporterMythicRareChance(old[4]); config.setSupporterSacredRareChance(old[5]);
            config.setLuckGem(old[6]);
        }
        if (pit != null) pit.close();
        if (bukkit != null) bukkit.close();
    }

    private void assertRates(double expected) {
        assertEquals(expected, NewConfiguration.INSTANCE.getMythicDropChance(player), 0.00000001);
        for (MythicColor color : MythicColor.values()) {
            assertEquals(expected, NewConfiguration.INSTANCE.getChance(player, color), 0.00000001);
        }
    }

    @Test
    public void ordinaryAndPermanentPlayersHaveTheSameBaselineEvenWithWildcardPermissions() {
        profile.applySupporterAccount(SupporterAccount.empty());
        assertRates(0.006);
        when(player.hasPermission(anyString())).thenReturn(true);
        assertRates(0.006);
        profile.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.NONE, 0L, 1));
        assertRates(0.006);
    }

    @Test
    public void activeTiersSelectAnAbsoluteProbabilityWithoutStackingOldPermissions() {
        when(player.hasPermission(anyString())).thenReturn(true);
        profile.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.MYTHIC,
                System.currentTimeMillis() + 60000, 1));
        assertRates(0.0075);
        profile.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED,
                System.currentTimeMillis() + 60000, 2));
        assertRates(0.0125);
    }

    @Test
    public void contractExpiryRestoresBaselineWithoutRemovingPermanentMembership() {
        profile.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED,
                System.currentTimeMillis() - 1000, 1));
        assertTrue(profile.isSupporter());
        assertRates(0.006);
        assertEquals("&e✬", profile.getSupporterStar());
    }

    @Test
    public void leftoverContractDataCannotGrantAnUpgradeWithoutSupporterAccess() {
        profile.applySupporterAccount(new SupporterAccount(false, false, SupporterTier.SACRED,
                System.currentTimeMillis() + 60000, 1));
        assertFalse(profile.isSupporter());
        assertRates(0.006);
    }

    @Test
    public void luckyGemKeepsItsOwnChanceOnEveryPaidTier() {
        when(player.hasMetadata("lucky")).thenReturn(true);
        for (SupporterTier tier : new SupporterTier[]{SupporterTier.MYTHIC, SupporterTier.SACRED}) {
            profile.applySupporterAccount(new SupporterAccount(true, false, tier,
                    System.currentTimeMillis() + 60000, tier.getId()));
            assertEquals(0.30, NewConfiguration.INSTANCE.getChance(player, MythicColor.ORANGE), 0.00000001);
        }
    }

    @Test
    public void oldAsyncResponsesCannotOverwriteANewerContract() {
        profile.applySupporterAccount(new SupporterAccount(true, false, SupporterTier.SACRED,
                System.currentTimeMillis() + 60000, 5));
        profile.applySupporterAccount(new SupporterAccount(false, false, SupporterTier.NONE, 0L, 4));
        assertRates(0.0125);
        assertEquals(5, profile.getSupporterAccount().getRevision());
    }

    @Test
    public void serializedSupporterFlagDoesNotPersistExternalPermissionsAsAPurchase() throws Exception {
        when(player.hasPermission(anyString())).thenReturn(true);
        profile.applySupporterAccount(SupporterAccount.empty());
        assertTrue(profile.isSupporter());
        ObjectMapper mapper = new ObjectMapper();
        List<BeanPropertyDefinition> properties = mapper.getSerializationConfig()
                .introspect(mapper.constructType(PlayerProfile.class)).findProperties();
        BeanPropertyDefinition supporter = properties.stream().filter(p -> p.getName().equals("supporter")).findFirst().orElseThrow(AssertionError::new);
        assertEquals(false, supporter.getAccessor().getValue(profile));
        assertFalse(properties.stream().anyMatch(p -> p.getName().equals("supporterAccount")));
        assertFalse(properties.stream().anyMatch(p -> p.getName().equals("activeSupporterTier")));
    }

    @Test
    public void jacksonCanRestoreTheMirroredEntitlementsAfterARestart() throws Exception {
        PlayerProfile restored = new PlayerProfile(id, "RateTest");
        long expiry = System.currentTimeMillis() + 60000;
        new ObjectMapper().readerForUpdating(restored).readValue("{\"supporter\":true,\"supporterContractTier\":2,"
                + "\"supporterContractExpiresAt\":" + expiry + "}");
        assertTrue(restored.getSupporter());
        assertTrue(restored.isSupporter());
        assertEquals(SupporterTier.SACRED, restored.getActiveSupporterTier());
        assertEquals(expiry, restored.getSupporterContractExpiresAt());
    }
}
