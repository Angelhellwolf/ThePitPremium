package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.event.PlayerOnly;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.parm.listener.IPlayerKilledEntity;
import cn.charlotte.pit.util.cooldown.Cooldown;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.metadata.FixedMetadataValue;

@ArmorOnly
@cn.charlotte.pit.parm.AutoRegister
public class WolfPackEnchant extends AbstractEnchantment implements IPlayerKilledEntity, org.bukkit.event.Listener {

    @Override
    public String getEnchantName() {
        return "狼群";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "wolf_pack_enchant";
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
        int every = enchantLevel == 1 ? 4 : 3;
        int max = enchantLevel == 1 ? 5 : (enchantLevel == 2 ? 7 : 9);
        return "&7每击杀 &e" + every + " &7名玩家召唤一只拥有 &c5❤ &7的狼"
                + "/s&7狼造成 &c1.5❤ &7普通伤害 (上限" + max + "只)"
                + "/s&7死亡、重生或离线时狼群消失";
    }

    private int getEvery(int enchantLevel) {
        return enchantLevel == 1 ? 4 : 3;
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onWolfDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Wolf) || !event.getDamager().hasMetadata("wolf_pack")
                || !(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
        Wolf wolf = (Wolf) event.getDamager();
        if (!(wolf.getOwner() instanceof Player)) return;
        Player owner = (Player) wolf.getOwner();
        Player victim = (Player) event.getEntity();
        if (owner.equals(victim) || owner.isDead() || !owner.isOnline() || !isEffectActive(owner, victim)) return;
        cn.charlotte.pit.util.combat.SecondaryDamage.reflection(owner, victim, 3.0);
    }

    private void removeWolves(Player owner) {
        for (Wolf wolf : owner.getWorld().getEntitiesByClass(Wolf.class)) {
            if (wolf.hasMetadata("wolf_pack") && owner.getUniqueId().toString().equals(wolf.getMetadata("wolf_pack").get(0).asString())) wolf.remove();
        }
    }

    @org.bukkit.event.EventHandler
    public void onSpawn(cn.charlotte.pit.event.PitPlayerSpawnEvent event) { removeWolves(event.getPlayer()); }

    @org.bukkit.event.EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) { removeWolves(event.getEntity()); }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) { removeWolves(event.getPlayer()); }

    private int getMax(int enchantLevel) {
        switch (enchantLevel) {
            case 1:
                return 5;
            case 2:
                return 7;
            default:
                return 9;
        }
    }

    @Override
    @PlayerOnly
    public void handlePlayerKilled(int enchantLevel, Player myself, Entity target, AtomicDouble coins, AtomicDouble experience) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(myself.getUniqueId());
        if (!cn.charlotte.pit.reward.KillRewardContext.crossedStreak(myself.getUniqueId(), profile.getStreakKills(), getEvery(enchantLevel))) {
            return;
        }
        long owned = myself.getWorld().getEntitiesByClass(Wolf.class).stream()
                .filter(wolf -> wolf.hasMetadata("wolf_pack") && myself.getUniqueId().toString().equals(wolf.getMetadata("wolf_pack").get(0).asString()))
                .count();
        if (owned >= getMax(enchantLevel)) {
            return;
        }
        Wolf wolf = (Wolf) myself.getWorld().spawnEntity(myself.getLocation(), EntityType.WOLF);
        wolf.setOwner(myself);
        wolf.setTamed(true);
        wolf.setMaxHealth(10);
        wolf.setHealth(10);
        wolf.setMetadata("wolf_pack", new FixedMetadataValue(ThePit.getInstance(), myself.getUniqueId().toString()));
    }
}
