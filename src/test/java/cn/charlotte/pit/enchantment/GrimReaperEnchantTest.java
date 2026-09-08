package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.type.dark_normal.GrimReaperEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.combat.PlayerLife;
import cn.charlotte.pit.util.combat.SecondaryDamage;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GrimReaperEnchantTest {
    private Player player(World world,double x,double z){
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(new Location(world,x,64,z));
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));return player;
    }

    @Test
    public void waveIsSphericalMergesSameTickKillsAndNeverRecursesFromItsOwnKills() {
        World world=mock(World.class);Player killer=player(world,0,0),inside=player(world,3,4),outside=player(world,7,7),dead=player(world,1,1);
        when(dead.isDead()).thenReturn(true);when(world.getNearbyEntities(any(Location.class),eq(8.0),eq(8.0),eq(8.0))).thenReturn(Arrays.asList(killer,inside,outside,dead));
        ItemStack pants=new ItemStack(Material.LEATHER_LEGGINGS);when(killer.getInventory().getLeggings()).thenReturn(pants);
        GrimReaperEnchant enchant=spy(new GrimReaperEnchant());doReturn(1).when(enchant).getItemEnchantLevel(pants);
        Object life=new Object();ThePit plugin=mock(ThePit.class);List<Runnable> tasks=new ArrayList<>();BukkitScheduler scheduler=mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{tasks.add(call.getArgument(1));return mock(BukkitTask.class);});
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);MockedStatic<ThePit> pit=mockStatic(ThePit.class);
            MockedStatic<PlayerLife> lives=mockStatic(PlayerLife.class);MockedStatic<PlayerUtil> players=mockStatic(PlayerUtil.class)){
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);pit.when(ThePit::getInstance).thenReturn(plugin);
            lives.when(()->PlayerLife.capture(killer)).thenReturn(life);lives.when(()->PlayerLife.isCurrent(killer,life)).thenReturn(true);
            doAnswer(call->{assertTrue(SecondaryDamage.isGrimReaperKill(killer,inside));enchant.handlePlayerKilled(1,killer,inside,new AtomicDouble(10),new AtomicDouble(10));return null;})
                    .when(inside).damage(4.0,killer);
            AtomicDouble coins=new AtomicDouble(100),xp=new AtomicDouble(100);
            enchant.handlePlayerKilled(1,killer,inside,coins,xp);
            assertEquals(20,coins.get(),0);assertEquals(20,xp.get(),0);
            enchant.handlePlayerKilled(1,killer,outside,new AtomicDouble(100),new AtomicDouble(100));assertEquals(1,tasks.size());
            tasks.get(0).run();verify(inside).damage(4.0,killer);verify(outside,never()).damage(anyDouble(),any());verify(dead,never()).damage(anyDouble(),any());
            assertEquals(1,tasks.size());assertFalse(SecondaryDamage.isGrimReaperKill(killer,inside));
            enchant.handlePlayerKilled(1,killer,inside,new AtomicDouble(),new AtomicDouble());assertEquals(2,tasks.size());
            lives.when(()->PlayerLife.isCurrent(killer,life)).thenReturn(false);tasks.get(1).run();verify(inside,times(1)).damage(4.0,killer);
        }
    }

    @Test
    public void secondaryDamageMarkerCannotLeakAfterADamageHandlerFails() {
        Player attacker=mock(Player.class),target=mock(Player.class);when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageByEntityEvent event=new EntityDamageByEntityEvent(attacker,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        doAnswer(call->{assertTrue(SecondaryDamage.isHit(event));throw new IllegalStateException("test");}).when(target).damage(4.0,attacker);
        try {SecondaryDamage.grimReaper(attacker,target);fail("must throw");}catch(IllegalStateException expected){}
        assertFalse(SecondaryDamage.isHit(event));
    }
}
