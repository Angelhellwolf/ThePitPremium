package cn.charlotte.pit.junxu;

import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.Vector;

public final class JunxuProtection implements Listener {
    private final JunxuManager manager;
    public JunxuProtection(JunxuManager manager){this.manager=manager;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void teleport(PlayerTeleportEvent event){
        Player player=event.getPlayer();if(player.hasMetadata("NPC")||event.getTo()==null)return;
        if(!manager.allowed(player,event.getTo())){event.setCancelled(true);manager.tell(player,"&c你没有这处军需处的通行权限。");}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void changedWorld(PlayerChangedWorldEvent event){
        if(!event.getPlayer().hasMetadata("NPC"))manager.refreshVisibility(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void move(PlayerMoveEvent event){
        Player player=event.getPlayer();if(player.hasMetadata("NPC")||event.getTo()==null||!manager.isWorld(event.getTo().getWorld()))return;
        if(!manager.allowed(player,event.getTo())||event.getTo().getY()<69){
            event.setTo(manager.rescue(player));player.setFallDistance(0);player.setVelocity(new Vector());
        }
        PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()).setInArena(manager.isTraining(player,event.getTo()));
    }
    @EventHandler(priority=EventPriority.LOWEST)public void damage(EntityDamageEvent event){
        if(!manager.isWorld(event.getEntity().getWorld()))return;
        if(event.getEntity() instanceof Player){
            Player victim=(Player)event.getEntity();
            if(event.getCause()==EntityDamageEvent.DamageCause.VOID&&!victim.hasMetadata("NPC")){
                event.setCancelled(true);victim.teleport(manager.rescue(victim));victim.setFallDistance(0);return;
            }
            if(!JunxuManager.permitsDamage(null,victim))event.setCancelled(true);
        }else event.setCancelled(true);
        if(event instanceof EntityDamageByEntityEvent){
            Entity source=((EntityDamageByEntityEvent)event).getDamager();
            if(source instanceof Projectile&&((Projectile)source).getShooter() instanceof Entity)source=(Entity)((Projectile)source).getShooter();
            if(source instanceof Player&&event.getEntity() instanceof Player&&!JunxuManager.permitsDamage((Player)source,(Player)event.getEntity()))event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void enforceDamage(EntityDamageEvent event){damage(event);}
    @EventHandler(priority=EventPriority.LOWEST)public void place(BlockPlaceEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)public void breakBlock(BlockBreakEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void bucket(PlayerBucketEmptyEvent event){if(manager.isWorld(event.getPlayer().getWorld()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST)public void bucketFill(PlayerBucketFillEvent event){if(manager.isWorld(event.getPlayer().getWorld()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)public void explode(EntityExplodeEvent event){if(manager.isWorld(event.getLocation().getWorld())){event.blockList().clear();event.setYield(0);}}
    @EventHandler(priority=EventPriority.HIGHEST)public void explodeBlock(BlockExplodeEvent event){if(manager.isWorld(event.getBlock().getWorld())){event.blockList().clear();event.setYield(0);}}
    @EventHandler public void ignite(BlockIgniteEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler public void burn(BlockBurnEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler public void form(BlockFormEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler public void flow(BlockFromToEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler public void change(EntityChangeBlockEvent event){if(manager.isWorld(event.getBlock().getWorld()))event.setCancelled(true);}
    @EventHandler public void portal(PlayerPortalEvent event){if(manager.isWorld(event.getFrom().getWorld())||event.getTo()!=null&&manager.isWorld(event.getTo().getWorld()))event.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent event){if(event.getPlayer().hasMetadata("NPC"))return;Bukkit.getScheduler().runTaskLater(manager.plugin(),()->{
        Player player=event.getPlayer();if(player.isOnline()&&manager.isWorld(player.getWorld())&&!manager.allowed(player,player.getLocation()))manager.sendBack(player);
    },2);}
    @EventHandler public void quit(PlayerQuitEvent event){if(event.getPlayer().hasMetadata("NPC"))return;manager.story().cancel(event.getPlayer().getUniqueId());for(JunxuRoom room:manager.rooms())room.guests.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST)public void respawn(PlayerRespawnEvent event){Location location=manager.respawn(event.getPlayer());if(location!=null)event.setRespawnLocation(location);else if(manager.isWorld(event.getRespawnLocation().getWorld()))event.setRespawnLocation(manager.safeReturn());}
    @EventHandler(ignoreCancelled=true)public void unload(ChunkUnloadEvent event){
        if(!manager.isWorld(event.getWorld()))return;int x=event.getChunk().getX()<<4,z=event.getChunk().getZ()<<4;
        for(JunxuRoom room:manager.rooms())if(x+15>=JunxuRoom.originX(room.slot)&&x<=JunxuRoom.originX(room.slot)+60&&z+15>=JunxuRoom.originZ(room.slot)&&z<=JunxuRoom.originZ(room.slot)+60){event.setCancelled(true);return;}
    }
}
