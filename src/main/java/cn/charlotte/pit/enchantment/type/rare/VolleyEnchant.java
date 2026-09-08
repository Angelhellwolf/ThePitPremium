package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/8 16:55
 */
@AutoRegister
@BowOnly
public class VolleyEnchant extends AbstractEnchantment implements Listener {
    private static final HashMap<UUID, Cooldown> cooldown = new HashMap<>();
    private final Map<UUID, Boolean> isShooting = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "连续射击";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "volley_enchant";
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
        return "&7非潜行射箭时追加 &e" + (enchantLevel + 1) + " &7支箭矢，共 &e" + (enchantLevel + 2) + " &7支"
                + "/s&7追加箭矢间隔0.1秒，连射期间不重复触发";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldown.remove(e.getPlayer().getUniqueId());
        isShooting.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (PlayerUtil.shouldIgnoreEnchant(player)) return;
        final org.bukkit.inventory.ItemStack itemInHand = player.getItemInHand();
        if (itemInHand == null) return;
        final int level = Math.min(3, this.getItemEnchantLevel(itemInHand));
        if (level == -1) {
            return;
        }
        if (isShooting.getOrDefault(player.getUniqueId(), false)) {
            return;
        }
        if (player.isSneaking()) {
            return;
        }
        if (!(event.getProjectile() instanceof Projectile)) {
            return;
        }
        if (itemInHand.getType() != Material.BOW) {
            return;
        }
        try {
            if (!cooldown.getOrDefault(player.getUniqueId(), new Cooldown(0)).hasExpired()) {
                return;
            }
            // Keep the original shot and clone extra arrows from it. Recreating
            // arrows through ItemBow#a can fail to launch or knock the shooter.
            final Projectile source = (Projectile) event.getProjectile();
            final Object life = cn.charlotte.pit.util.combat.PlayerLife.capture(player);
            final double arrowSpeed = source.getVelocity().length();
            final List<MetadataValue> enchantMeta = source.hasMetadata("enchant") ? source.getMetadata("enchant") : null;
            isShooting.put(player.getUniqueId(), true);
            new BukkitRunnable() {
                int extra = 0;

                @Override
                public void run() {
                    if (!cn.charlotte.pit.util.combat.PlayerLife.isCurrent(player, life) || extra >= level + 1) {
                        cooldown.put(player.getUniqueId(), new Cooldown(15, TimeUnit.MILLISECONDS));
                        isShooting.remove(player.getUniqueId());
                        cancel();
                        return;
                    }
                    extra++;
                    Vector velocity = player.getEyeLocation().getDirection().normalize().multiply(arrowSpeed);
                    Arrow extraArrow = player.launchProjectile(Arrow.class, velocity);
                    extraArrow.setShooter(player);
                    extraArrow.setCritical(source instanceof Arrow && ((Arrow) source).isCritical());
                    extraArrow.setBounce(false);
                    extraArrow.setFireTicks(source.getFireTicks());
                    if (enchantMeta != null && !enchantMeta.isEmpty()) {
                        extraArrow.setMetadata("enchant", enchantMeta.get(0));
                    }
                    if (!source.getMetadata("pit_shot_sneaking").isEmpty()) {
                        extraArrow.setMetadata("pit_shot_sneaking", source.getMetadata("pit_shot_sneaking").get(0));
                    }
                }
            }.runTaskTimer(ThePit.getInstance(), 2L, 2L);
        } catch (Exception e) {
            isShooting.remove(player.getUniqueId());
            CC.printError(player, e);
        }
    }
}
