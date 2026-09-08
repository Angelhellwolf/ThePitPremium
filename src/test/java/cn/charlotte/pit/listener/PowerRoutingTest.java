package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.EnchantmentFactor;
import cn.charlotte.pit.enchantment.type.normal.PowerEnchant;
import cn.charlotte.pit.events.EventFactory;
import cn.charlotte.pit.game.Game;
import cn.charlotte.pit.perk.PerkFactory;
import cn.charlotte.pit.quest.QuestFactory;
import cn.charlotte.pit.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class PowerRoutingTest {
    @Test public void powerSurvivesSwitchingWeaponsAndAddsTheListedDamageThroughTheArrowHandler() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ThePit> pit = mockStatic(ThePit.class);
             MockedStatic<PlayerProfile> profiles = mockStatic(PlayerProfile.class);
             MockedStatic<PlayerUtil> players = mockStatic(PlayerUtil.class);
             MockedStatic<UtilKt> items = mockStatic(UtilKt.class)) {
            ThePit plugin = mock(ThePit.class);
            pit.when(ThePit::getInstance).thenReturn(plugin);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            when(plugin.getPerkFactory()).thenReturn(new PerkFactory());
            when(plugin.getQuestFactory()).thenReturn(new QuestFactory());
            when(plugin.getEventFactory()).thenReturn(mock(EventFactory.class));
            when(plugin.getGame()).thenReturn(mock(Game.class));
            EnchantmentFactor factory = new EnchantmentFactor();
            when(plugin.getEnchantmentFactor()).thenReturn(factory);
            PowerEnchant power = spy(new PowerEnchant());
            factory.getPlayerShootEntities().add(power);
            factory.getEnchantmentMap().put("Power", power);
            Player shooter = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            when(shooter.getUniqueId()).thenReturn(uuid);
            when(shooter.getInventory()).thenReturn(mock(PlayerInventory.class));
            PlayerProfile profile = mock(PlayerProfile.class);
            when(profile.getPrestige()).thenReturn(20);
            profiles.when(() -> PlayerProfile.getPlayerProfileByUuid(uuid)).thenReturn(profile);
            ItemStack bow = new ItemStack(Material.BOW);
            GameEffectListener listener = new GameEffectListener();
            for (int level = 0; level <= 3; level++) {
                net.minecraft.server.v1_8_R3.ItemStack nms = mock(net.minecraft.server.v1_8_R3.ItemStack.class);
                NBTTagCompound tag = new NBTTagCompound(), extra = new NBTTagCompound();
                NBTTagList enchants = new NBTTagList();
                enchants.add(new NBTTagString("Power:" + level));
                extra.set("ench", enchants); tag.set("extra", extra);
                when(nms.getTag()).thenReturn(tag);
                items.when(() -> UtilKt.reflectGetNmsItem(bow)).thenReturn(nms);
                Arrow arrow = mock(Arrow.class);
                when(arrow.getShooter()).thenReturn(shooter);
                Map<String, MetadataValue> metadata = new HashMap<>();
                doAnswer(call -> { metadata.put(call.getArgument(0), call.getArgument(1)); return null; })
                        .when(arrow).setMetadata(anyString(), any(MetadataValue.class));
                when(arrow.hasMetadata(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
                when(arrow.getMetadata(anyString())).thenAnswer(call -> {
                    MetadataValue value = metadata.get(call.getArgument(0));
                    return value == null ? Collections.emptyList() : Collections.singletonList(value);
                });
                EntityShootBowEvent shot = mock(EntityShootBowEvent.class);
                when(shot.getEntity()).thenReturn(shooter);
                when(shot.getBow()).thenReturn(bow);
                when(shot.getProjectile()).thenReturn(arrow);
                listener.addEnchantToArrow(shot);
                when(shooter.getItemInHand()).thenReturn(new ItemStack(Material.IRON_SWORD));
                EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(arrow, mock(LivingEntity.class),
                        EntityDamageEvent.DamageCause.PROJECTILE, 10);
                listener.onPlayerDamagePlayer(hit);
                double expected = new double[]{10, 10.7, 11.2, 12}[level];
                assertEquals("Power level " + level, expected, hit.getDamage(), 0.000001);
            }
        }
    }
}
