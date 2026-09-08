package cn.charlotte.pit.supporter;

import org.bukkit.entity.Player;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.UUID;
import static org.junit.Assert.*;

public class SupporterRuntimeCheckTest {
    @Test
    public void offlineMenuPlayerSupportsLegacyPrimitiveReturnBridgesWithoutJoiningTheServer() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = SupporterRuntimeCheck.createPlayer(id, "MenuCheck");
        assertEquals(id, player.getUniqueId());
        assertEquals("MenuCheck", player.getName());
        assertFalse(player.isOnline());
        assertFalse(player.hasPermission("pit.vip"));
        for (Method method : Player.class.getMethods()) {
            if (method.getName().equals("getLastDamage") && method.getParameterCount() == 0) {
                assertEquals(0.0, ((Number) method.invoke(player)).doubleValue(), 0.0);
            }
        }
    }
}
