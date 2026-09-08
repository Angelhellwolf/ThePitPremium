package cn.charlotte.pit.npc;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.event.PitProfileLoadedEvent;
import cn.charlotte.pit.npc.runnable.NpcRunnable;
import cn.charlotte.pit.util.perf.PerfMetrics;
import lombok.SneakyThrows;
import net.jitse.npclib.NPCLib;
import net.jitse.npclib.api.NPC;
import net.jitse.npclib.api.events.NPCInteractEvent;
import net.jitse.npclib.api.state.NPCSlot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @Author: EmptyIrony
 * @Date: 2020/12/30 22:35
 */
public class NpcFactory implements Listener {
    private static final List<AbstractPitNPC> pitNpc = new ArrayList<>();

    public static List<AbstractPitNPC> getPitNpc() {
        return NpcFactory.pitNpc;
    }

    @SneakyThrows
    public void init(Collection<Class<? extends AbstractPitNPC>> classes) {
        NPCLib npcLib = new NPCLib(ThePit.getInstance());

        for (Class<?> clazz : classes) {
            if (!ThePit.isDEBUG_SERVER()) {
                if (clazz.getName().contains("InfinityItemNPC")) {
                    continue;
                }

            }
            if (AbstractPitNPC.class.isAssignableFrom(clazz)) {
                AbstractPitNPC abstractPitNPC = (AbstractPitNPC) clazz.newInstance();

                NPC npc = npcLib.createNPC();
                npc.setLocation(abstractPitNPC.getNpcSpawnLocation());

                if (abstractPitNPC.getNpcHeldItem() != null) {
                    npc.setItem(NPCSlot.MAINHAND, abstractPitNPC.getNpcHeldItem());
                }

                abstractPitNPC.setNpc(npc);
                abstractPitNPC.initSkin(npc);

                pitNpc.add(abstractPitNPC);
            }
        }

        long period = Math.max(10L, NewConfiguration.INSTANCE.getPerfNpcTextUpdateTicks());
        new NpcRunnable().runTaskTimer(ThePit.getInstance(), period, period);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!NewConfiguration.INSTANCE.getPerfNpcForceVisibleEnable()) {
            return;
        }
        // Fallback: show NPCs on join even if PitProfileLoadedEvent is delayed/missed.
        Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }
            for (AbstractPitNPC pitNpc : pitNpc) {
                try {
                    if (!pitNpc.getNpc().isCreated()) {
                        pitNpc.getNpc().create();
                    }
                    if (!pitNpc.getNpc().isShown(player)) {
                        pitNpc.getNpc().show(player);
                    }
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("[ThePitPremium] Failed to show NPC '" + pitNpc.getNpcInternalName() + "' to " + player.getName() + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
                }
            }
        }, 10L);
    }

    @EventHandler
    @SneakyThrows
    public void onPlayerJoin(PitProfileLoadedEvent event) {
        final long startNs = System.nanoTime();
        Player player = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
        if (player == null || !player.isOnline()) {
            return;
        }

        for (AbstractPitNPC pitNpc : pitNpc) {
            if (!pitNpc.getNpc().isCreated()) {
                pitNpc.getNpc().create();
            }
            if (!pitNpc.getNpc().isShown(player)) {
                pitNpc.getNpc().show(player);
            }
            pitNpc.getNpc()
                    .setText(player, pitNpc.getNpcTextLine(player));
        }

        PerfMetrics.addCount("npc.profileLoaded.players", 1);
        PerfMetrics.addCount("npc.profileLoaded.npcs", pitNpc.size());
        PerfMetrics.recordTime("npc.profileLoaded", System.nanoTime() - startNs);

    }

    @EventHandler
    public void onInteract(NPCInteractEvent event) {
        for (AbstractPitNPC abstractPitNPC : pitNpc) {
            if (abstractPitNPC.getNpc().getUniqueId().equals(event.getNPC().getUniqueId())) {
                abstractPitNPC.handlePlayerInteract(event.getWhoClicked());
            }
        }
    }

}
