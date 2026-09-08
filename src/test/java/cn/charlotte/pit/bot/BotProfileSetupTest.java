package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.MedalData;
import cn.charlotte.pit.medal.*;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BotProfileSetupTest {
    @Test public void nativeCombatMedalCallbacksHaveRecordsWithoutResettingProgress() {
        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
        ThePit plugin=mock(ThePit.class);MedalFactory factory=mock(MedalFactory.class);when(plugin.getMedalFactory()).thenReturn(factory);
        AbstractMedal medal=mock(AbstractMedal.class);when(medal.getInternalName()).thenReturn("vampire");when(medal.getMaxLevel()).thenReturn(2);
        when(factory.getMedals()).thenReturn(Collections.singletonList(medal));
        PlayerProfile profile=mock(PlayerProfile.class);MedalData data=new MedalData();when(profile.getMedalData()).thenReturn(data);
        BotProfileSetup.initializeMedals(plugin,profile);assertNotNull(data.getMedalStatus("vampire",1));assertNotNull(data.getMedalStatus("vampire",2));
        data.getMedalStatus("vampire",1).setProgress(123);BotProfileSetup.initializeMedals(plugin,profile);
        assertEquals(123,data.getMedalStatus("vampire",1).getProgress());assertEquals(2,data.getMedalStatus().size());
        }
    }
}
