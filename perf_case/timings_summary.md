# Timings Summary (dc6bb4c3b7fd4c95b81e8b23e82351b2)

## Sample

- Window: `1770259360` → `1770263188` (~63.8 min from minute reports)
- Avg TPS (minute reports mean): `19.97`
- Avg MSPT (Full Server Tick processing): `8.01ms`
- Lag ticks: `493` (avg lag MSPT `785.72ms`)
- Worst minute avg MSPT: `36.71ms` at `1770262668` (lag ticks `56`, lag avg `702.62ms`)

## Top Hotspots (overall)

| % of Full Tick | Total | Count | Avg | Category | Handler |
| --- | --- | --- | --- | --- | --- |
| 100.00% | 621.747s | 77620 | 8.010ms | minecraft | Minecraft::Full Server Tick |
| 59.21% | 368.157s | 77620 | 4.743ms | scheduler | Minecraft::Bukkit Scheduler |
| 50.95% | 316.773s | 5719643 | 0.055ms | minecraft | Minecraft::Plugins |
| 36.70% | 228.211s | 1501119 | 0.152ms | other | ThePitPremium::Combined Total |
| 34.19% | 212.602s | 3150339 | 0.067ms | network | Minecraft::Packet Processing |
| 28.77% | 178.848s | 77620 | 2.304ms | minecraft | Minecraft::Worlds |
| 20.26% | 125.949s | 674448 | 0.187ms | network | Minecraft::Packet - net.minecraft.server.v1_8_R3.PacketPlayInFlying$PacketPlayInPositionLook |
| 17.45% | 108.514s | 38809 | 2.796ms | scoreboard | ThePitPremium::Task: cn.charlotte.pit.util.scoreboard.AssembleThread (interval:2) |
| 15.26% | 94.858s | 77620 | 1.222ms | minecraft | Minecraft::world - doTick |
| 11.34% | 70.495s | 77620 | 0.908ms | entities | Minecraft::world - mobSpawn |

## Category Breakdown

| Category | % of Full Tick | Total |
| --- | --- | --- |
| minecraft | 210.45% | 1308.451s |
| scheduler | 95.12% | 591.397s |
| network | 68.39% | 425.204s |
| other | 43.47% | 270.296s |
| entities | 40.73% | 253.226s |
| event | 23.58% | 146.636s |
| scoreboard | 17.46% | 108.564s |
| anticheat | 7.48% | 46.487s |
| chunks | 4.71% | 29.305s |
| npc | 4.42% | 27.499s |

## ThePitPremium Hotspots (top 15)

| % of Full Tick | Total | Count | Avg | Category | Handler |
| --- | --- | --- | --- | --- | --- |
| 36.70% | 228.211s | 1501119 | 0.152ms | other | ThePitPremium::Combined Total |
| 17.45% | 108.514s | 38809 | 2.796ms | scoreboard | ThePitPremium::Task: cn.charlotte.pit.util.scoreboard.AssembleThread (interval:2) |
| 6.23% | 38.708s | 121954 | 0.317ms | scheduler | ThePitPremium::Task: null (Single) |
| 5.48% | 34.091s | 77620 | 0.439ms | scheduler | ThePitPremium::Task: cn.charlotte.pit.runnable.GameRunnable (interval:1) |
| 5.15% | 32.025s | 1072004 | 0.030ms | scheduler | ThePitPremium::Task: null (interval:1) |
| 3.25% | 20.205s | 770515 | 0.026ms | event | ThePitPremium::Event: spg.lgdev.iSpigot (PlayerMoveEvent) |
| 3.11% | 19.322s | 96826 | 0.200ms | npc | ThePitPremium::Event: cn.charlotte.pit.npc.NpcFactory (PitProfileLoadedEvent) |
| 2.45% | 15.243s | 770515 | 0.020ms | event | ThePitPremium::Event: cn.charlotte.pit.util.bossbar.BossBarHandler (PlayerMoveEvent) |
| 2.27% | 14.094s | 37680 | 0.374ms | event | ThePitPremium::Event: cn.charlotte.pit.listener.GameEffectListener (EntityDamageByEntityEvent) |
| 1.53% | 9.506s | 9227 | 1.030ms | event | ThePitPremium::Event: cn.charlotte.pit.listener.CombatListener (PlayerDeathEvent) |
| 1.19% | 7.399s | 3881 | 1.906ms | scheduler | ThePitPremium::Task: cn.charlotte.pit.runnable.ProtectRunnable (interval:20) |
| 0.60% | 3.754s | 3881 | 0.967ms | scheduler | ThePitPremium::Task: cn.charlotte.pit.runnable.GoldDropRunnable (interval:20) |
| 0.36% | 2.227s | 3881 | 0.574ms | scheduler | ThePitPremium::Task: cn.charlotte.pit.util.menu.MenuUpdateTask (interval:20) |
| 0.27% | 1.666s | 193652 | 0.009ms | event | ThePitPremium::Event: cn.charlotte.pit.perk.type.streak.uber.UberStreak (PotionAddEvent) |
| 0.26% | 1.613s | 15587 | 0.103ms | event | ThePitPremium::Event: spg.lgdev.iSpigot (PlayerTeleportEvent) |

## Conclusion

- `Bukkit Scheduler` is the biggest bucket (~59.21% of Full Tick); focus on reducing heavy scheduled tasks and spikes.
- `AssembleThread` (scoreboard) is a confirmed hotspot (~17.45% of Full Tick); mitigate by caching and spreading updates.
- `NpcFactory (PitProfileLoadedEvent)` is visible in the top list (~3.11% of Full Tick); reduce per-player work on join/spawn and lower text refresh frequency.
- `Packet Processing` is also significant (~34.19% of Full Tick); verify whether move/position packets amplify plugin work during lag minutes.

## Notes

- Units: totals are nanoseconds in the raw JSON.
- This summary aggregates across all `history` slices in `timings_history.json`.
- Percentages are based on inclusive timings (handlers include their children), so category totals can exceed 100%.
