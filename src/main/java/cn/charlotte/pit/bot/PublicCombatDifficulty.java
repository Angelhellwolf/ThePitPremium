package cn.charlotte.pit.bot;

import cn.charlotte.pit.data.PlayerProfile;
import java.util.*;

/** Game progression and recent accepted hit exchanges; paid ranks never determine opponent strength. */
final class PublicCombatDifficulty {
    static final class Style {
        final String name;final int reaction,minimumAttack,attackJitter;final float turn;
        final double strafeChance,moveScale,hesitateChance;
        Style(String name,int reaction,int attack,int jitter,float turn,double strafe,double move,double hesitate){
            this.name=name;this.reaction=reaction;this.minimumAttack=attack;this.attackJitter=jitter;this.turn=turn;
            this.strafeChance=strafe;this.moveScale=move;this.hesitateChance=hesitate;
        }
    }
    static final Style NOVICE=new Style("NOVICE",16,12,6,9,0.06,0.72,0.20);
    static final Style NORMAL=new Style("NORMAL",8,7,4,18,0.20,0.92,0.07);
    static final Style EXPERT=new Style("EXPERT",3,4,3,26,0.40,1.0,0.01);
    private final Map<UUID,Exchange> exchanges=new HashMap<>();
    Style forPlayer(PlayerProfile profile,long now){
        Exchange exchange=exchanges.get(profile.getPlayerUuid());
        int dealt=exchange==null||now-exchange.last>90000?0:exchange.playerHits;
        int received=exchange==null||now-exchange.last>90000?0:exchange.botHits;
        return select(profile.getPrestige(),profile.getLevel(),profile.getKills(),profile.getDeaths(),dealt,received);
    }
    static Style select(int prestige,int level,int kills,int deaths,int playerHits,int botHits){
        if(playerHits+botHits>=12&&playerHits/(double)(playerHits+botHits)>=0.85)return EXPERT;
        double base=prestige>=10?0.85:prestige>=5?0.72:prestige>=1?0.48:Math.min(0.42,Math.max(0,level)/120.0*0.42);
        if(kills+deaths>=100){double ratio=(kills+10.0)/(deaths+10.0);base+=ratio>=2?0.12:ratio<0.5?-0.10:0;}
        if(playerHits+botHits>=8){double share=playerHits/(double)(playerHits+botHits);base+=share>=0.75?0.40:share<0.30?-0.30:0;}
        if(prestige==0&&level<60&&kills<200&&playerHits+botHits<8)return NOVICE;
        return base<0.35?NOVICE:base>=0.65?EXPERT:NORMAL;
    }
    void hit(UUID human,boolean byHuman,long now){
        if(exchanges.size()>2048)exchanges.entrySet().removeIf(entry->now-entry.getValue().last>90000);
        Exchange value=exchanges.computeIfAbsent(human,ignored->new Exchange());
        if(now-value.last>90000){value.playerHits=0;value.botHits=0;}
        if(byHuman)value.playerHits++;else value.botHits++;value.last=now;
        if(value.playerHits+value.botHits>40){value.playerHits=(value.playerHits+1)/2;value.botHits=(value.botHits+1)/2;}
    }
    private static final class Exchange {int playerHits,botHits;long last;}
}
