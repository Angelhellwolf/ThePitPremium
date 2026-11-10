package cn.charlotte.pit.npc.type;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.map.kingsquests.ui.KingQuestsUI;
import cn.charlotte.pit.util.chat.CC;
import net.jitse.npclib.api.NPC;
import net.jitse.npclib.api.skin.Skin;
import net.jitse.npclib.api.state.NPCSlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class KingNpc extends SkinNPC {
    @Override
    public String getNpcInternalName() {
        return "king";
    }

    @Override
    public List<String> getNpcDisplayName(Player player) {
        return Arrays.asList("&6&l国王", "&e右键接受或提交国王任务");
    }

    @Override
    public Location getNpcSpawnLocation() {
        return ThePit.getInstance().getPitConfig().getKingNpcLocation();
    }

    @Override
    public Skin getNpcSkin() {
        // Use an existing signed skin by default; SkinNPC supports a king-specific override.
        return new BakeMasterNpc().getNpcSkin();
    }

    @Override
    public void initSkin(NPC npc) {
        super.initSkin(npc);
        npc.setItem(NPCSlot.HELMET, new ItemStack(Material.GOLD_HELMET));
        npc.setItem(NPCSlot.CHESTPLATE, new ItemStack(Material.GOLD_CHESTPLATE));
    }

    @Override
    public void handlePlayerInteract(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded() || profile.isBot()) {
            player.sendMessage(CC.translate("&c玩家数据尚未就绪，请稍后再试。"));
            return;
        }
        KingQuestsUI.INSTANCE.openMenu(player);
    }

    @Override
    public ItemStack getNpcHeldItem() {
        return new ItemStack(Material.GOLD_SWORD);
    }
}
