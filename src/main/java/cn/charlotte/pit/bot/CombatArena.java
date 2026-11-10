package cn.charlotte.pit.bot;

import org.bukkit.Location;

/** Geometry contract shared by public combat and the private training grounds. */
interface CombatArena {
    boolean combat(Location location);
    boolean standable(Location location);
}
