package cn.charlotte.pit.util.sound;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/26 13:32
 */
public abstract class AbstractPitSound {
    private final Map<Player, Integer> playersTick;
    private final Map<Player, Integer> pendingPlayers;
    private final Set<Player> pendingRemovals;
    private boolean ticking;

    public AbstractPitSound() {
        this.playersTick = new HashMap<>();
        this.pendingPlayers = new HashMap<>();
        this.pendingRemovals = new HashSet<>();
    }

    public abstract String getMusicInternalName();

    public abstract void onSoundTick(Player player, int tick);

    public synchronized void tick() {
        if (playersTick.isEmpty()) {
            return;
        }

        ticking = true;
        try {
            final Iterator<Map.Entry<Player, Integer>> iterator = playersTick.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<Player, Integer> entry = iterator.next();
                final Player player = entry.getKey();
                if (player == null || !player.isOnline() || pendingRemovals.remove(player)) {
                    iterator.remove();
                    continue;
                }

                final int currentTick = entry.getValue();
                this.onSoundTick(player, currentTick);

                // Sound implementations end themselves from onSoundTick. Defer that
                // structural mutation until after the callback so the iterator remains valid.
                if (pendingRemovals.remove(player)) {
                    iterator.remove();
                } else {
                    entry.setValue(currentTick + 1);
                }
            }
        } finally {
            ticking = false;
            if (!pendingRemovals.isEmpty()) {
                playersTick.keySet().removeAll(pendingRemovals);
                pendingRemovals.clear();
            }
            if (!pendingPlayers.isEmpty()) {
                playersTick.putAll(pendingPlayers);
                pendingPlayers.clear();
            }
        }
    }

    public synchronized void play(Player player) {
        if (player == null) {
            return;
        }
        if (ticking) {
            pendingRemovals.remove(player);
            pendingPlayers.put(player, 0);
            return;
        }
        playersTick.put(player, 0);
    }

    public synchronized void end(Player player) {
        if (player == null) {
            return;
        }
        if (ticking) {
            pendingPlayers.remove(player);
            pendingRemovals.add(player);
            return;
        }
        playersTick.remove(player);
    }

    public synchronized boolean hasActivePlayers() {
        return !playersTick.isEmpty() || !pendingPlayers.isEmpty();
    }

}
