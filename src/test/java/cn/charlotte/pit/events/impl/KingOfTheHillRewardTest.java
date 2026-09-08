package cn.charlotte.pit.events.impl;

import cn.charlotte.pit.event.PitKillEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class KingOfTheHillRewardTest {
    @Test
    @SuppressWarnings("unchecked")
    public void bothHeightsUseTheCapturedEventFloorAndOutsideGoldNeverGrantsTheBonus() throws Exception {
        World world=mock(World.class);Player killer=mock(Player.class),victim=mock(Player.class);
        Location position=new Location(world,0,66,0);when(killer.getLocation()).thenReturn(position);
        Block air=mock(Block.class),floor=mock(Block.class),unrelated=mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);when(floor.getType()).thenReturn(Material.GOLD_BLOCK);when(unrelated.getType()).thenReturn(Material.GOLD_BLOCK);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call->{Location loc=call.getArgument(0);return loc.getBlockY()==64?(loc.getBlockX()==0?floor:unrelated):air;});
        KingOfTheHillEvent hill=new KingOfTheHillEvent();
        Field active=KingOfTheHillEvent.class.getDeclaredField("isActive");active.setAccessible(true);active.set(hill,true);
        Field blocks=KingOfTheHillEvent.class.getDeclaredField("blocks");blocks.setAccessible(true);((Map<Block,Material>)blocks.get(hill)).put(floor,Material.STONE);
        PitKillEvent kill=new PitKillEvent(killer,victim,20,10,true);hill.onKill(kill);
        assertEquals(80,kill.getCoins(),0);assertEquals(40,kill.getExp(),0);assertEquals(66,position.getY(),0);
        when(killer.getLocation()).thenReturn(new Location(world,20,66,0));
        kill=new PitKillEvent(killer,victim,20,10,true);hill.onKill(kill);
        assertEquals(20,kill.getCoins(),0);assertEquals(10,kill.getExp(),0);
        active.set(hill,false);when(killer.getLocation()).thenReturn(position);assertFalse(hill.isRegion(killer));
    }
}
