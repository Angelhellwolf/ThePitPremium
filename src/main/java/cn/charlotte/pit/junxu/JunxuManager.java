package cn.charlotte.pit.junxu;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.bot.JunxuTrainingBots;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.supporter.*;
import cn.charlotte.pit.util.chat.CC;
import net.md_5.bungee.api.chat.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;

/** All Bukkit state belongs to the server thread; a UUID is the sole quota/ownership key. */
public final class JunxuManager {
    private final ThePit plugin;
    private final Map<UUID,JunxuRoom> rooms=new HashMap<>();
    private final Map<Integer,JunxuRoom> slots=new HashMap<>();
    private final Set<UUID> building=new HashSet<>(),previewing=new HashSet<>();
    private final JunxuInvitations invitations=new JunxuInvitations();
    private JunxuLedger ledger;
    private World world;
    private JunxuTemplate template;
    private JunxuNpcs npcs;
    private JunxuTrainingBots bots;
    private JunxuStory story;
    private JunxuSettingsStore settings;
    private JunxuTrainingMenu trainingMenu;
    private JunxuVisibility visibility;
    private YamlConfiguration config;
    private Path configFile;
    private BukkitTask task;
    private boolean ready,closing,templateReady;
    private int visitorCap,inviteSeconds,absenceSeconds,seconds;

    public JunxuManager(ThePit plugin){this.plugin=plugin;}
    public ThePit plugin(){return plugin;}
    public World world(){return world;}
    public boolean isReady(){return ready&&!closing;}
    public JunxuLedger ledger(){return ledger;}
    public JunxuTrainingBots bots(){return bots;}
    public JunxuNpcs npcs(){return npcs;}
    public JunxuStory story(){return story;}
    public JunxuTrainingSettings trainingSettings(UUID owner){return settings.get(owner);}
    public double maximumBotHealth(){return settings.maximumHealth();}
    public void trainingMenu(Player player){if(isReady())trainingMenu.open(player);}
    public void refreshVisibility(Player player){if(visibility!=null)visibility.refresh(player);}
    public JunxuRoom room(UUID owner){return rooms.get(owner);}
    public boolean building(UUID id){return building.contains(id);}
    public int visitorCap(){return visitorCap;}
    public int invitationSeconds(){return inviteSeconds;}
    public int absenceSeconds(){return absenceSeconds;}
    public int inviteCount(){return invitations.size();}
    public Collection<JunxuRoom> rooms(){return Collections.unmodifiableCollection(rooms.values());}

    public void start(){
        if(cn.charlotte.pit.util.command.CommandHandler.getCommands().stream().noneMatch(command->command.getName().equals("junxu")))
            cn.charlotte.pit.util.command.CommandHandler.registerClass(cn.charlotte.pit.command.JunxuCommand.class);
        try{
            if(!Bukkit.getPluginManager().isPluginEnabled("Citizens"))throw new IllegalStateException("Citizens must be enabled");
            configFile=plugin.getDataFolder().toPath().resolve("junxu.yml");
            if(Files.notExists(configFile))plugin.saveResource("junxu.yml",false);
            config=new YamlConfiguration();config.load(configFile.toFile());
            ZoneId zone=ZoneId.of(config.getString("timezone",ZoneId.systemDefault().getId()));
            ledger=new JunxuLedger(plugin.getDataFolder().toPath().resolve("junxu-ledger.dat"),zone);
            settings=new JunxuSettingsStore(plugin.getDataFolder().toPath().resolve("junxu-training.yml"),
                    Math.max(20,Math.min(2048,config.getDouble("bots.maximum-health",200))));
            visitorCap=Math.max(0,Math.min(16,config.getInt("visitors.maximum",1)));
            inviteSeconds=Math.max(1,config.getInt("visitors.invitation-seconds",60));
            absenceSeconds=Math.max(1,config.getInt("owner-absence-seconds",60));
            String name=config.getString("world","junxu_void");
            if(!name.matches("junxu_[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Junxu world name must start with junxu_");
            world=JunxuTemplate.createWorld(name);
            template=new JunxuTemplate(plugin,world,config.getInt("building.blocks-per-tick",2000));
            npcs=new JunxuNpcs(this);bots=new JunxuTrainingBots(this,config.getInt("bots.respawn-delay-ticks",100));
            plugin.getPitBotManager().setJunxuBots(bots);
            story=new JunxuStory(this);
            trainingMenu=new JunxuTrainingMenu(this);
            visibility=new JunxuVisibility(this);
            Bukkit.getPluginManager().registerEvents(new JunxuProtection(this),plugin);
            Bukkit.getPluginManager().registerEvents(new JunxuMenu(this),plugin);
            Bukkit.getPluginManager().registerEvents(trainingMenu,plugin);
            // A loaded world never grants access after a restart. Openings and invitations are transient.
            for(Player player:new ArrayList<>(world.getPlayers()))if(!player.hasMetadata("NPC"))sendBack(player);
            Location entrance=readLocation("entrance");
            if(entrance!=null && entrance.getWorld()!=world)npcs.entrance(entrance);
            ready=true;
            Path stamp=plugin.getDataFolder().toPath().resolve("junxu-template-revision.txt");
            templateReady=Files.exists(stamp)&&Files.readString(stamp).trim().equals(JunxuTemplate.REVISION)
                    &&world.getBlockAt(30,70,11).getType().isSolid();
            if(!templateReady)template.build(0,success->{
                templateReady=success;
                if(success)try{Files.writeString(stamp,JunxuTemplate.REVISION+"\n");}catch(IOException error){fail(error);}
            });
            task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
            plugin.getLogger().info("JUNXU_READY world="+name+" timezone="+zone+" mythicHours=4 sacredHours=8 bots=10 visitors="+visitorCap);
        }catch(Exception error){ready=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"军需处初始化失败，未开放房间",error);shutdown();}
    }
    public SupporterTier tier(Player player){return player==null?SupporterTier.NONE:JunxuEntitlements.tier(profile(player),System.currentTimeMillis());}
    private PlayerProfile profile(Player player){return PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());}
    public String tierName(Player player){
        SupporterTier tier=tier(player);if(tier!=SupporterTier.NONE)return tier.getDisplayName();
        SupporterAccount account=profile(player).getSupporterAccount();
        return account!=null&&account.isPermanent()?"天坑·会员":"普通玩家";
    }
    public long remaining(Player player){return ledger==null?0:ledger.remaining(player.getUniqueId(),System.currentTimeMillis(),JunxuEntitlements.limit(tier(player)));}
    public long remaining(JunxuRoom room){
        long now=System.currentTimeMillis();
        return Math.min(ledger.remaining(room.owner,now,JunxuEntitlements.limit(room.tier)),room.account==null?0:Math.max(0,room.account.getExpiresAt()-now));
    }
    public static String duration(long millis){long seconds=Math.max(0,millis+999)/1000;return String.format("%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
    public void tell(CommandSender player,String message){player.sendMessage(CC.translate(message));}
    public void menu(Player player){
        if(!player.hasPermission("junxu.use")){tell(player,"&c你没有使用军需处的权限。");return;}
        if(!isReady()){tell(player,"&c军需处正在初始化，请稍后再试。");return;}
        new JunxuMenu.View(this,player).open();
    }
    private boolean canTravel(Player player){
        PlayerProfile profile=profile(player);
        if(player.hasMetadata("NPC")||!profile.isLoaded()){tell(player,"&e玩家资料正在载入。");return false;}
        if(profile.isScreenShare()){tell(player,"&c当前状态无法穿过裂隙。");return false;}
        if(!profile.getCombatTimer().hasExpired()&&!isWorld(player.getWorld())){tell(player,"&c战斗状态结束后才能进入军需处。");return false;}
        if(profile.isTempInvUsing()){tell(player,"&c请先结束当前活动，再进入军需处。");return false;}
        return true;
    }
    public void open(Player player){
        if(!isReady()||!canTravel(player))return;
        UUID id=player.getUniqueId();SupporterTier tier=tier(player);
        if(JunxuEntitlements.limit(tier)==0){tell(player,"&c开启军需处需要有效的神话契约或神话圣契。你仍可接受圣契持有者的邀请。");return;}
        if(rooms.containsKey(id)){tell(player,"&e你的军需处已经开启。");return;}
        if(!building.add(id)){tell(player,"&e你的军需处正在构筑，请稍候。");return;}
        if(remaining(player)<=0){building.remove(id);tell(player,"&c今日额度已用尽，将于 "+ledger.zone()+" 00:00 刷新。");return;}
        try{
            JunxuLedger.Entry entry=ledger.create(id,player.getName());
            if(JunxuTemplate.REVISION.equals(entry.builtRevision)&&world.getBlockAt(JunxuRoom.originX(entry.slot)+30,70,JunxuRoom.originZ(entry.slot)+11).getType().isSolid()){
                building.remove(id);begin(player,entry);return;
            }
            tell(player,"&6洛恩：&f正在为你的契约展开独立空间……构筑完成后开始计时。");
            template.build(entry.slot,success->{
                building.remove(id);
                if(!success||!isReady()){if(player.isOnline())tell(player,"&c空间构筑失败，尚未消耗额度。");return;}
                try{ledger.built(id,JunxuTemplate.REVISION);}catch(IOException error){fail(error);return;}
                if(player.isOnline()&&Bukkit.getPlayer(id)==player)begin(player,entry);
            });
        }catch(IOException error){building.remove(id);fail(error);tell(player,"&c军印记录暂不可写入，房间未开启。");}
    }
    private void begin(Player player,JunxuLedger.Entry entry){
        if(!canTravel(player))return;SupporterTier tier=tier(player);SupporterAccount account=profile(player).getSupporterAccount();
        if(tier==SupporterTier.NONE||account==null){tell(player,"&c你的契约已失效。");return;}
        try{
            if(!ledger.open(entry.id,System.currentTimeMillis(),JunxuEntitlements.limit(tier),account.getExpiresAt())){tell(player,"&c今日可用时间不足。");return;}
            JunxuRoom room=new JunxuRoom(entry.id,entry.name,entry.slot,world,tier);room.account=account;room.absentSince=System.currentTimeMillis();
            rooms.put(entry.id,room);slots.put(entry.slot,room);
            try{npcs.open(room);if(JunxuEntitlements.bots(tier)>0)bots.open(room);}
            catch(Exception error){close(room,"军需处启动失败");throw error;}
            if(!enter(player,room)){close(room,"通道传送未完成");return;}
            tell(player,"&a第七军需处已开启。今日剩余 &f"+duration(remaining(room))+"&a。");
            plugin.getLogger().info("JUNXU_OPEN owner="+room.owner+" slot="+room.slot+" session="+room.session+" tier="+tier);
        }catch(Exception error){fail(error);tell(player,"&c空间开启失败，请联系管理员查看记录。");}
    }
    public void enterOwn(Player player){
        JunxuRoom room=rooms.get(player.getUniqueId());
        if(room==null){tell(player,"&e请先开启自己的军需处。");return;}
        if(canTravel(player))enter(player,room);
    }
    public boolean enter(Player player,JunxuRoom room){
        if(!authorized(player,room)){tell(player,"&c你没有这次开放的通行权限。");return false;}
        player.closeInventory();
        if(!player.teleport(room.arrival()))return false;
        refreshVisibility(player);
        previewing.remove(player.getUniqueId());
        for(JunxuRoom other:rooms.values())if(other!=room)other.guests.remove(player.getUniqueId());
        profile(player).setInArena(false);player.setFallDistance(0);player.setFireTicks(0);
        if(player.getGameMode()!=GameMode.CREATIVE)player.setGameMode(GameMode.ADVENTURE);
        if(room.owner.equals(player.getUniqueId()))room.absentSince=-1;
        try{JunxuLedger.Entry entry=ledger.create(player.getUniqueId(),player.getName());if(!entry.introductionComplete)story.play(player,room,false);}
        catch(IOException error){fail(error);}
        return true;
    }
    public void closeOwn(Player player){JunxuRoom room=rooms.get(player.getUniqueId());if(room==null)tell(player,"&e你没有开启中的军需处。");else close(room,"房主结束了本次补给");}
    public void close(JunxuRoom room,String reason){
        if(!room.open)return;
        room.open=false;invitations.invalidate(room.owner);
        List<Player> visitors=occupants(room);
        story.cancel(room);bots.close(room);npcs.close(room);
        rooms.remove(room.owner);slots.remove(room.slot);
        try{ledger.close(room.owner,System.currentTimeMillis());}catch(IOException error){fail(error);}
        for(Player player:visitors){tell(player,"&e"+reason+"。&7本次补给结束。第七军需处，等待你的下一次归来。");sendBack(player);}
        room.guests.clear();
        // Remove only entities in this isolated cell; other rooms keep their effects and Bot states.
        for(Entity entity:new ArrayList<>(world.getEntities()))if(room.contains(entity.getLocation())&&!(entity instanceof Player))entity.remove();
        plugin.getLogger().info("JUNXU_CLOSE owner="+room.owner+" slot="+room.slot+" reason="+reason);
    }
    public void invite(Player owner,String recipientName){
        if(!isReady())return;
        JunxuRoom room=rooms.get(owner.getUniqueId());
        if(room==null||!room.open||tier(owner)!=SupporterTier.SACRED){tell(owner,"&c只有已开启军需处的神话圣契房主可以邀请访客。");return;}
        Player guest=Bukkit.getPlayerExact(recipientName);
        if(guest==null||guest.hasMetadata("NPC")){tell(owner,"&c该玩家当前不在线。");return;}
        if(guest==owner){tell(owner,"&e无需邀请自己。");return;}
        if(room.guests.contains(guest.getUniqueId())){tell(owner,"&e该玩家已持有本次通行权限。");return;}
        if(room.guests.size()>=visitorCap){tell(owner,"&e访客席位已满（"+visitorCap+" 人）。");return;}
        invitations.send(room.owner,guest.getUniqueId(),room.session,System.currentTimeMillis()+inviteSeconds*1000L);
        tell(guest,"&6圣契邀请函 &7· &f"+owner.getName()+" &7邀请你进入 &6第七军需处&7。剩余开放时间 &f"+duration(remaining(room))+"&7；邀请 "+inviteSeconds+" 秒内有效。");
        TextComponent accept=new TextComponent("§a[接受邀请] ");accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/junxu accept "+owner.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder("仅你可接受；进入不消耗你的额度").create()));
        TextComponent decline=new TextComponent("§c[拒绝邀请]");decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/junxu decline "+owner.getName()));
        guest.spigot().sendMessage(accept,decline);tell(owner,"&a已向 "+guest.getName()+" 发出圣契邀请函。");
    }
    private JunxuRoom byName(String name){for(JunxuRoom room:rooms.values())if(room.ownerName.equalsIgnoreCase(name))return room;return null;}
    public void accept(Player guest,String ownerName){
        if(!isReady())return;
        JunxuRoom room=byName(ownerName);long now=System.currentTimeMillis();
        if(room==null||!room.open||currentTier(room,now)!=SupporterTier.SACRED||remaining(room)<=0
                ||invitations.get(guest.getUniqueId(),room.owner,room.session,now)==null){tell(guest,"&c邀请不存在、已到期，或该次开放已结束。");return;}
        if(room.guests.size()>=visitorCap){tell(guest,"&c该军需处访客席位已满。");return;}
        if(!canTravel(guest))return;
        room.guests.add(guest.getUniqueId());
        if(enter(guest,room)){
            invitations.remove(guest.getUniqueId(),room.owner);tell(guest,"&a你已作为访客进入 "+room.ownerName+" 的第七军需处。");
            Player owner=Bukkit.getPlayer(room.owner);if(owner!=null)tell(owner,"&a"+guest.getName()+" 已接受邀请。");
        }else room.guests.remove(guest.getUniqueId());
    }
    public void decline(Player guest,String ownerName){
        JunxuRoom room=byName(ownerName);
        if(room==null||!invitations.remove(guest.getUniqueId(),room.owner)){tell(guest,"&e没有待处理的这封邀请。");return;}
        tell(guest,"&7已拒绝这次邀请。");Player owner=Bukkit.getPlayer(room.owner);if(owner!=null)tell(owner,"&7"+guest.getName()+" 拒绝了邀请。");
    }
    public void leave(Player player){
        if(!isWorld(player.getWorld())){tell(player,"&e你当前不在军需处。");return;}
        for(JunxuRoom room:rooms.values())room.guests.remove(player.getUniqueId());
        story.cancel(player.getUniqueId());previewing.remove(player.getUniqueId());sendBack(player);
        tell(player,"&7已返回公共天坑集结点。房主离开满 "+absenceSeconds+" 秒后自动关闭房间。");
    }
    public void kick(Player owner,String guestName){
        JunxuRoom room=rooms.get(owner.getUniqueId());
        if(room==null||tier(owner)!=SupporterTier.SACRED){tell(owner,"&c只有开启房间的神话圣契房主可以移除访客。");return;}
        UUID id=null;for(UUID candidate:room.guests)if(name(candidate).equalsIgnoreCase(guestName)){id=candidate;break;}
        if(id==null){tell(owner,"&e该玩家不在你的访客名单中。");return;}
        room.guests.remove(id);invitations.remove(id,room.owner);Player guest=Bukkit.getPlayer(id);
        if(guest!=null){story.cancel(id);if(room.contains(guest.getLocation()))sendBack(guest);tell(guest,"&e房主已收回本次军需处通行权限。");}
        tell(owner,"&a已移除访客 "+guestName+"。");
    }
    public String name(UUID id){Player player=Bukkit.getPlayer(id);JunxuLedger.Entry entry=ledger.get(id);return player!=null?player.getName():entry!=null?entry.name:id.toString();}
    public void showVisitors(Player owner){
        JunxuRoom room=rooms.get(owner.getUniqueId());if(room==null||tier(owner)!=SupporterTier.SACRED){tell(owner,"&e神话圣契开启房间后可邀请访客。");return;}
        tell(owner,"&6访客 "+room.guests.size()+"/"+visitorCap+" &7· /junxu invite <玩家名>");
        for(UUID id:room.guests){TextComponent line=new TextComponent("§f"+name(id)+" §c[移除]");line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/junxu kick "+name(id)));owner.spigot().sendMessage(line);}
    }
    public void configureTraining(Player owner,JunxuTrainingSettings value){
        if(!isReady()||JunxuEntitlements.bots(tier(owner))==0){tell(owner,"&c神话契约或神话圣契房主可以设置自己的训练战魂。");return;}
        if(value.health>maximumBotHealth()){tell(owner,"&c生命值范围为 1–"+maximumBotHealth()+"。");return;}
        try{
            settings.put(owner.getUniqueId(),value);JunxuRoom room=rooms.get(owner.getUniqueId());
            if(room!=null)bots.configure(room,value);
            tell(owner,"&a训练设置已保存：&f"+value.health+" 生命值，"+value.armor.title+"，"+value.weapon.title+"，"+value.mode.title+"，"+value.route.title+"，"+value.strength.title+(value.paused?"，已暂停。":"，训练中。"));
        }catch(IOException error){fail(error);tell(owner,"&c训练设置未能保存，请稍后重试。");}
    }
    public boolean isWorld(World candidate){return world!=null&&candidate==world;}
    public JunxuRoom at(Location location){
        if(location==null||!isWorld(location.getWorld()))return null;
        int x=Math.floorDiv(location.getBlockX(),512),z=Math.floorDiv(location.getBlockZ(),512);
        if(x<0||x>=128||z<0)return null;JunxuRoom room=slots.get(z*128+x);
        return room!=null&&room.contains(location)?room:null;
    }
    public boolean authorized(Player player,JunxuRoom room){return player!=null&&room!=null&&room.permits(player.getUniqueId())&&room.open;}
    public boolean isTraining(Player player,Location location){
        JunxuRoom room=at(location);return room!=null&&room.arena(location)&&(authorized(player,room)||bots!=null&&bots.room(player)==room);
    }
    public boolean allowed(Player player,Location location){
        if(!isWorld(location.getWorld()))return true;
        if(previewing.contains(player.getUniqueId())&&player.hasPermission("junxu.admin")&&location.getX()>=0&&location.getX()<61&&location.getZ()>=0&&location.getZ()<61&&location.getY()>=48&&location.getY()<110)return true;
        return authorized(player,at(location));
    }
    public static boolean permitsDamage(Player attacker,Player victim){
        ThePit plugin=ThePit.getInstance();JunxuManager manager=plugin==null?null:plugin.getJunxuManager();
        if(manager==null||(!manager.isWorld(victim.getWorld())&&(attacker==null||!manager.isWorld(attacker.getWorld()))))return true;
        if(!manager.isTraining(victim,victim.getLocation()))return false;
        return attacker==null||manager.isTraining(attacker,attacker.getLocation())&&manager.at(attacker.getLocation())==manager.at(victim.getLocation());
    }
    public List<Player> occupants(JunxuRoom room){List<Player> result=new ArrayList<>();for(Player p:world.getPlayers())if(!p.hasMetadata("NPC")&&room.contains(p.getLocation()))result.add(p);return result;}
    public Location respawn(Player player){JunxuRoom room=at(player.getLocation());return authorized(player,room)?room.arrival():null;}
    public Location rescue(Player player){
        Location current=player.getLocation();
        if(isWorld(current.getWorld())){
            for(JunxuRoom room:rooms.values())if(authorized(player,room)
                    &&current.getX()>=JunxuRoom.originX(room.slot)&&current.getX()<JunxuRoom.originX(room.slot)+61
                    &&current.getZ()>=JunxuRoom.originZ(room.slot)&&current.getZ()<JunxuRoom.originZ(room.slot)+61)return room.arrival();
        }
        for(JunxuRoom room:rooms.values())if(authorized(player,room))return room.arrival();
        if(previewing.contains(player.getUniqueId()))return new Location(world,30.5,71,11.5);return safeReturn();
    }
    public void sendBack(Player player){
        player.closeInventory();Location destination=safeReturn();
        player.setFallDistance(0);player.setFireTicks(0);
        if(!player.teleport(destination)){player.kickPlayer("军需处通道已关闭，请重新连接返回集结点。");return;}
        refreshVisibility(player);
        PlayerProfile returned=profile(player);returned.setInArena(false);
        returned.setCombatTimer(new cn.charlotte.pit.util.cooldown.Cooldown(0));returned.getDamageMap().clear();
        if(cn.charlotte.pit.listener.CombatListener.INSTANCE!=null)cn.charlotte.pit.listener.CombatListener.INSTANCE.forgetCombatState(player.getUniqueId());
        if(player.getGameMode()!=GameMode.CREATIVE)player.setGameMode(GameMode.ADVENTURE);
    }
    public Location safeReturn(){
        Location configured=config==null?null:readLocation("return");
        if(configured!=null&&!isWorld(configured.getWorld())&&safe(configured))return configured;
        for(Location spawn:plugin.getPitConfig().getSpawnLocations())if(spawn!=null&&spawn.getWorld()!=world&&safe(spawn))return spawn.clone();
        throw new IllegalStateException("No safe public Pit return point");
    }
    private boolean safe(Location at){at.getChunk().load();return at.getBlock().getType().isTransparent()&&at.clone().add(0,1,0).getBlock().getType().isTransparent()&&at.clone().add(0,-1,0).getBlock().getType().isSolid();}
    private Location readLocation(String key){
        String worldName=config.getString(key+".world");World w=worldName==null?null:Bukkit.getWorld(worldName);if(w==null)return null;
        return new Location(w,config.getDouble(key+".x"),config.getDouble(key+".y"),config.getDouble(key+".z"),(float)config.getDouble(key+".yaw"),(float)config.getDouble(key+".pitch"));
    }
    public void createEntrance(Player admin){
        if(!admin.hasPermission("junxu.admin")){tell(admin,"&c此命令仅管理员可用。");return;}
        if(!isReady())return;Location at=admin.getLocation();if(isWorld(at.getWorld())){tell(admin,"&c入口军需官应创建在公共天坑集结区。");return;}
        try{
            writeLocation("entrance",at);saveConfig();npcs.entrance(at);
            tell(admin,"&a军需官·洛恩已创建，位置与朝向已保存。重复执行会迁移现有入口。");
        }catch(IOException error){fail(error);tell(admin,"&c入口位置未能保存。");}
    }
    public void setReturn(Player admin){if(!admin.hasPermission("junxu.admin")||isWorld(admin.getWorld())||!safe(admin.getLocation())){tell(admin,"&c请站在公共天坑的安全地面设置退出点。");return;}try{writeLocation("return",admin.getLocation());saveConfig();tell(admin,"&a安全返回点已保存。");}catch(IOException e){fail(e);}}
    private void writeLocation(String key,Location at){config.set(key+".world",at.getWorld().getName());config.set(key+".x",at.getX());config.set(key+".y",at.getY());config.set(key+".z",at.getZ());config.set(key+".yaw",at.getYaw());config.set(key+".pitch",at.getPitch());}
    private void saveConfig()throws IOException{
        Path tmp=configFile.resolveSibling(configFile.getFileName()+".tmp");byte[] bytes=config.saveToString().getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
        Files.move(tmp,configFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public void preview(Player admin){if(!isReady()||!admin.hasPermission("junxu.admin"))return;if(!templateReady){tell(admin,"&e精装模板正在生成。");return;}previewing.add(admin.getUniqueId());if(!admin.teleport(new Location(world,30.5,71,11.5)))previewing.remove(admin.getUniqueId());else tell(admin,"&6已进入第七军需处精装模板。使用 /junxu leave 返回。");}
    private SupporterTier currentTier(JunxuRoom room,long now){
        Player owner=Bukkit.getPlayer(room.owner);
        if(owner!=null&&profile(owner).isLoaded()&&profile(owner).getSupporterAccount()!=null)room.account=profile(owner).getSupporterAccount();
        return JunxuEntitlements.tier(room.account,now);
    }
    private void tick(){
        if(!isReady())return;long now=System.currentTimeMillis();seconds++;
        try{
            invitations.expire(now);npcs.tick();
            for(JunxuRoom room:new ArrayList<>(rooms.values())){
                SupporterTier tier=currentTier(room,now);
                if(tier==SupporterTier.NONE){close(room,"契约已到期");continue;}
                if(tier!=room.tier){
                    bots.close(room);
                    if(tier!=SupporterTier.SACRED){invitations.invalidate(room.owner);for(UUID id:new HashSet<>(room.guests)){Player guest=Bukkit.getPlayer(id);if(guest!=null&&room.contains(guest.getLocation()))sendBack(guest);}room.guests.clear();}
                    room.tier=tier;
                    bots.open(room);
                }
                Player owner=Bukkit.getPlayer(room.owner);
                if(owner!=null&&owner.isOnline()&&room.contains(owner.getLocation()))room.absentSince=-1;
                else if(room.absentSince<0)room.absentSince=now;
                if(room.absentSince>=0&&now-room.absentSince>=absenceSeconds*1000L){close(room,"房主已离开超过 "+absenceSeconds+" 秒");continue;}
                if(!ledger.renew(room.owner,now,JunxuEntitlements.limit(tier),room.account.getExpiresAt())){close(room,"星火核心供能耗尽，请返回集结点");continue;}
                long day=ledger.day(now),left=remaining(room);
                if(day!=room.reminderDay){room.reminderDay=day;room.warnedTen=false;room.warnedOne=false;}
                if(left<=600000&&!room.warnedTen){room.warnedTen=true;warn(room,"不足 10 分钟");}
                if(left<=60000&&!room.warnedOne){room.warnedOne=true;warn(room,"不足 1 分钟");}
                if(seconds%30==0&&owner!=null&&SupporterService.get()!=null)SupporterService.get().refresh(owner);
            }
            for(Player player:new ArrayList<>(world.getPlayers()))if(!player.hasMetadata("NPC")){
                if(!allowed(player,player.getLocation())||player.getLocation().getY()<69){player.teleport(rescue(player));player.setFallDistance(0);}
                profile(player).setInArena(isTraining(player,player.getLocation()));
            }
            JunxuMenu.refresh(this);
            if(visibility!=null)visibility.tick();
        }catch(Exception error){
            fail(error);
            for(JunxuRoom room:new ArrayList<>(rooms.values()))try{close(room,"军印记录服务暂不可用");}catch(Exception cleanup){plugin.getLogger().log(java.util.logging.Level.SEVERE,"军需处关闭失败",cleanup);}
        }
    }
    private void warn(JunxuRoom room,String left){for(Player player:occupants(room))tell(player,"&e星火核心供能即将耗尽（"+left+"），请各位返回集结点。");}
    private void fail(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"JUNXU_FAILURE",error);}
    public void shutdown(){
        if(closing)return;closing=true;
        if(task!=null)task.cancel();
        for(JunxuRoom room:new ArrayList<>(rooms.values()))try{close(room,"服务器维护，通道已关闭");}catch(Exception error){fail(error);}
        if(story!=null)story.shutdown();if(bots!=null)bots.shutdown();if(npcs!=null)npcs.shutdown();if(template!=null)template.shutdown();if(visibility!=null)visibility.shutdown();
        if(world!=null){for(Player player:new ArrayList<>(world.getPlayers()))if(!player.hasMetadata("NPC"))try{sendBack(player);}catch(Exception error){fail(error);}world.save();}
        ready=false;
    }
    public List<String> diagnostics(){
        List<String> lines=new ArrayList<>();lines.add("JUNXU_STATUS ready="+isReady()+" world="+(world==null?"none":world.getName())+" rooms="+rooms.size()+" building="+building.size()+" invitations="+inviteCount()+" templateReady="+templateReady+" entrance="+(npcs==null?0:npcs.entranceCount()));
        if(ledger!=null)lines.add("JUNXU_STORAGE entries="+ledger.entries().size()+" timezone="+ledger.zone()+" durableLeaseMillis=5000");
        for(JunxuRoom room:rooms.values())lines.add("JUNXU_ROOM owner="+room.ownerName+" uuid="+room.owner+" slot="+room.slot+" tier="+room.tier+" services="+npcs.count(room)+" bots="+bots.count(room)+" guests="+room.guests.size()+" remainingMillis="+remaining(room)+" absentSince="+room.absentSince);
        if(bots!=null)lines.addAll(bots.diagnostics());return lines;
    }
}
