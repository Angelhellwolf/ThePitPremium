package cn.charlotte.pit.listener;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.event.PotionAddEvent;
import cn.charlotte.pit.events.impl.major.RedVSBlueEvent;
import cn.charlotte.pit.util.item.ItemBuilder;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.MapMaker;
import lombok.SneakyThrows;
import net.minecraft.server.v1_8_R3.MobEffectList;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEffect;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2021/2/4 14:56
 */
public class PacketListener extends PacketAdapter {
    // Live NMS effects are weak keys: expiry/logout cannot retain players here.
    private final Map<Object, EffectObservation> processedEffects = new MapMaker().weakKeys().makeMap();

    private static final class EffectObservation {
        private final PotionEffect effect;
        private final int tick = net.minecraft.server.v1_8_R3.MinecraftServer.currentTick;

        private EffectObservation(PotionEffect effect) {
            this.effect = effect;
        }
    }

    public PacketListener() {
        super(ThePit.getInstance(), PacketType.Play.Server.ENTITY_EQUIPMENT, PacketType.Play.Server.ENTITY_EFFECT);
    }

    @Override
    @SneakyThrows
    public void onPacketSending(PacketEvent event) {
        final Packet packet = (Packet) event.getPacket().getHandle();
        final Player player = event.getPlayer();

        try {
            if (packet instanceof PacketPlayOutEntityEquipment) {
                if ("red_vs_blue".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName())) {
                    PacketPlayOutEntityEquipment equipment = (PacketPlayOutEntityEquipment) packet;
                    Class<? extends PacketPlayOutEntityEquipment> clazz = equipment.getClass();
                    Field b = clazz.getDeclaredField("b");
                    b.setAccessible(true);
                    Field c = clazz.getDeclaredField("c");
                    c.setAccessible(true);
                    Field a = clazz.getDeclaredField("a");
                    a.setAccessible(true);
                    if ((int) (b.get(packet)) == 4) {
                        RedVSBlueEvent activeEpicEvent = (RedVSBlueEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                        for (Player target : Bukkit.getOnlinePlayers()) {
                            if (target.getEntityId() == (int) a.get(packet)) {
                                if (activeEpicEvent.getRedTeam().contains(target.getUniqueId())) {
                                    c.set(packet, CraftItemStack.asNMSCopy(new ItemBuilder(Material.WOOL).durability(14).build()));
                                } else if (activeEpicEvent.getBlueTeam().contains(target.getUniqueId())) {
                                    c.set(packet, CraftItemStack.asNMSCopy(new ItemBuilder(Material.WOOL).durability(11).build()));
                                }
                                break;
                            }
                        }
                    }
                }
            } else if (packet instanceof PacketPlayOutEntityEffect) {
                final PacketContainer container = PacketContainer.fromPacket(packet);
                final Integer entityId = container.getIntegers().read(0);
                if (player.getEntityId() != entityId) return;

                final PotionEffectType potion = PotionEffectType.getById(container.getBytes().read(0));
                if (potion == null) return;
                // Read the actual server effect on the main thread. Packet duration/amplifier
                // used to be reversed, and delayed cancellation removed replacement effects.
                Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    Object identity = ((CraftPlayer) player).getHandle().getEffect(MobEffectList.byId[potion.getId()]);
                    if (identity == null) return;
                    PotionEffect current = player.getActivePotionEffects().stream()
                            .filter(effect -> effect.getType().equals(potion)).findFirst().orElse(null);
                    if (current == null) return;
                    EffectObservation previous = processedEffects.get(identity);
                    int tick = net.minecraft.server.v1_8_R3.MinecraftServer.currentTick;
                    if (previous != null && PotionEffectUpdate.isCountdown(previous.effect, current, tick - previous.tick)) return;
                    processedEffects.put(identity, new EffectObservation(current));
                    PotionAddEvent potionEvent = new PotionAddEvent(player, current);
                    potionEvent.callEvent();
                    if (potionEvent.isCancelled()) {
                        player.removePotionEffect(potion);
                    } else if (!current.equals(potionEvent.getEffect())) {
                        player.removePotionEffect(potion);
                        player.addPotionEffect(potionEvent.getEffect(), true);
                        Object replacement = ((CraftPlayer) player).getHandle().getEffect(MobEffectList.byId[potion.getId()]);
                        if (replacement != null) processedEffects.put(replacement, new EffectObservation(potionEvent.getEffect()));
                    }
                }, 1L);
            }
        } catch (Exception ignored) {
            // Prevent reflection/packet errors from propagating and kicking players
        }
    }
}
