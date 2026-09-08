package cn.charlotte.pit.bot;

import net.citizensnpcs.api.npc.NPC;
import cn.charlotte.pit.data.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

/** A fixed slot outlives its NPC instance and every entity created by respawning. */
final class PublicPlayerState {
    enum Phase { RESPAWNING, WAITING, ENTERING, DROPPING, COMBAT }
    final PublicPlayerIdentityStore.Identity identity;
    PlayerProfile profile;
    NPC npc;
    Player entity;
    Phase phase = Phase.RESPAWNING;
    long dueTick, phaseTick, nextTargetTick, nextPathTick, nextAttackTick, knockbackUntil, lastProgressTick, nextPatrolTick;
    UUID target;
    Location navGoal, patrolGoal, progressLocation;
    int routeIndex, life, jumps, deaths, hits, completedCombatLives, recoveries, strafe = 1;
    boolean enteredThisLife, hitThisLife, deathHandled;
    PublicCombatDifficulty.Style difficulty=PublicCombatDifficulty.NORMAL;
    UUID difficultyTarget;
    long nextDifficultyTick;

    PublicPlayerState(PublicPlayerIdentityStore.Identity identity) { this.identity = identity; }

    void born(NPC npc, Player entity, long tick, int waitTicks) {
        this.npc = npc; this.entity = entity;
        life++; routeIndex = 0; target = null; navGoal = null; patrolGoal = null;
        enteredThisLife = false; hitThisLife = false; deathHandled = false;
        phase = Phase.WAITING; phaseTick = tick; dueTick = tick + waitTicks;
        nextTargetTick = tick + identity.slot * 3L; nextAttackTick = tick + identity.slot * 2L;
        lastProgressTick = tick; progressLocation = entity.getLocation();
        knockbackUntil = 0;
        difficultyTarget = null; nextDifficultyTick = 0; difficulty = PublicCombatDifficulty.NORMAL;
    }
    void enteredPit(long tick) {
        phase = Phase.COMBAT; phaseTick = tick;
        if (!enteredThisLife) { enteredThisLife = true; jumps++; }
    }
    void hitHuman() { hitThisLife = true; hits++; }
    boolean claimDeath() {
        if (deathHandled || phase == Phase.RESPAWNING) return false;
        deathHandled = true; deaths++;
        if (enteredThisLife && hitThisLife) completedCombatLives++;
        return true;
    }
    int completedLoops() { return Math.max(0, Math.min(completedCombatLives, jumps - 1)); }
}
