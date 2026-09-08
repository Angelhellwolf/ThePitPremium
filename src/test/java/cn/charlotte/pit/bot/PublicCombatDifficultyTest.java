package cn.charlotte.pit.bot;

import org.junit.Test;
import static org.junit.Assert.*;

public class PublicCombatDifficultyTest {
    @Test public void newPlayersReceiveSlowerReactionsAndMoreOpenings(){
        PublicCombatDifficulty.Style novice=PublicCombatDifficulty.select(0,20,10,30,0,0);
        PublicCombatDifficulty.Style expert=PublicCombatDifficulty.select(10,120,5000,800,0,0);
        assertSame(PublicCombatDifficulty.NOVICE,novice);assertSame(PublicCombatDifficulty.EXPERT,expert);
        assertTrue(novice.reaction>expert.reaction);assertTrue(novice.minimumAttack>expert.minimumAttack);
        assertTrue(novice.hesitateChance>expert.hesitateChance);assertTrue(novice.turn<expert.turn);
    }
    @Test public void actualExchangesCanRecognizeASkilledNewAccount(){
        assertSame(PublicCombatDifficulty.EXPERT,PublicCombatDifficulty.select(0,20,0,0,15,1));
        assertSame(PublicCombatDifficulty.NOVICE,PublicCombatDifficulty.select(1,100,80,100,1,15));
    }
    @Test public void tinySamplesDoNotTurnTheFirstHitIntoAnExpertOpponent(){
        assertSame(PublicCombatDifficulty.NOVICE,PublicCombatDifficulty.select(0,20,0,0,1,0));
    }
}
