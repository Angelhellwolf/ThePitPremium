package cn.charlotte.pit.command;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.junxu.JunxuManager;
import cn.charlotte.pit.util.command.Command;
import cn.charlotte.pit.util.command.param.Parameter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JunxuCommand {
    private JunxuManager manager(){return ThePit.getInstance().getJunxuManager();}
    @Command(names="junxu",permissionNode="junxu.use")public void menu(Player player){if(manager()!=null)manager().menu(player);}
    @Command(names="junxu open",permissionNode="junxu.use")public void open(Player player){if(manager()!=null)manager().open(player);}
    @Command(names="junxu enter",permissionNode="junxu.use")public void enter(Player player){if(manager()!=null)manager().enterOwn(player);}
    @Command(names="junxu close",permissionNode="junxu.use")public void close(Player player){if(manager()!=null)manager().closeOwn(player);}
    @Command(names="junxu invite",permissionNode="junxu.use")public void invite(Player player,@Parameter(name="玩家名")String target){if(manager()!=null)manager().invite(player,target);}
    @Command(names="junxu accept",permissionNode="junxu.use")public void accept(Player player,@Parameter(name="房主名")String owner){if(manager()!=null)manager().accept(player,owner);}
    @Command(names={"junxu decline","junxu reject"},permissionNode="junxu.use")public void decline(Player player,@Parameter(name="房主名")String owner){if(manager()!=null)manager().decline(player,owner);}
    @Command(names="junxu leave",permissionNode="junxu.use")public void leave(Player player){if(manager()!=null)manager().leave(player);}
    @Command(names="junxu kick",permissionNode="junxu.use")public void kick(Player player,@Parameter(name="玩家名")String target){if(manager()!=null)manager().kick(player,target);}
    @Command(names="junxu visitors",permissionNode="junxu.use")public void visitors(Player player){if(manager()!=null)manager().showVisitors(player);}
    @Command(names={"junxu story","junxu story replay"},permissionNode="junxu.use")public void story(Player player){if(manager()!=null&&manager().story()!=null)manager().story().replay(player);}
    @Command(names="junxu story skip",permissionNode="junxu.use")public void skip(Player player){if(manager()!=null&&manager().story()!=null)manager().story().skip(player);}
    @Command(names="junxu admin npc create",permissionNode="junxu.admin")public void npc(Player player){if(manager()!=null)manager().createEntrance(player);}
    @Command(names="junxu admin return set",permissionNode="junxu.admin")public void returnPoint(Player player){if(manager()!=null)manager().setReturn(player);}
    @Command(names="junxu admin template",permissionNode="junxu.admin")public void template(Player player){if(manager()!=null)manager().preview(player);}
    @Command(names="junxu admin status",permissionNode="junxu.admin")public void status(CommandSender sender){if(manager()!=null)for(String line:manager().diagnostics())sender.sendMessage(line);else sender.sendMessage("JUNXU_STATUS unavailable");}
    @Command(names="junxu bot",permissionNode="junxu.use")public void bots(Player player){if(manager()!=null)manager().trainingMenu(player);}
    @Command(names="junxu bot health",permissionNode="junxu.use")public void health(Player player,@Parameter(name="生命值")double health){
        if(manager()==null)return;
        if(!Double.isFinite(health)||health<1||health>manager().maximumBotHealth()){manager().tell(player,"&c生命值范围为 1–"+manager().maximumBotHealth()+"。");return;}
        manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).health(health));
    }
    @Command(names="junxu bot armor",permissionNode="junxu.use")public void armor(Player player,@Parameter(name="standard/none/leather/chainmail/iron/diamond")String armor){
        if(manager()==null)return;try{manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).armor(cn.charlotte.pit.junxu.JunxuTrainingSettings.Armor.valueOf(armor.toUpperCase(java.util.Locale.ROOT))));}
        catch(IllegalArgumentException error){manager().tell(player,"&c盔甲可选：standard、none、leather、chainmail、iron、diamond。");}
    }
    @Command(names="junxu bot weapon",permissionNode="junxu.use")public void weapon(Player player,@Parameter(name="none/wood/stone/iron/diamond")String weapon){
        if(manager()==null)return;try{manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).weapon(cn.charlotte.pit.junxu.JunxuTrainingSettings.Weapon.valueOf(weapon.toUpperCase(java.util.Locale.ROOT))));}
        catch(IllegalArgumentException error){manager().tell(player,"&c武器可选：none、wood、stone、iron、diamond。");}
    }
    @Command(names="junxu bot mode",permissionNode="junxu.use")public void mode(Player player,@Parameter(name="hostile/retaliate/passive")String mode){
        if(manager()==null)return;try{manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).mode(cn.charlotte.pit.junxu.JunxuTrainingSettings.Mode.valueOf(mode.toUpperCase(java.util.Locale.ROOT))));}
        catch(IllegalArgumentException error){manager().tell(player,"&c行为可选：hostile 主动攻击、retaliate 受击反击、passive 不还手。");}
    }
    @Command(names="junxu bot hostile",permissionNode="junxu.use")public void hostile(Player player,@Parameter(name="true/false")boolean hostile){
        if(manager()!=null)manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).mode(hostile?cn.charlotte.pit.junxu.JunxuTrainingSettings.Mode.HOSTILE:cn.charlotte.pit.junxu.JunxuTrainingSettings.Mode.PASSIVE));
    }
    @Command(names="junxu bot route",permissionNode="junxu.use")public void route(Player player,@Parameter(name="formation/ring/cross/follow/stationary")String route){
        if(manager()==null)return;try{manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).route(cn.charlotte.pit.junxu.JunxuTrainingSettings.Route.valueOf(route.toUpperCase(java.util.Locale.ROOT))));}
        catch(IllegalArgumentException error){manager().tell(player,"&c路线可选：formation 分组、ring 环场、cross 交叉、follow 跟随、stationary 驻守。");}
    }
    @Command(names="junxu bot strength",permissionNode="junxu.use")public void strength(Player player,@Parameter(name="easy/normal/hard")String strength){
        if(manager()==null)return;try{manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).strength(cn.charlotte.pit.junxu.JunxuTrainingSettings.Strength.valueOf(strength.toUpperCase(java.util.Locale.ROOT))));}
        catch(IllegalArgumentException error){manager().tell(player,"&c强度可选：easy 轻度、normal 标准、hard 强劲。");}
    }
    @Command(names="junxu bot state",permissionNode="junxu.use")public void state(Player player,@Parameter(name="active/paused")String state){
        if(manager()==null)return;if(!state.equalsIgnoreCase("active")&&!state.equalsIgnoreCase("paused")){manager().tell(player,"&c状态可选：active 训练、paused 暂停。");return;}
        manager().configureTraining(player,manager().trainingSettings(player.getUniqueId()).paused(state.equalsIgnoreCase("paused")));
    }
}
