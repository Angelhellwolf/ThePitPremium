package cn.charlotte.pit.npc.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.menu.stash.StashMainMenu;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import net.jitse.npclib.api.skin.Skin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StashNpc extends SkinNPC {
    @Override
    public String getNpcInternalName() {
        return "stash";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add("&6&l寄存处");
        if (!profile.getPlayerOption().isStashVisibility()) {
            lines.add("&7已在设置中隐藏");
            return lines;
        }
        if (PlayerUtil.isPlayerUnlockedPerk(player, "stash_unlock")) {
            lines.add("&e&l右键打开");
        } else {
            lines.add("&c需在精通商店解锁");
        }
        return lines;
    }

    @Override
    public Location getNpcSpawnLocation() {
        Location location = ThePit.getInstance().getPitConfig().getStashNpcLocation();
        if (location != null) {
            return location;
        }
        if (!ThePit.getInstance().getPitConfig().getSpawnLocations().isEmpty()) {
            return ThePit.getInstance().getPitConfig().getSpawnLocations().get(0);
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    @Override
    public Skin getNpcSkin() {
        return new Skin(
                "ewogICJ0aW1lc3RhbXAiIDogMTY3MjQyNzc0MDA2OCwKICAicHJvZmlsZUlkIiA6ICJmMjAzYmM4OWQ4NzM0N2EyYWQ5NjllYjQ4YmIyY2QwMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJWYXVsdEtlZXBlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hNDBjMWQ5N2Q4NzUyM2JhYWFmZjcwNTgzNmRkYjEzZGU5M2M5N2YzNDRhZWY2MjQ3N2E4NDE4OTJmN2Y2ZmY2IgogICAgfQogIH0KfQ==",
                null
        );
    }

    @Override
    public void handlePlayerInteract(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.getPlayerOption().isStashVisibility()) {
            player.sendMessage(CC.translate("&c你在设置中隐藏了寄存处, 请使用 /settings 重新开启."));
            return;
        }
        if (!PlayerUtil.isPlayerUnlockedPerk(player, "stash_unlock")) {
            player.sendMessage(CC.translate("&c你还未解锁寄存处! 请在精通天赋中购买 &e寄存处 &c(10声望)."));
            return;
        }
        new StashMainMenu().openMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return new ItemStack(Material.CHEST);
    }
}
