package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.enchantment.type.aqua.GrandmasterEnchant;
import cn.charlotte.pit.util.PlayerUtil;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GrandmasterEnchantTest {
    @Test
    public void fishingDamageUsesTheOriginalDamageEventAndNeverDirectlyEditsHealth() {
        Player shooter=mock(Player.class),target=mock(Player.class);
        when(shooter.getUniqueId()).thenReturn(UUID.randomUUID());when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory inventory=mock(PlayerInventory.class);when(shooter.getInventory()).thenReturn(inventory);
        ItemStack pants=new ItemStack(Material.LEATHER_LEGGINGS);when(inventory.getLeggings()).thenReturn(pants);
        FishHook hook=mock(FishHook.class);when(hook.getShooter()).thenReturn(shooter);
        GrandmasterEnchant enchant=spy(new GrandmasterEnchant());doReturn(1).when(enchant).getItemEnchantLevel(pants);
        try(MockedStatic<PlayerUtil> players=mockStatic(PlayerUtil.class)){
            EntityDamageByEntityEvent event=new EntityDamageByEntityEvent(hook,target,EntityDamageEvent.DamageCause.PROJECTILE,0);
            enchant.onDamage(event);assertEquals(1,event.getDamage(),0);
            verify(target,never()).setHealth(anyDouble());verify(target,never()).damage(anyDouble(),any());
            event.setCancelled(true);enchant.onDamage(event);assertEquals(1,event.getDamage(),0);
            event.setCancelled(false);players.when(()->PlayerUtil.shouldIgnoreEnchant(shooter,target)).thenReturn(true);
            enchant.onDamage(event);assertEquals(1,event.getDamage(),0);
        }
    }
}
