package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.buff.impl.PinDownDeBuff;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@BowOnly
@AutoRegister
public class PinDownEnchant extends AbstractEnchantment implements IPlayerShootEntity, Listener {

    private static final PinDownDeBuff PIN_DOWN = new PinDownDeBuff();

    @Override
    public String getEnchantName() {
        return "钉死";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "pin_down_enchant";
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
    public String getUsefulnessLore(int enchantLevel) {
        int seconds;
        switch (enchantLevel) {
            case 1:
                seconds = 3;
                break;
            case 2:
                seconds = 5;
                break;
            default:
                seconds = 10;
                break;
        }
        return "&7完全蓄力的箭矢命中后移除目标的 &b速度 &7与 &a跳跃提升 &7并阻止其在 &e" + seconds + " &7秒内再次获得";
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && this.getItemEnchantLevel(event.getBow()) > 0) {
            event.getProjectile().setMetadata("pin_down", new FixedMetadataValue(ThePit.getInstance(), event.getForce() >= 1.0F || event.getProjectile() instanceof org.bukkit.entity.Arrow && ((org.bukkit.entity.Arrow) event.getProjectile()).isCritical()));
        }
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (!cn.charlotte.pit.util.combat.CombatContext.isFullyChargedArrow() || cancel.get()) return;
        Player victim = (Player) target;
        int seconds;
        switch (enchantLevel) {
            case 1:
                seconds = 3;
                break;
            case 2:
                seconds = 5;
                break;
            default:
                seconds = 10;
                break;
        }
        victim.removePotionEffect(PotionEffectType.SPEED);
        victim.removePotionEffect(PotionEffectType.JUMP);
        PIN_DOWN.stackBuff(victim, seconds * 1000L);
        victim.setMetadata("pin_down_until", new FixedMetadataValue(ThePit.getInstance(), System.currentTimeMillis() + seconds * 1000L));
    }
}
