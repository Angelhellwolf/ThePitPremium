package cn.charlotte.pit.enchantment.type.dark_rare;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class NostalgiaEnchant extends AbstractEnchantment implements IPlayerDamaged, ITickTask {

    @Override
    public String getEnchantName() {
        return "怀旧";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "nostalgia_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.DARK_RARE;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7受到的伤害 &9-10% &7,身上的钻石装备会发出附魔光泽";
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        boostDamage.getAndAdd(-0.10);
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        glowDiamond(player.getInventory().getHelmet());
        glowDiamond(player.getInventory().getChestplate());
        glowDiamond(player.getInventory().getBoots());
    }

    private void glowDiamond(ItemStack item) {
        if (item == null || !item.getType().name().contains("DIAMOND")) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasEnchant(Enchantment.DURABILITY)) {
            return;
        }
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 40;
    }
}
