package cn.charlotte.pit.perk.type.prestige;

import cn.charlotte.pit.util.combat.PlayerLife;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FeedbackBalanceTest {
    @Test
    public void battleFuryHasAThreeSecondWindowAndEightSecondCooldownBelowSixTotalHearts() {
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Object life=new Object();BattleFuryPerk perk=new BattleFuryPerk();
        try(MockedStatic<PlayerLife> lives=mockStatic(PlayerLife.class)){
            lives.when(()->PlayerLife.isCurrent(player,life)).thenReturn(true);
            for(double invalid:new double[]{12,18,0,-1,Double.NaN})assertFalse(perk.activate(player,life,invalid,1000));
            assertTrue(perk.activate(player,life,11.9,1000));
            assertEquals(.05,perk.activeBonus(player,1,1001),0);
            assertEquals(.10,perk.activeBonus(player,2,2000),0);
            assertEquals(.20,perk.activeBonus(player,3,3999),0);
            assertEquals(0,perk.activeBonus(player,3,4000),0);
            assertFalse(perk.activate(player,life,8,8999));
            assertTrue(perk.activate(player,life,8,9000));
            lives.when(()->PlayerLife.isCurrent(player,life)).thenReturn(false);
            assertEquals(0,perk.activeBonus(player,3,9001),0);
        } finally {perk.onPerkInactive(player);}
    }

    @Test
    public void armorBreakAddsNormalDamageOnlyAgainstFourEquippedPieces() {
        Player target=mock(Player.class);PlayerInventory inventory=mock(PlayerInventory.class);when(target.getInventory()).thenReturn(inventory);
        Material[] materials={Material.IRON_BOOTS,Material.LEATHER_LEGGINGS,Material.DIAMOND_CHESTPLATE,Material.CHAINMAIL_HELMET};
        ArmorBreakPerk perk=new ArmorBreakPerk();
        for(int level=1;level<=3;level++)for(int pieces=0;pieces<=4;pieces++){
            ItemStack[] armor=new ItemStack[4];for(int i=0;i<pieces;i++)armor[i]=new ItemStack(materials[i]);
            when(inventory.getArmorContents()).thenReturn(armor);
            for(boolean arrow:new boolean[]{false,true}){
                AtomicDouble bonus=new AtomicDouble(1.4),trueDamage=new AtomicDouble(.75);
                if(arrow)perk.handleShootEntity(level,null,target,6,trueDamage,bonus,new AtomicBoolean());
                else perk.handleAttackEntity(level,null,target,6,trueDamage,bonus,new AtomicBoolean());
                assertEquals(1.4+(pieces==4?level*.05:0),bonus.get(),.000001);
                assertEquals(.75,trueDamage.get(),0);
            }
        }
        AtomicDouble bonus=new AtomicDouble(1);
        perk.handleAttackEntity(3,null,target,6,new AtomicDouble(),bonus,new AtomicBoolean(true));
        assertEquals(1,bonus.get(),0);
    }
}
