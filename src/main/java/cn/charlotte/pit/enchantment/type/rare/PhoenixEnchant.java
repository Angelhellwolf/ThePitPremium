package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.parm.listener.IAttackEntity;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.item.IMythicItem;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
@AutoRegister
public class PhoenixEnchant extends AbstractEnchantment implements Listener, IPlayerRespawn, IAttackEntity, IPlayerShootEntity {

    @Override
    public String getEnchantName() {
        return "凤凰";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "phoenix_enchant";
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
        String extra = "";
        if (enchantLevel >= 2) {
            extra = "/s&7并在12秒内造成的伤害 &c+" + (enchantLevel >= 3 ? 30 : 10) + "%";
        }
        return "&7本应死亡时消耗此神话之甲 &c1 &7点生命并恢复全部生命值" + extra
                + "/s&7每次生命仅能触发一次.";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        ItemStack leggings = player.getInventory().getLeggings();
        int level = getItemEnchantLevel(leggings);
        if (level <= 0) {
            return;
        }
        if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) {
            return;
        }
        if (player.hasMetadata("phoenix_used")) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        IMythicItem item = MythicUtil.getMythicItem(leggings);
        if (item == null || item.getLive() <= 1) {
            return;
        }
        event.setCancelled(true);
        item.setLive(item.getLive() - 1);
        player.getInventory().setLeggings(item.toItemStack());
        player.setHealth(player.getMaxHealth());
        player.setMetadata("phoenix_used", new FixedMetadataValue(ThePit.getInstance(), System.currentTimeMillis() + 12000L));
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        myself.removeMetadata("phoenix_used", ThePit.getInstance());
    }

    private boolean activeBoost(Player player, int enchantLevel) {
        if (enchantLevel < 2 || !player.hasMetadata("phoenix_used")) {
            return false;
        }
        long until = player.getMetadata("phoenix_used").get(0).asLong();
        return until > System.currentTimeMillis();
    }

    @Override
    public void handleAttackEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (activeBoost(attacker, enchantLevel)) {
            boostDamage.getAndAdd(enchantLevel >= 3 ? 0.30 : 0.10);
        }
    }

    @Override
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (activeBoost(attacker, enchantLevel)) {
            boostDamage.getAndAdd(enchantLevel >= 3 ? 0.30 : 0.10);
        }
    }
}
