package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.junxu.JunxuManager;
import cn.charlotte.pit.junxu.JunxuRoom;
import cn.charlotte.pit.junxu.JunxuEntitlements;
import cn.charlotte.pit.junxu.JunxuTrainingSettings;
import cn.charlotte.pit.listener.CombatListener;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.*;
import net.citizensnpcs.api.npc.*;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.util.NMS;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ten private projections per open sacred room. Public movement/attack and Pit settlement are reused. */
public final class JunxuTrainingBots implements Listener {
    public static final int LIMIT=10;
    private static final String REGISTRY="thepit-junxu-training",KEY="thepit-junxu-projection";
    private final JunxuManager manager;
    private final ThePit plugin;
    private final NPCRegistry registry;
    private final List<BotSkins> skins;
    private final Map<UUID,Squad> squads=new HashMap<>();
    private final Map<UUID,Squad> owners=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,PublicPlayerState> states=new HashMap<>();
    private final BukkitTask task;
    private final int respawnTicks;
    private long tick;

    public JunxuTrainingBots(JunxuManager manager,int respawnTicks) throws IOException {
        this.manager=manager;this.plugin=manager.plugin();this.respawnTicks=Math.max(20,respawnTicks);
        skins=BotSkins.load(plugin.getResource("public-player-skins.properties"));
        List<NPC> stale=new ArrayList<>();
        for(NPCRegistry candidate:CitizensAPI.getNPCRegistries())for(NPC npc:candidate)if(npc.data().has(KEY))stale.add(npc);
        for(NPC npc:stale)removeNpc(npc);
        NPCRegistry old=CitizensAPI.getNamedNPCRegistry(REGISTRY);
        if(old!=null){old.deregisterAll();CitizensAPI.removeNamedNPCRegistry(REGISTRY);}
        registry=CitizensAPI.createNamedNPCRegistry(REGISTRY,new MemoryNPCDataStore());
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public void open(JunxuRoom room) {
        if(squads.containsKey(room.owner))return;
        Squad squad=new Squad(room,manager.trainingSettings(room.owner));squads.put(room.owner,squad);
        for(int i=0;i<JunxuEntitlements.bots(room.tier);i++){
            UUID id=UUID.nameUUIDFromBytes(("thepit:junxu:projection:"+room.owner+":"+i).getBytes(StandardCharsets.UTF_8));
            String name="JX"+Integer.toString(room.slot,36)+"_Spirit"+(i+1);
            PublicPlayerState state=new PublicPlayerState(new PublicPlayerIdentityStore.Identity(i,id,name,name));
            state.profile=new PlayerProfile(id,name);state.profile.setBot(true);state.profile.setLoaded(true);
            BotProfileSetup.initializeMedals(plugin,state.profile);state.profile.init();
            state.dueTick=tick+i*2L;squad.members.add(state);states.put(id,state);owners.put(id,squad);
        }
    }
    public void close(JunxuRoom room) {
        Squad squad=squads.remove(room.owner);if(squad==null)return;
        for(PublicPlayerState state:squad.members){
            retire(state);states.remove(state.identity.uuid);owners.remove(state.identity.uuid);
            PlayerProfile.getCacheProfile().remove(state.identity.uuid,state.profile);
            if(CombatListener.INSTANCE!=null)CombatListener.INSTANCE.forgetCombatState(state.identity.uuid);
        }
    }
    public boolean isBot(Entity entity){return entity!=null&&states.containsKey(entity.getUniqueId());}
    public boolean ownsProfile(PlayerProfile profile){PublicPlayerState s=profile==null?null:states.get(profile.getPlayerUuid());return s!=null&&s.profile==profile;}
    public Player getPlayer(UUID id){PublicPlayerState s=states.get(id);return s==null?null:s.entity;}
    public JunxuRoom room(Entity entity){Squad s=entity==null?null:owners.get(entity.getUniqueId());return s==null?null:s.room;}
    public JunxuRoom room(UUID id){Squad squad=owners.get(id);return squad==null?null:squad.room;}
    public List<Player> activePlayers(){List<Player> result=new ArrayList<>();for(PublicPlayerState s:states.values())if(s.phase!=PublicPlayerState.Phase.RESPAWNING&&s.entity!=null&&s.entity.isValid())result.add(s.entity);return result;}
    public int count(JunxuRoom room){Squad squad=squads.get(room.owner);if(squad==null)return 0;int count=0;for(PublicPlayerState s:squad.members)if(s.npc!=null&&s.npc.isSpawned())count++;return count;}
    public void assemble(JunxuRoom room,int ticks){Squad s=squads.get(room.owner);if(s!=null)s.formationUntil=tick+ticks;}
    public void configure(JunxuRoom room,JunxuTrainingSettings settings){
        Squad squad=squads.get(room.owner);if(squad==null)return;squad.settings=settings;
        for(PublicPlayerState state:squad.members){state.target=null;PublicPlayerCombat.cancel(state);if(state.entity!=null&&state.npc!=null&&state.npc.isSpawned())applySettings(state,settings);}
    }
    private void tick(){
        tick++;
        for(Squad squad:new ArrayList<>(squads.values())){
            if(!squad.room.open){close(squad.room);continue;}
            for(PublicPlayerState state:squad.members)try{tick(squad,state);}catch(RuntimeException error){
                plugin.getLogger().log(java.util.logging.Level.WARNING,"恢复军需处战魂 slot="+squad.room.slot,error);
                retire(state);state.phase=PublicPlayerState.Phase.RESPAWNING;state.dueTick=tick+40;
            }
        }
    }
    private void tick(Squad squad,PublicPlayerState state){
        if(state.phase==PublicPlayerState.Phase.RESPAWNING){if(tick>=state.dueTick)spawn(squad,state);return;}
        if(state.entity==null||state.entity.isDead()||state.entity.getHealth()<=0){handleDeath(state.entity,state.entity==null?null:state.entity.getKiller());return;}
        Location here=state.entity.getLocation();
        if(!squad.room.arena(here)){
            PublicPlayerCombat.cancel(state);state.entity.teleport(squad.spawn(state.identity.slot));state.entity.setFallDistance(0);state.target=null;return;
        }
        state.profile.setInArena(true);
        if(squad.settings.paused){state.target=null;PublicPlayerCombat.cancel(state);if(!PublicPlayerCombat.recoveringKnockback(state,tick))state.entity.setVelocity(new org.bukkit.util.Vector());return;}
        if(tick<squad.formationUntil){
            if(PublicPlayerCombat.recoveringKnockback(state,tick)){PublicPlayerCombat.cancel(state);return;}
            state.target=null;Location place=squad.spawn(state.identity.slot);
            if(here.distanceSquared(place)>0.6)squad.combat.navigate(state,place,tick);else{PublicPlayerCombat.cancel(state);NMS.look(state.entity,180,0);}
            return;
        }
        if(squad.settings.route==JunxuTrainingSettings.Route.FOLLOW){follow(squad,state);return;}
        Player target=state.target==null?null:Bukkit.getPlayer(state.target);
        if(squad.settings.mode==JunxuTrainingSettings.Mode.PASSIVE){state.target=null;target=null;}
        if(!squad.combat.targetable(state.entity,target)||!manager.authorized(target,squad.room)){state.target=null;target=null;}
        if(target==null&&squad.settings.mode==JunxuTrainingSettings.Mode.HOSTILE&&tick>=state.nextTargetTick){
            state.nextTargetTick=tick+10+state.identity.slot;
            target=squad.combat.findTarget(state,manager.occupants(squad.room));
            if(target!=null)state.target=target.getUniqueId();
        }
        if(target!=null){long before=state.nextAttackTick;squad.combat.fight(state,target,tick);
            if(state.nextAttackTick>before&&before<=tick)state.nextAttackTick=tick+squad.settings.strength.attackTicks;
        }
        else{
            // Two five-person groups follow the same rectangular route with fixed spacing.
            int group=state.identity.slot/5,position=state.identity.slot%5;
            double[][] route={{21.5,38.5},{39.5,38.5},{39.5,52.5},{21.5,52.5}};
            int corner=(int)((tick/160+group*2+position/2)%4);
            Location goal=squad.room.at(route[corner][0]+(position%2)*0.8,23,route[corner][1]+group*0.4,0);
            if(squad.settings.route==JunxuTrainingSettings.Route.STATIONARY)goal=squad.spawn(state.identity.slot);
            else if(squad.settings.route==JunxuTrainingSettings.Route.RING){
                double angle=tick*0.0035+state.identity.slot*Math.PI*2/squad.members.size();
                goal=squad.room.at(30.5+Math.cos(angle)*9,23,45.5+Math.sin(angle)*7,0);
            }else if(squad.settings.route==JunxuTrainingSettings.Route.CROSS){
                double[][] cross={{21.5,38.5},{39.5,52.5},{39.5,38.5},{21.5,52.5}};
                int leg=(int)((tick/180+state.identity.slot/3)%4);goal=squad.room.at(cross[leg][0],23,cross[leg][1],0);
            }
            if(!PublicPlayerCombat.recoveringKnockback(state,tick))squad.combat.navigate(state,goal,tick);else PublicPlayerCombat.cancel(state);
        }
    }
    private void follow(Squad squad,PublicPlayerState state){
        Player bot=state.entity,chosen=null;double distance=Double.MAX_VALUE;
        for(Player player:manager.occupants(squad.room)){
            if(!manager.authorized(player,squad.room)||player.isDead()||player.getGameMode()==GameMode.SPECTATOR)continue;
            double here=bot.getLocation().distanceSquared(player.getLocation());
            if(squad.room.owner.equals(player.getUniqueId())){chosen=player;break;}
            if(here<distance){chosen=player;distance=here;}
        }
        if(chosen==null){state.target=null;PublicPlayerCombat.cancel(state);return;}
        boolean attacks=squad.settings.mode==JunxuTrainingSettings.Mode.HOSTILE
                ||squad.settings.mode==JunxuTrainingSettings.Mode.RETALIATE&&chosen.getUniqueId().equals(state.target);
        if(attacks&&squad.combat.targetable(bot,chosen)){
            state.target=chosen.getUniqueId();long before=state.nextAttackTick;squad.combat.fight(state,chosen,tick);
            if(state.nextAttackTick>before&&before<=tick)state.nextAttackTick=tick+squad.settings.strength.attackTicks;
        }else if(squad.settings.mode==JunxuTrainingSettings.Mode.PASSIVE)state.target=null;
        if(PublicPlayerCombat.recoveringKnockback(state,tick)){PublicPlayerCombat.cancel(state);return;}
        Location target=chosen.getLocation();Location goal=followDestination(squad.room,target);
        Location here=bot.getLocation();PublicPlayerCombat.look(bot,chosen.getEyeLocation(),18);
        if(here.distanceSquared(goal)>9||!bot.hasLineOfSight(chosen))squad.combat.navigate(state,goal,tick);
        else{
            PublicPlayerCombat.cancel(state);org.bukkit.util.Vector toward=goal.toVector().subtract(here.toVector()).setY(0);
            if(toward.lengthSquared()>0.09)squad.combat.move(bot,toward.normalize(),true,false);
            else{org.bukkit.util.Vector velocity=bot.getVelocity();velocity.setX(0);velocity.setZ(0);bot.setVelocity(velocity);}
        }
    }
    static Location followDestination(JunxuRoom room,Location target){
        return room.at(Math.max(17.6,Math.min(43.4,target.getX()-JunxuRoom.originX(room.slot))),23,
                Math.max(34.6,Math.min(56.4,target.getZ()-JunxuRoom.originZ(room.slot))),0);
    }
    private void spawn(Squad squad,PublicPlayerState state){
        if(!squad.room.open||count(squad.room)>=JunxuEntitlements.bots(squad.room.tier)){state.dueTick=tick+20;return;}
        Player conflict=Bukkit.getPlayerExact(state.identity.name);
        if(conflict!=null&&!conflict.hasMetadata("NPC")){state.dueTick=tick+100;return;}
        retire(state);
        NPC npc=registry.createNPC(EntityType.PLAYER,state.identity.uuid,squad.room.slot*LIMIT+state.identity.slot,state.identity.name);
        state.npc=npc;
        npc.data().setPersistent(KEY,squad.room.owner.toString());
        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE,false);npc.data().setPersistent(NPC.Metadata.RESPAWN_DELAY,-1);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_PLAYERLIST,false);npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST,true);
        npc.setProtected(false);npc.setUseMinecraftAI(false);npc.setFlyable(false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED,false);npc.data().setPersistent(NPC.Metadata.TARGETABLE,true);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE,true);npc.data().setPersistent(NPC.Metadata.SPAWN_NODAMAGE_TICKS,0);
        npc.data().setPersistent(NPC.Metadata.DAMAGE_OTHERS,true);npc.data().set("damage_others",true);
        npc.setAlwaysUseNameHologram(true);
        npc.setName("§b战魂投影·"+(state.identity.slot+1));
        skins.get(state.identity.slot%skins.size()).apply(npc);
        Location spawn=squad.spawn(state.identity.slot);spawn.getChunk().load();
        if(!npc.spawn(spawn)||!(npc.getEntity() instanceof Player)){retire(state);state.dueTick=tick+40;return;}
        Player player=(Player)npc.getEntity();state.entity=player;
        // The entity's UUID remains stable across deaths. Its profile is never persisted as a human account.
        if(!state.identity.uuid.equals(player.getUniqueId()))throw new IllegalStateException("Projection identity changed");
        if(CombatListener.INSTANCE!=null)CombatListener.INSTANCE.forgetCombatState(state.identity.uuid);
        state.profile.setBot(true);state.profile.setLoaded(true);state.profile.setInArena(true);state.profile.init();
        player.setGameMode(GameMode.SURVIVAL);player.getInventory().clear();player.getInventory().setArmorContents(new ItemStack[4]);
        player.setMaxHealth(state.profile.getMaxHealth());player.setHealth(state.profile.getMaxHealth());
        player.setWalkSpeed(state.profile.getMoveSpeed());InventoryUtil.supplyItems(player);
        player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        player.getInventory().setHeldItemSlot(0);player.updateInventory();
        applySettings(state,squad.settings);
        player.setNoDamageTicks(0);((CraftPlayer)player).getHandle().invulnerableTicks=0;
        ((CraftPlayer)player).getHandle().abilities.isInvulnerable=false;
        NMS.removeFromServerPlayerList(player);
        state.born(npc,player,tick,0);state.phase=PublicPlayerState.Phase.COMBAT;
        manager.refreshVisibility(null);
    }
    private void applySettings(PublicPlayerState state,JunxuTrainingSettings value){
        Player player=state.entity;
        state.profile.getExtraMaxHealth().put("junxu_training",value.health-20);
        state.profile.setMoveSpeed(value.strength.speed);player.setWalkSpeed(value.strength.speed);
        player.setMaxHealth(value.health);player.setHealth(value.health);
        if(value.armor==JunxuTrainingSettings.Armor.NONE)player.getInventory().setArmorContents(new ItemStack[4]);
        else if(value.armor==JunxuTrainingSettings.Armor.STANDARD){
            player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        }else{
            String prefix=value.armor.name();
            player.getInventory().setHelmet(new ItemStack(Material.valueOf(prefix+"_HELMET")));
            player.getInventory().setChestplate(new ItemStack(Material.valueOf(prefix+"_CHESTPLATE")));
            player.getInventory().setLeggings(new ItemStack(Material.valueOf(prefix+"_LEGGINGS")));
            player.getInventory().setBoots(new ItemStack(Material.valueOf(prefix+"_BOOTS")));
        }
        player.getInventory().setItem(0,value.weapon==JunxuTrainingSettings.Weapon.NONE?null:new ItemStack(Material.valueOf(value.weapon.name()+"_SWORD")));
        player.getInventory().setHeldItemSlot(0);player.updateInventory();
    }
    public boolean handleDeath(Player victim,Player killer){
        if(victim==null)return false;PublicPlayerState state=states.get(victim.getUniqueId());if(state==null)return false;
        if(state.entity!=victim||!state.claimDeath())return true;
        state.phase=PublicPlayerState.Phase.RESPAWNING;state.dueTick=tick+respawnTicks;
        retire(state);
        if(CombatListener.INSTANCE!=null)CombatListener.INSTANCE.handlePlayerDeath(victim,killer,false);
        return true;
    }
    private void retire(PublicPlayerState state){PublicPlayerCombat.cancel(state);if(state.npc!=null){NPC npc=state.npc;state.npc=null;removeNpc(npc);}}
    private void removeNpc(NPC npc){
        Entity entity=npc.getEntity();
        if(entity instanceof Player){
            Player player=(Player)entity;PacketPlayOutEntityDestroy packet=new PacketPlayOutEntityDestroy(player.getEntityId());
            for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.hasMetadata("NPC")){
                ((CraftPlayer)viewer).getHandle().playerConnection.sendPacket(packet);NMS.sendTabListRemove(viewer,player);
            }
            if(player.isValid())player.remove();
        }
        npc.despawn();npc.destroy();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void hit(EntityDamageByEntityEvent event){
        if(CombatDamage.accepted(event)<=0)return;PublicPlayerState state=states.get(event.getEntity().getUniqueId());
        if(state!=null){state.knockbackUntil=tick+8;PublicPlayerCombat.cancel(state);
            Entity source=event.getDamager();if(source instanceof Projectile&&((Projectile)source).getShooter() instanceof Entity)source=(Entity)((Projectile)source).getShooter();
            Squad squad=owners.get(state.identity.uuid);
            if(source instanceof Player&&!source.hasMetadata("NPC")&&squad!=null&&!squad.settings.paused&&squad.settings.mode!=JunxuTrainingSettings.Mode.PASSIVE)state.target=source.getUniqueId();
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void npcAttack(NPCDamageEntityEvent event){
        if(!event.getNPC().data().has(KEY))return;
        PublicPlayerState state=states.get(event.getNPC().getUniqueId());Squad squad=owners.get(event.getNPC().getUniqueId());
        if(state==null||state.phase!=PublicPlayerState.Phase.COMBAT||squad==null||tick<squad.formationUntil
                ||squad.settings.paused||squad.settings.mode==JunxuTrainingSettings.Mode.PASSIVE)event.setCancelled(true);
    }
    @EventHandler public void death(NPCDeathEvent event){
        if(!event.getNPC().data().has(KEY))return;event.getDrops().clear();event.setDroppedExp(0);
        if(event.getEvent().getEntity() instanceof Player){Player victim=(Player)event.getEvent().getEntity();handleDeath(victim,victim.getKiller());}
    }
    public void shutdown(){task.cancel();for(Squad squad:new ArrayList<>(squads.values()))close(squad.room);registry.deregisterAll();CitizensAPI.removeNamedNPCRegistry(REGISTRY);}
    public List<String> diagnostics(){
        List<String> lines=new ArrayList<>();
        for(Squad squad:squads.values()){
            long lives=squad.members.stream().mapToLong(s->s.life).sum(),deaths=squad.members.stream().mapToLong(s->s.deaths).sum();
            long outside=squad.members.stream().filter(s->s.npc!=null&&s.npc.isSpawned()&&!squad.room.arena(s.entity.getLocation())).count();
            lines.add("JUNXU_BOTS owner="+squad.room.owner+" alive="+count(squad.room)+" cap="+JunxuEntitlements.bots(squad.room.tier)+" lives="+lives+" deaths="+deaths+" outside="+outside
                    +" health="+squad.settings.health+" armor="+squad.settings.armor+" weapon="+squad.settings.weapon+" mode="+squad.settings.mode+" route="+squad.settings.route+" strength="+squad.settings.strength+" paused="+squad.settings.paused);
        }return lines;
    }
    private static final class Squad implements CombatArena {
        final JunxuRoom room;final List<PublicPlayerState> members=new ArrayList<>();final PublicPlayerCombat combat;
        JunxuTrainingSettings settings;
        long formationUntil;
        Squad(JunxuRoom room,JunxuTrainingSettings settings){this.room=room;this.settings=settings;combat=new PublicPlayerCombat(this,new Random());}
        Location spawn(int index){return room.at(20.5+(index%5)*5,23,42.5+(index/5)*8,180);}
        @Override public boolean combat(Location at){return room.open&&room.arena(at);}
        @Override public boolean standable(Location at){
            return combat(at)&&at.getBlock().getType()==Material.AIR&&at.clone().add(0,1,0).getBlock().getType()==Material.AIR
                    &&at.clone().add(0,-1,0).getBlock().getType().isSolid();
        }
    }
}
