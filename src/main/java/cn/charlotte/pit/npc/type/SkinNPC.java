package cn.charlotte.pit.npc.type;

import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.npc.AbstractPitNPC;
import net.jitse.npclib.api.NPC;
import net.jitse.npclib.api.skin.Skin;

public abstract class SkinNPC extends AbstractPitNPC {

    @Override
    public void initSkin(NPC npc) {
        final String skinValue = NewConfiguration.INSTANCE.getConfig().getString(this.getNpcInternalName() + "-npc-skin");
        if (skinValue != null) {
            String signature = NewConfiguration.INSTANCE
                    .getConfig()
                    .getString("not-netease-skins." + getNpcInternalName() + "-signature");
            if (signature != null && !signature.isEmpty()) {
                // Config has both value and signature — use config skin
                npc.setSkin(new Skin(skinValue, signature));
                return;
            }
        }
        // Fall back to hardcoded skin (which has a valid signature)
        // to avoid null signature causing PacketEvents validation errors
        npc.setSkin(getNpcSkin());
    }
}
