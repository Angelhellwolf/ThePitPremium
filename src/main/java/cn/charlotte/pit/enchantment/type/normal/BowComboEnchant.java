package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.chat.RomanUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.time.TimeUtil;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.ItemStack;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/25 14:23
 */
@AutoRegister
@BowOnly
public class BowComboEnchant extends AbstractEnchantment implements IPlayerShootEntity, Listener, IActionDisplayEnchant {

    private static final String UUID_META = "bow_combo_enchant_uuid";
    private static final String HIT_META = "bow_combo_enchant_hit";
    public static final HashMap<UUID, Integer> combo = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "神射手";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "bow_combo_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public int getItemEnchantLevel(org.bukkit.inventory.ItemStack item) {
        int level = super.getItemEnchantLevel(item);
        if (level != -1) {
            return level;
        }
        return getLegacyEnchantLevel(item, "bow_combo");
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7连续 &e" + (enchantLevel >= 2 ? 2 : 3) + " &7次箭矢射出并命中为自身添加 &b速度 " + RomanUtil.convert(enchantLevel + 1) + " &f(" + TimeUtil.millisToTimer(4000) + ")";
    }

    @Override
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (combo.getOrDefault(attacker.getUniqueId(), 0) >= (enchantLevel >= 2 ? 2 : 3)) {
                    combo.put(attacker.getUniqueId(), 0);
                    attacker.removePotionEffect(PotionEffectType.SPEED);
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 4, enchantLevel, true));
                }
            }
        }.runTaskLater(ThePit.getInstance(), 2L);
    }

    @EventHandler
    public void onBowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        final org.bukkit.inventory.ItemStack itemInHand = player.getItemInHand();
        if (itemInHand == null) return;
        final int level = this.getItemEnchantLevel(itemInHand);
        if (level == -1) {
            return;
        }
        event.getProjectile().setMetadata(UUID_META, new FixedMetadataValue(ThePit.getInstance(), UUID.randomUUID().toString()));
    }

    @EventHandler
    public void onBowHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        if (!event.getEntity().hasMetadata(UUID_META)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        if (!event.getEntity().hasMetadata(HIT_META)) {
            Bukkit.getScheduler().runTaskLater(ThePit.getInstance(), () -> {
                if (!event.getEntity().hasMetadata(HIT_META)) {
                    combo.put(player.getUniqueId(), 0);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            if (event.getDamager().hasMetadata(UUID_META)) {
                Player player = (Player) ((Projectile) event.getDamager()).getShooter();
                combo.put(player.getUniqueId(), combo.getOrDefault(player.getUniqueId(), 0) + 1);
                event.getDamager().setMetadata(HIT_META, new FixedMetadataValue(ThePit.getInstance(), true));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combo.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public String getText(int level, Player player) {
        return "&e&l" + combo.getOrDefault(player.getUniqueId(), 0) + "/" + (level >= 2 ? 2 : 3);
    }

    private int getLegacyEnchantLevel(org.bukkit.inventory.ItemStack item, String legacyNbtName) {
        if (item == null || item.getType() == Material.AIR) {
            return -1;
        }
        ItemStack nmsItem = UtilKt.reflectGetNmsItem(item);
        if (nmsItem == null) {
            return -1;
        }
        NBTTagCompound tag = nmsItem.getTag();
        if (tag == null || !tag.hasKey("extra") || !tag.getCompound("extra").hasKey("ench")) {
            return -1;
        }
        NBTTagList list = tag.getCompound("extra").getList("ench", 8);
        for (int i = 0; i < list.size(); i++) {
            String string = list.getString(i);
            String[] split = string.split(":");
            if (split.length != 2) {
                continue;
            }
            if (legacyNbtName.equals(split[0])) {
                try {
                    return Integer.parseInt(split[1]);
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
