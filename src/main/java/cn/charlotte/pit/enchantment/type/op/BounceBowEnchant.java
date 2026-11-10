package cn.charlotte.pit.enchantment.type.op;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

/**
 * @Creator Misoryan
 * @Date 2021/6/19 12:50
 */
@AutoRegister
public class BounceBowEnchant extends AbstractEnchantment implements Listener {
    @Override
    public String getEnchantName() {
        return "弹跳箭矢";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "bounce_bow_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.NOSTALGIA_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7潜行射出的箭矢碰到墙壁时反弹（至多4次）,"
                + "/s&7每次反弹使该箭伤害 &c+25% &7（最多+100%）";
    }

    @EventHandler
    public void onBowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) return;
        final org.bukkit.inventory.ItemStack itemInHand = player.getItemInHand();
        if (itemInHand == null) return;
        final int level = this.getItemEnchantLevel(itemInHand);
        if (level == -1) {
            return;
        }
        if (!player.isSneaking()) return;
        if (itemInHand.getType() == Material.BOW) {
            event.getProjectile().setMetadata("bounce_bow", new FixedMetadataValue(ThePit.getInstance(), 0));
            event.getProjectile().setMetadata("bounce_velocity", new FixedMetadataValue(ThePit.getInstance(), event.getProjectile().getVelocity().clone()));
            //event.setProjectile(projectile);
        }
    }

    @EventHandler
    public void onBowHit(ProjectileHitEvent event) {
        Projectile source = event.getEntity();
        if (!source.hasMetadata("bounce_bow") || !(source.getShooter() instanceof Player)) return;
        Player player = (Player) source.getShooter();
        if (!player.isOnline() || !player.getWorld().equals(source.getWorld()) || PlayerUtil.shouldIgnoreEnchant(player)) return;
        int count = source.getMetadata("bounce_bow").get(0).asInt();
        if (count >= 4) return;
        Vector velocity = getBounceVector(source, source.getLocation().getBlock());
        if (velocity == null || velocity.lengthSquared() < 1.0E-6) return;
        org.bukkit.entity.Arrow arrow = (org.bukkit.entity.Arrow) source.getWorld().spawnEntity(
                source.getLocation().clone().add(velocity.clone().normalize().multiply(0.25)), EntityType.ARROW);
        arrow.setShooter(player);
        arrow.setVelocity(velocity);
        arrow.setFireTicks(source.getFireTicks());
        if (source instanceof org.bukkit.entity.Arrow) arrow.setCritical(((org.bukkit.entity.Arrow) source).isCritical());
        arrow.setMetadata("bounce_bow", new FixedMetadataValue(ThePit.getInstance(), count + 1));
        arrow.setMetadata("bounce_velocity", new FixedMetadataValue(ThePit.getInstance(), velocity.clone()));
        for (String key : new String[] {"enchant", "pit_shot_sneaking", "pin_down"}) {
            if (!source.getMetadata(key).isEmpty()) arrow.setMetadata(key, source.getMetadata(key).get(0));
        }
        source.remove();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onBouncedDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Arrow) || !event.getDamager().hasMetadata("bounce_bow")) return;
        Player shooter = cn.charlotte.pit.util.combat.CombatContext.playerSource(event.getDamager());
        if (shooter == null || !isEffectActive(shooter, event.getEntity() instanceof Player ? (Player) event.getEntity() : null)) return;
        int count = Math.max(0, Math.min(4, event.getDamager().getMetadata("bounce_bow").get(0).asInt()));
        event.setDamage(event.getDamage() * (1 + count * 0.25));
    }

    public Vector getBounceVector(Entity entity, Block hitBlock) {
        double min = 0.5;
        double dec = 0.6;
        Vector vector = entity.getVelocity().clone();
        if (vector.lengthSquared() < 1.0E-6 && !entity.getMetadata("bounce_velocity").isEmpty())
            vector = ((Vector) entity.getMetadata("bounce_velocity").get(0).value()).clone();
        double b1 = vector.length();
        if (b1 < min) return null;
        Location location = entity.getLocation();
        BlockIterator blockIterator = new BlockIterator(location.getWorld(), location.toVector(), vector, 0.0, 3);
        Block blockPrevious = hitBlock;
        Block block = blockIterator.next();
        while (blockIterator.hasNext() && !block.getType().isSolid()) {
            blockPrevious = block;
            block = blockIterator.next();
        }
        if (!block.getType().isSolid()) return null;
        BlockFace blockFace = block.getFace(blockPrevious);
        if (blockFace == null) return null;
        if (blockFace == BlockFace.SELF) {
            blockFace = BlockFace.UP;
        }
        Vector v1 = new Vector(blockFace.getModX(), blockFace.getModY(), blockFace.getModZ());
        Vector v2 = v1.multiply(vector.dot(v1)).multiply(2.0);
        return vector.subtract(v2).normalize().multiply(b1 * dec);
    }

}
