package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.item.ItemUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

@ArmorOnly
@AutoRegister
public class SnowmenArmyEnchant extends AbstractEnchantment implements IPlayerKilledEntity, IPlayerRespawn, Listener {

    private ItemBuilder snowBlock() {
        return new ItemBuilder(Material.SNOW_BLOCK)
                .internalName("snowmen_army_block")
                .canDrop(false)
                .canSaveToEnderChest(false)
                .deathDrop(true)
                .canTrade(false)
                .removeOnJoin(true)
                .name("&f雪块");
    }

    @Override
    public String getEnchantName() {
        return "雪人军团";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "snowmen_army_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7复活获得 &f3 * 雪块 &7,击杀获得 &f" + enchantLevel + " * 雪块 &7(上限" + (enchantLevel * 3 + 3) + ")"
                + "/s&7手持并右键消耗3块雪块召唤持续30秒的雪人";
    }

    private int getMaxHeld(int enchantLevel) {
        return enchantLevel * 3 + 3;
    }

    private void give(Player player, int amount, int enchantLevel) {
        int held = InventoryUtil.getAmountOfItem(player, "snowmen_army_block");
        int give = Math.min(amount, getMaxHeld(enchantLevel) - held);
        if (give > 0) {
            player.getInventory().addItem(snowBlock().amount(give).build());
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        give(myself, enchantLevel, enchantLevel);
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        give(myself, 3, enchantLevel);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!"snowmen_army_block".equals(ItemUtil.getInternalName(event.getItem()))) {
            return;
        }
        Player player = event.getPlayer();
        int level = getItemEnchantLevel(player.getInventory().getLeggings());
        if (level <= 0) {
            return;
        }
        if (InventoryUtil.getAmountOfItem(player, "snowmen_army_block") < 3) {
            return;
        }
        event.setCancelled(true);
        InventoryUtil.removeItem(player, "snowmen_army_block", 3);
        Snowman snowman = (Snowman) player.getWorld().spawnEntity(player.getLocation(), EntityType.SNOWMAN);
        snowman.setMetadata("snowmen_army", new FixedMetadataValue(ThePit.getInstance(), player.getUniqueId().toString()));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (snowman.isValid()) {
                    snowman.remove();
                }
            }
        }.runTaskLater(ThePit.getInstance(), 30 * 20L);
    }
}
