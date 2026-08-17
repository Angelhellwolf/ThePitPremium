package cn.charlotte.pit.runnable;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.sub.PerkData;
import cn.charlotte.pit.data.temp.TradeRequest;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.perf.PerfMetrics;
import lombok.Getter;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/5 0:30
 */
public class GameRunnable extends BukkitRunnable {
    @Getter
    private final static List<TradeRequest> tradeRequests = new ArrayList<>();


    private long tick = 0;

    @Override
    public void run() {
        final long startNs = System.nanoTime();
        final Map<String, ITickTask> perkTasks = ThePit.getInstance().getPerkFactory().getTickTasks();
        final Map<String, ITickTask> enchantmentTasks = ThePit.getInstance().getEnchantmentFactor().getTickTasks();
        int players = 0;
        int tasksExecuted = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            players++;
            CraftPlayer craftPlayer = (CraftPlayer) player;
            if (craftPlayer.getHandle().getAbsorptionHearts() > 40.0F) {
                craftPlayer.getHandle().setAbsorptionHearts(40.0F);
            }

            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
            if (profile == null) {
                continue;
            }

            tasksExecuted += runPerkTasks(profile.getChosePerk().values(), perkTasks, player);
            tasksExecuted += runPerkTasks(profile.getUnlockedPerkMap().values(), perkTasks, player);

            //裤子
            final ItemStack leggings = player.getInventory().getLeggings();
            tasksExecuted += runEnchantmentTasks(leggings, enchantmentTasks, player);

            ItemStack itemInHand = player.getInventory().getItemInHand();
            if (itemInHand != null && itemInHand.getType() != Material.AIR && itemInHand.getType() != Material.LEATHER_LEGGINGS && itemInHand.getType() != Material.PAPER) {
                tasksExecuted += runEnchantmentTasks(itemInHand, enchantmentTasks, player);
            }
        }

        // This collection is global, so cleaning it once per tick is sufficient.
        if (!tradeRequests.isEmpty()) {
            tradeRequests.removeIf(tradeRequest -> tradeRequest.getCooldown().hasExpired());
        }

        tick++;
        PerfMetrics.addCount("game.players", players);
        PerfMetrics.addCount("game.tasks.executed", tasksExecuted);
        PerfMetrics.recordTime("game.tick", System.nanoTime() - startNs);
    }

    private int runPerkTasks(Iterable<PerkData> perks, Map<String, ITickTask> tasks, Player player) {
        int executed = 0;
        for (PerkData perk : perks) {
            final ITickTask task = tasks.get(perk.getPerkInternalName());
            if (task == null || !isDue(task, perk.getLevel())) {
                continue;
            }

            task.handle(perk.getLevel(), player);
            executed++;
        }
        return executed;
    }

    /**
     * Reads only the enchantment NBT needed by tick tasks. Constructing a full
     * IMythicItem also parses UUIDs, colors, records and durability, none of
     * which are used by this hot path.
     */
    private int runEnchantmentTasks(ItemStack item, Map<String, ITickTask> tasks, Player player) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        net.minecraft.server.v1_8_R3.ItemStack nmsItem = UtilKt.reflectGetNmsItem(item);
        if (nmsItem == null) {
            return 0;
        }

        NBTTagCompound tag = nmsItem.getTag();
        if (tag == null || !tag.hasKey("extra")) {
            return 0;
        }

        NBTTagCompound extra = tag.getCompound("extra");
        String internalName = extra.getString("internal");
        if (!supportsEnchantmentTicks(internalName) || !extra.hasKey("ench")) {
            return 0;
        }

        NBTTagList enchantments = extra.getList("ench", 8);
        int executed = 0;
        for (int i = 0; i < enchantments.size(); i++) {
            String encoded = enchantments.getString(i);
            int separator = encoded.lastIndexOf(':');
            if (separator <= 0 || separator >= encoded.length() - 1) {
                continue;
            }

            ITickTask task = tasks.get(encoded.substring(0, separator));
            if (task == null) {
                continue;
            }

            final int level;
            try {
                level = Integer.parseInt(encoded.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (isDue(task, level)) {
                task.handle(level, player);
                executed++;
            }
        }
        return executed;
    }

    private boolean isDue(ITickTask task, int level) {
        int loopTicks = task.loopTick(level);
        return loopTicks > 0 && tick % loopTicks == 0;
    }

    private static boolean supportsEnchantmentTicks(String internalName) {
        return "mythic_leggings".equals(internalName)
                || "mythic_sword".equals(internalName)
                || "mythic_bow".equals(internalName);
    }

    private boolean isNewYear() {
        return java.time.Month.JANUARY.equals(java.time.Month.JANUARY) && java.time.LocalTime.now().equals(java.time.LocalTime.of(12, 0));
    }
}
