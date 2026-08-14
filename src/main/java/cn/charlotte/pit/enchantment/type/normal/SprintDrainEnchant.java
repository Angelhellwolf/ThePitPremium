package cn.charlotte.pit.enchantment.type.normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.BowOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerShootEntity;
import cn.charlotte.pit.UtilKt;
import cn.charlotte.pit.util.chat.RomanUtil;
import cn.charlotte.pit.util.cooldown.Cooldown;
import cn.charlotte.pit.util.time.TimeUtil;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.ItemStack;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/2/8 19:47
 */
@BowOnly
public class SprintDrainEnchant extends AbstractEnchantment implements IPlayerShootEntity {
    @Override
    public String getEnchantName() {
        return "灵魂汲取";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "sprint_drain_enchant";
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
        return getLegacyEnchantLevel(item, "sprint_drain");
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7弓箭命中玩家时你获得 &b速度 " + RomanUtil.convert(enchantLevel >= 3 ? 2 : 1) + " &f(" + TimeUtil.millisToTimer(1000 * (enchantLevel * 2L + 1)) + ")"
                + (enchantLevel >= 2 ?
                "/s&7且箭矢命中后对敌人施加 &c缓慢 I &f(00:03)" : "");
    }

    @Override
    @PlayerOnly
    public void handleShootEntity(int enchantLevel, Player attacker, Entity target, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        attacker.removePotionEffect(PotionEffectType.SPEED);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * (enchantLevel * 2 + 1), enchantLevel >= 3 ? 1 : 0), true);
        if (enchantLevel >= 2) {
            Player targetPlayer = (Player) target;
            targetPlayer.removePotionEffect(PotionEffectType.SLOW);
            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 3, 0, true));
        }
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
