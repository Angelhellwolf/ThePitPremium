package cn.charlotte.pit.enchantment.type.dark_normal;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.event.PitRegainHealthEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IPlayerBeKilledByEntity;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.metadata.FixedMetadataValue;

@ArmorOnly
@AutoRegister
public class NeedlessSufferingEnchant extends AbstractEnchantment implements IPlayerRespawn, IPlayerBeKilledByEntity, Listener {

    @Override
    public String getEnchantName() {
        return "无谓的苦难";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "needless_suffering_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7复活时获得 &6+30❤ 生命吸收 &7,无法以任何方式恢复生命值"
                + "/s&7穿着此裤子被其他玩家击杀一次后才会获得生命吸收.";
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        if (!myself.hasMetadata("needless_suffering_ready")) {
            return;
        }
        ((CraftPlayer) myself).getHandle().setAbsorptionHearts(60.0F);
    }

    @Override
    public void handlePlayerBeKilledByEntity(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        if (target instanceof Player) {
            myself.setMetadata("needless_suffering_ready", new FixedMetadataValue(ThePit.getInstance(), true));
        }
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (getItemEnchantLevel(player.getInventory().getLeggings()) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPitHeal(PitRegainHealthEvent event) {
        if (getItemEnchantLevel(event.getPlayer().getInventory().getLeggings()) > 0) {
            event.setCancelled(true);
        }
    }
}
