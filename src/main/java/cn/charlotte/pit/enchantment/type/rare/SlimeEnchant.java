package cn.charlotte.pit.enchantment.type.rare;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.param.item.ArmorOnly;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.event.PitStreakKillChangeEvent;
import cn.charlotte.pit.parm.AutoRegister;
import cn.charlotte.pit.pet.PetFactory;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.MessageType;
import cn.charlotte.pit.util.cooldown.Cooldown;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * @Author: EmptyIrony
 * @Date: 2021/3/21 18:32
 */
@ArmorOnly
@AutoRegister
public class SlimeEnchant extends AbstractEnchantment implements Listener {
    @Override
    public String getEnchantName() {
        return "天坑史莱姆";
    }

    @Override
    public int getMaxEnchantLevel() {
        return 3;
    }

    @Override
    public String getNbtName() {
        return "slime";
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
        return "&7击杀玩家召唤体积为2的史莱姆协助战斗,"
                + "/s&7每击杀 &e" + getUpdateRequire(enchantLevel) + " &7名玩家史莱姆体积+1并回满生命"
                + "/s&7(最大体积9)";
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onStreak(PitStreakKillChangeEvent event) {
        Player myself = Bukkit.getPlayer(event.getPlayerProfile().getPlayerUuid());
        if (myself == null || !myself.isOnline()) {
            return;
        }
        if (myself.getInventory().getLeggings() == null || getItemEnchantLevel(myself.getInventory().getLeggings()) <= 0) {
            return;
        }
        final PetFactory pet = ThePit.getInstance().getPetFactory();
        final PetFactory.PetData data = pet.getPetMap().get(myself.getUniqueId());
        if (data != null) {
            if (data.getEntity() instanceof Slime) {
                int enchantLevel = getItemEnchantLevel(myself.getInventory().getLeggings());
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(myself.getUniqueId());
                int streak = getUpdateRequire(enchantLevel);
                if (cn.charlotte.pit.util.reward.StreakRewardMath.crossedMilestones(event.getFrom(), event.getTo(), streak) > 0) {
                    final Slime slime = (Slime) data.getEntity();
                    if (slime.getSize() < 9) {
                        slime.setSize(slime.getSize() + 1);
                        slime.setHealth(slime.getMaxHealth());

                        data.getHologram().setText(CC.translate(profile.getFormattedName() + " &a&l" + slime.getSize()));
                        CC.send(MessageType.COMBAT, myself, "&2史莱姆升级! &7你的史莱姆成长了!");
                    }
                }
            }
        } else {
            Bukkit.getScheduler()
                    .runTask(ThePit.getInstance(), () -> pet.spawnPet("slime_pet", myself));
        }
    }

    private int getUpdateRequire(int enchant) {
        switch (enchant) {
            case 2:
                return 3;
            case 3:
                return 2;
            default:
                return 4;
        }
    }
}
