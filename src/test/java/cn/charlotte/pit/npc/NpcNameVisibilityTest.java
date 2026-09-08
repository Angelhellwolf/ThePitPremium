package cn.charlotte.pit.npc;

import net.jitse.npclib.NPCLib;
import net.jitse.npclib.internal.NPCBase;
import net.jitse.npclib.internal.NPCManager;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NpcNameVisibilityTest {
    @Test public void unchangedHologramRehidesInternalNameAfterViewerScoreboardReplacement() {
        NPCBase npc = mock(NPCBase.class, withSettings().useConstructor(mock(NPCLib.class), List.of("Title"))
                .defaultAnswer(CALLS_REAL_METHODS));
        try {
            Player viewer = mock(Player.class);
            when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
            Scoreboard first = mock(Scoreboard.class), replacement = mock(Scoreboard.class);
            Team firstTeam = mock(Team.class), replacementTeam = mock(Team.class);
            when(first.registerNewTeam(anyString())).thenReturn(firstTeam);
            when(replacement.registerNewTeam(anyString())).thenReturn(replacementTeam);
            when(viewer.getScoreboard()).thenReturn(first, replacement);
            npc.setText(viewer, List.of("Title"));
            npc.setText(viewer, List.of("Title"));
            verify(firstTeam).setNameTagVisibility(NameTagVisibility.NEVER);
            verify(replacementTeam).setNameTagVisibility(NameTagVisibility.NEVER);
            verify(replacementTeam).addEntry(anyString());
            assertEquals(List.of("Title"), npc.getText(viewer));
        } finally {
            NPCManager.getAllNPCs().remove(npc);
        }
    }
}
