package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.bot.BotSkins;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.npc.type.PerkNPC;
import cn.charlotte.pit.npc.type.PrestigeNPC;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.level.LevelUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.*;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.*;

public final class JunxuNpcs implements Listener {
    private static final String REGISTRY="thepit-junxu-services",KEY="thepit-junxu-service";
    private final JunxuManager manager;
    private final NPCRegistry registry;
    private final List<BotSkins> skins;
    private final Map<UUID,Binding> bindings=new HashMap<>();
    private NPC entrance;

    public JunxuNpcs(JunxuManager manager) throws IOException {
        this.manager=manager;
        skins=BotSkins.load(manager.plugin().getResource("public-player-skins.properties"));
        List<NPC> stale=new ArrayList<>();
        for(NPCRegistry candidate:CitizensAPI.getNPCRegistries())for(NPC npc:candidate)if(npc.data().has(KEY))stale.add(npc);
        for(NPC npc:stale)npc.destroy();
        NPCRegistry old=CitizensAPI.getNamedNPCRegistry(REGISTRY);
        if(old!=null){old.deregisterAll();CitizensAPI.removeNamedNPCRegistry(REGISTRY);}
        registry=CitizensAPI.createNamedNPCRegistry(REGISTRY,new MemoryNPCDataStore());
        Bukkit.getPluginManager().registerEvents(this,manager.plugin());
    }
    public void entrance(Location location) {
        if(entrance!=null){bindings.remove(entrance.getUniqueId());entrance.destroy();}
        entrance=create("§6军需官·洛恩",location,null,Role.ENTRANCE,0,Material.GOLD_HELMET,Material.BOOK);
    }
    public void open(JunxuRoom room) {
        create("§d符文师·伊瑟",room.at(18.5,23,18.5,270),room,Role.ENCHANT,1,Material.LEATHER_HELMET,Material.ENCHANTED_BOOK);
        create("§b战术导师·凯恩",room.at(42.5,23,18.5,90),room,Role.TALENT,2,Material.CHAINMAIL_HELMET,Material.MAP);
        create("§6兵器大师·雷铎",room.at(24.5,23,30.5,270),room,Role.MASTERY,0,Material.IRON_HELMET,Material.IRON_SWORD);
    }
    private NPC create(String name,Location location,JunxuRoom room,Role role,int skin,Material helmet,Material hand) {
        location.getChunk().load();
        NPC npc=registry.createNPC(EntityType.PLAYER,name);
        npc.data().setPersistent(KEY,role.name());npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE,false);
        npc.data().setPersistent(NPC.Metadata.RESPAWN_DELAY,-1);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_PLAYERLIST,true);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST,true);
        npc.setProtected(true);npc.setUseMinecraftAI(false);npc.setAlwaysUseNameHologram(true);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE,false);
        npc.data().setPersistent(NPC.Metadata.DAMAGE_OTHERS,false);
        skins.get(skin%skins.size()).apply(npc);
        if(!npc.spawn(location)){npc.destroy();throw new IllegalStateException("Unable to spawn Junxu service "+role);}
        Player player=(Player)npc.getEntity();
        player.getInventory().setHelmet(new ItemStack(helmet));player.getInventory().setItemInHand(new ItemStack(hand));
        player.getInventory().setChestplate(new ItemStack(role==Role.ENCHANT?Material.LEATHER_CHESTPLATE:Material.IRON_CHESTPLATE));
        NMS.removeFromServerPlayerList(player);
        bindings.put(npc.getUniqueId(),new Binding(npc,room,role,location.clone()));
        return npc;
    }
    public boolean isService(Entity entity) { return entity!=null && bindings.containsKey(entity.getUniqueId()); }
    public void close(JunxuRoom room) {
        for(Binding binding:new ArrayList<>(bindings.values()))if(binding.room==room){bindings.remove(binding.npc.getUniqueId());binding.npc.destroy();}
    }
    public int count(JunxuRoom room) { int count=0;for(Binding b:bindings.values())if(b.room==room&&b.npc.isSpawned())count++;return count; }
    public int entranceCount(){return entrance!=null&&entrance.isSpawned()?1:0;}
    public void tick() {
        for(Binding b:bindings.values())if(!b.npc.isSpawned() && (b.room==null || b.room.open)){
            b.location.getChunk().load();b.npc.spawn(b.location);
            if(b.npc.getEntity() instanceof Player)NMS.removeFromServerPlayerList((Player)b.npc.getEntity());
        }
        // Citizens uses a separate CIT-* GameProfile name behind its hologram.
        // Hide that actual profile entry, not the NPC's localized display name.
        List<Player> actors = new ArrayList<>(manager.plugin().getPitBotManager().getActivePlayers());
        for (Binding binding : bindings.values())
            if (binding.npc.getEntity() instanceof Player) actors.add((Player) binding.npc.getEntity());
        for (Player actor : actors) {
            String profileName = ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) actor).getHandle().getProfile().getName();
            String teamName = "jxh_" + actor.getUniqueId().toString().replace("-", "").substring(0, 12);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.getWorld().equals(actor.getWorld())) continue;
                org.bukkit.scoreboard.Scoreboard board = viewer.getScoreboard();
                org.bukkit.scoreboard.Team team = board.getTeam(teamName);
                if (team == null) team = board.registerNewTeam(teamName);
                if (team.getNameTagVisibility() != org.bukkit.scoreboard.NameTagVisibility.NEVER)
                    team.setNameTagVisibility(org.bukkit.scoreboard.NameTagVisibility.NEVER);
                if (!team.hasEntry(profileName)) team.addEntry(profileName);
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(NPCRightClickEvent event) {
        Binding binding=bindings.get(event.getNPC().getUniqueId());if(binding==null)return;
        event.setCancelled(true);Player player=event.getClicker();
        if(player.hasMetadata("NPC"))return;
        if(binding.role==Role.ENTRANCE){manager.menu(player);return;}
        if(!manager.authorized(player,binding.room) || !binding.room.contains(player.getLocation()))return;
        if(!PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).isLoaded()){manager.tell(player,"&e资料正在载入。");return;}
        if(binding.role==Role.ENCHANT){manager.tell(player,"&d伊瑟：&f刃口会钝，刻进兵器的符文不会忘记它的使命。");openEnchant(player);}
        if(binding.role==Role.TALENT){manager.tell(player,"&b凯恩：&f先选好你要走的路，再决定如何赢下下一场。");new PerkNPC().handlePlayerInteract(player);}
        if(binding.role==Role.MASTERY){manager.tell(player,"&6雷铎：&f兵器认得握住它的人。去演武场，让我看看你的本事。");new PrestigeNPC().handlePlayerInteract(player);}
    }
    /** Matches the existing public enchantment-table prerequisite, then invokes its API. */
    public static void openEnchant(Player player) {
        PlayerProfile profile=PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if(!profile.getUnlockedPerkMap().containsKey("Mythicism")){
            player.sendMessage(CC.translate("&c你需要达到 "+LevelUtil.getLevelTag(0,120)+" &c并解锁精通天赋 &6神话附魔师 &c以使用神话之井！"));return;
        }
        ThePit.getApi().openMythicWellMenu(player);
        player.playSound(player.getLocation(),Sound.CHICKEN_EGG_POP,1,0.9F);
    }
    public void shutdown(){bindings.clear();registry.deregisterAll();CitizensAPI.removeNamedNPCRegistry(REGISTRY);}
    enum Role { ENTRANCE,ENCHANT,TALENT,MASTERY }
    private static final class Binding {
        final NPC npc;final JunxuRoom room;final Role role;final Location location;
        Binding(NPC npc,JunxuRoom room,Role role,Location location){this.npc=npc;this.room=room;this.role=role;this.location=location;}
    }
}
