package cn.charlotte.pit.reward;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.type.normal.CriticallyRichEnchant;
import cn.charlotte.pit.enchantment.type.normal.StrikeGoldEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HitCoinRewardsTest {
    @Test public void hitCoinsRequireAHumanTargetAndAnUncancelledPositiveHit() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            PlayerProfile recipient = profile(), victim = profile();
            try {
                Player attacker = mock(Player.class), target = mock(Player.class);
                when(attacker.getUniqueId()).thenReturn(recipient.getPlayerUuid());
                when(target.getUniqueId()).thenReturn(victim.getPlayerUuid());
                players.when(() -> PlayerUtil.isCritical(attacker)).thenReturn(true);
                StrikeGoldEnchant strike = new StrikeGoldEnchant();
                CriticallyRichEnchant critical = new CriticallyRichEnchant();
                victim.setBot(true);
                strike.handleAttackEntity(3, attacker, target, 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                critical.handleAttackEntity(3, attacker, target, 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                critical.handleAttackEntity(3, attacker, mock(Entity.class), 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                victim.setBot(false);
                strike.handleAttackEntity(3, attacker, target, 0, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                critical.handleAttackEntity(3, attacker, target, 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean(true));
                assertEquals(0, recipient.getCoins(), 0);
                strike.handleAttackEntity(3, attacker, target, 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                critical.handleAttackEntity(3, attacker, target, 4, new AtomicDouble(), new AtomicDouble(), new AtomicBoolean());
                assertEquals(9, recipient.getCoins(), 0);
            } finally {
                PlayerProfile.getCacheProfile().remove(recipient.getPlayerUuid());
                PlayerProfile.getCacheProfile().remove(victim.getPlayerUuid());
            }
        }
    }
    private PlayerProfile profile() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "HitGoldTest");
        profile.setPrestige(20); profile.setLoaded(true); profile.setCoins(0);
        PlayerProfile.getCacheProfile().put(profile.getPlayerUuid(), profile);
        return profile;
    }
}
