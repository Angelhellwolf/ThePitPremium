package cn.charlotte.pit.junxu;

import cn.charlotte.pit.supporter.SupporterTier;
import org.bukkit.World;
import org.junit.Test;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JunxuTrainingSettingsTest {
    @Test public void settingsSurviveReopenAndRemainIndependent() throws Exception {
        Path dir=Files.createTempDirectory("junxu-settings-test"),file=dir.resolve("settings.yml");
        try {
            UUID owner=UUID.randomUUID(),other=UUID.randomUUID();JunxuSettingsStore store=new JunxuSettingsStore(file,200);
            JunxuTrainingSettings value=JunxuTrainingSettings.defaults().health(80).armor(JunxuTrainingSettings.Armor.DIAMOND)
                    .weapon(JunxuTrainingSettings.Weapon.NONE).mode(JunxuTrainingSettings.Mode.PASSIVE)
                    .route(JunxuTrainingSettings.Route.CROSS).strength(JunxuTrainingSettings.Strength.EASY).paused(true);
            store.put(owner,value);store=new JunxuSettingsStore(file,200);JunxuTrainingSettings saved=store.get(owner);
            assertEquals(80,saved.health,0);assertEquals(JunxuTrainingSettings.Armor.DIAMOND,saved.armor);
            assertEquals(JunxuTrainingSettings.Weapon.NONE,saved.weapon);assertEquals(JunxuTrainingSettings.Mode.PASSIVE,saved.mode);
            assertEquals(JunxuTrainingSettings.Route.CROSS,saved.route);assertEquals(JunxuTrainingSettings.Strength.EASY,saved.strength);assertTrue(saved.paused);
            assertEquals(20,store.get(other).health,0);assertFalse(store.get(other).paused);
            JunxuSettingsStore finalStore=store;assertThrows(IllegalArgumentException.class,()->finalStore.put(owner,value.health(201)));
            assertEquals(80,store.get(owner).health,0);
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(dir.resolve("settings.yml.tmp"));Files.deleteIfExists(dir);}
    }
    @Test public void changesPreserveOtherParametersAndRejectInvalidHealth(){
        JunxuTrainingSettings value=JunxuTrainingSettings.defaults().mode(JunxuTrainingSettings.Mode.RETALIATE).paused(true).health(1);
        assertEquals(JunxuTrainingSettings.Mode.RETALIATE,value.mode);assertTrue(value.paused);assertEquals(1,value.health,0);
        assertThrows(IllegalArgumentException.class,()->value.health(0));assertThrows(IllegalArgumentException.class,()->value.health(Double.NaN));
        assertTrue(JunxuTrainingSettings.Strength.EASY.attackTicks>JunxuTrainingSettings.Strength.HARD.attackTicks);
        assertTrue(JunxuTrainingSettings.Strength.EASY.speed<JunxuTrainingSettings.Strength.HARD.speed);
    }
    @Test public void projectionsAreVisibleOnlyInsideTheirAuthorizedRoom(){
        World world=mock(World.class),lobby=mock(World.class);UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),stranger=UUID.randomUUID();
        JunxuRoom room=new JunxuRoom(owner,"Owner",1,world,SupporterTier.SACRED);room.guests.add(guest);
        assertTrue(JunxuVisibility.canView(owner,room.arrival(),room));assertTrue(JunxuVisibility.canView(guest,room.arrival(),room));
        assertFalse(JunxuVisibility.canView(stranger,room.arrival(),room));
        assertFalse(JunxuVisibility.canView(owner,new org.bukkit.Location(lobby,542,71,11),room));
        assertFalse(JunxuVisibility.canView(owner,new org.bukkit.Location(world,1054,71,11),room));
        room.open=false;assertFalse(JunxuVisibility.canView(owner,room.arrival(),room));
    }
}
