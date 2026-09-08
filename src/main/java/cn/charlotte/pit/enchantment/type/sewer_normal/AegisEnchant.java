package cn.charlotte.pit.enchantment.type.sewer_normal;

import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.IActionDisplayEnchant;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.ITickTask;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author: Misoryan
 * @Created_In: 2021/3/16 21:43
 */
@ArmorOnly
public class AegisEnchant extends AbstractEnchantment implements IPlayerDamaged, ITickTask, IActionDisplayEnchant {

    private final Map<UUID, Integer> shield = new HashMap<>();
    private final Map<UUID, Cooldown> cooldown = new HashMap<>();

    @Override
    public String getEnchantName() {
        return "宙斯之盾";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 1;
    }

    @Override
    public String getNbtName() {
        return "aegis_enchant";
    }

    @Override
    public EnchantmentRarity getRarity() {
        return EnchantmentRarity.SEWER_NORMAL;
    }

    @Override
    public Cooldown getCooldown() {
        return null;
    }

    @Override
    public String getUsefulnessLore(int enchantLevel) {
        return "&7每 &e9 &7秒获得一层护盾 (可以抵消1次玩家伤害) (最高1层)";
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (shield.getOrDefault(myself.getUniqueId(), 0) > 0) {
            shield.put(myself.getUniqueId(), 0);
            cancel.set(true);
            myself.sendMessage(CC.translate("&c你的护盾因受到攻击而破裂!"));
            if (attacker instanceof Player) {
                ((Player) attacker).playSound(attacker.getLocation(), Sound.ANVIL_LAND, 1, 1);
            }
            myself.playSound(myself.getLocation(), Sound.ANVIL_LAND, 1, 1);
        }
    }

    @Override
    public void handle(int enchantLevel, Player player) {
        if (PlayerUtil.isVenom(player) || PlayerUtil.isEquippingSomber(player)) {
            return;
        }
        shield.putIfAbsent(player.getUniqueId(), 0);
        cooldown.putIfAbsent(player.getUniqueId(), new Cooldown(9, TimeUnit.SECONDS));
        if (shield.get(player.getUniqueId()) < 1 && cooldown.get(player.getUniqueId()).hasExpired()) {
            cooldown.put(player.getUniqueId(), new Cooldown(9, TimeUnit.SECONDS));
            shield.put(player.getUniqueId(), 1);
        }
    }

    @Override
    public int loopTick(int enchantLevel) {
        return 20;
    }

    @Override
    public String getText(int level, Player player) {
        return shield.getOrDefault(player.getUniqueId(), 0) > 0 ? "&a&l护盾" : "&c&l无";
    }
}
