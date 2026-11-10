package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerDamaged;
import cn.charlotte.pit.parm.listener.IPlayerRespawn;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.BotEnchantPolicy;
import cn.charlotte.pit.util.SpiterCompatibility;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicBoolean;

@ArmorOnly
public class EscapePodEnchant extends AbstractEnchantment implements IPlayerDamaged, IPlayerRespawn {

    private static final String USED_KEY = "escape_pod_used";
    private static final String PENDING_KEY = "escape_pod_pending";

    @Override
    public String getEnchantName() {
        return "逃生舱";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "escape_pod_enchant";
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
        int regenSeconds = getRegenSeconds(enchantLevel);
        return "&7生命值降至 &c2❤ &7及以下时将你发射至空中,"
                + "/s&7对附近玩家造成 &c" + enchantLevel + "❤ &7的&f真实&7伤害"
                + "/s&7并获得 &c生命恢复 IV &f(" + formatSeconds(regenSeconds) + ")"
                + "/s&7每次生命仅能触发一次.";
    }

    @Override
    @PlayerOnly
    public void handlePlayerDamaged(int enchantLevel, Player myself, Entity attacker, double damage, AtomicDouble finalDamage, AtomicDouble boostDamage, AtomicBoolean cancel) {
        if (myself.hasMetadata(USED_KEY) || myself.hasMetadata(PENDING_KEY)) {
            return;
        }
        myself.setMetadata(PENDING_KEY, new FixedMetadataValue(ThePit.getInstance(), enchantLevel));
    }

    @Override
    public void handleRespawn(int enchantLevel, Player myself) {
        myself.removeMetadata(USED_KEY, ThePit.getInstance());
        myself.removeMetadata(PENDING_KEY, ThePit.getInstance());
    }

    public static void triggerIfNeeded(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!player.hasMetadata(PENDING_KEY)) {
            return;
        }
        int enchantLevel = player.getMetadata(PENDING_KEY).get(0).asInt();
        player.removeMetadata(PENDING_KEY, ThePit.getInstance());
        if (player.hasMetadata(USED_KEY) || enchantLevel <= 0 || player.getHealth() > 4.0D) {
            return;
        }

        player.setMetadata(USED_KEY, new FixedMetadataValue(ThePit.getInstance(), true));
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getRegenSeconds(enchantLevel) * 20, 3), true);

        double trueDamage = enchantLevel * 2.0D;
        for (Player nearby : PlayerUtil.getNearbyPlayers(player.getLocation(), 4.0D)) {
            if (nearby.getUniqueId().equals(player.getUniqueId())
                    || !BotEnchantPolicy.effectAllowed(player, nearby)
                    || PlayerUtil.shouldIgnoreEnchant(player, nearby)) {
                continue;
            }
            PlayerUtil.damage(player, nearby, PlayerUtil.DamageType.TRUE, trueDamage, true);
        }

        Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            SpiterCompatibility.exemptVelocity(player, 40L);
            player.setVelocity(new Vector(0, 1.8, 0));
        });
    }

    private static int getRegenSeconds(int enchantLevel) {
        if (enchantLevel <= 1) {
            return 15;
        }
        if (enchantLevel == 2) {
            return 20;
        }
        return 30;
    }

    private static String formatSeconds(int seconds) {
        return String.format("00:%02d", seconds);
    }
}
