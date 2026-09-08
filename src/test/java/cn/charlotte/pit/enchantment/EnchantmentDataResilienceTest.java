package cn.charlotte.pit.enchantment;

import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.enchantment.type.normal.PeroxideEnchant;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EnchantmentDataResilienceTest {
    @Test
    public void invalidEntriesDoNotHideAValidPeroxideEnchantLaterInTheItem() {
        ItemStack pants=new ItemStack(Material.LEATHER_LEGGINGS);
        net.minecraft.server.v1_8_R3.ItemStack nms=mock(net.minecraft.server.v1_8_R3.ItemStack.class);
        NBTTagCompound tag=new NBTTagCompound(),extra=new NBTTagCompound();NBTTagList list=new NBTTagList();
        list.add(new NBTTagString("broken-entry"));list.add(new NBTTagString("Peroxide:not-a-number"));list.add(new NBTTagString("Peroxide:3"));
        extra.set("ench",list);tag.set("extra",extra);when(nms.getTag()).thenReturn(tag);
        try(MockedStatic<UtilKt> util=mockStatic(UtilKt.class)){
            util.when(()->UtilKt.reflectGetNmsItem(pants)).thenReturn(nms);
            assertEquals(3,new PeroxideEnchant().getItemEnchantLevel(pants));
            assertEquals(-1,new PeroxideEnchant().getItemEnchantLevel(null));
        }
    }
}
