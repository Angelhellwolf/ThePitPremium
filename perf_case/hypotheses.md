# Root-Cause Hypotheses (dc6bb4c3b7fd4c95b81e8b23e82351b2)

Evidence source: `perf_case/timings_summary.md` (generated from `perf_case/timings_raw.json` + `perf_case/timings_history.json`).

## H1) Scoreboard refresh is the dominant plugin-level scheduler cost

**Evidence**
- `ThePitPremium::Task: cn.charlotte.pit.util.scoreboard.AssembleThread (interval:2)` is ~`17.45%` of Full Server Tick total time. (`perf_case/timings_summary.md`)

**Hypothesis**
- The scoreboard pipeline performs per-player work too frequently (2 ticks) and/or with per-run spikes (lots of online players updated in the same run).

**How to verify**
1) Baseline timings:
   - `/timings on`, `/timings reset`, wait 5–10 min under normal load, `/timings paste`.
2) Increase scoreboard update interval (no code change):
   - `plugins/ThePitPremium/custom.yml`: set `scoreboard-update-ticks: 10` (or `20`), restart server, repeat timings.
3) Compare:
   - `AssembleThread` total% and avg ms should drop proportionally.
4) Optional deeper proof:
   - `/spark profiler --timeout 60 --thread server` and confirm scoreboard methods in top stacks.

## H2) “null (Single)” scheduler tasks are caused by NPC library / NPC text updates

**Evidence**
- `ThePitPremium::Event: cn.charlotte.pit.npc.NpcFactory (PitProfileLoadedEvent)` is ~`3.11%` total time.
- `ThePitPremium::Task: null (Single)` is ~`6.23%` total time. (`perf_case/timings_summary.md`)

**Hypothesis**
- NPC creation/text updates schedule anonymous synchronous tasks (often rendered as `null`) and/or do expensive per-player per-NPC operations.

**How to verify**
1) Re-run timings and check whether `NpcFactory (PitProfileLoadedEvent)` and `null (Single)` drop after applying the NPC optimization patch (Step E).
2) Add targeted metrics (Step E) to log:
   - `npc.profileLoaded` handler duration
   - `npc.textUpdate` runnable duration and how many players/NPCs were updated
3) Optional: temporarily disable the NPC feature/plugin integration and confirm `null (Single)` disappears (if feasible).

## H3) Packet Processing / PlayerMove hot path amplifies lag spikes

**Evidence**
- `Minecraft::Packet Processing` ~`34.19%` of Full Server Tick total.
- `PacketPlayInFlying$PacketPlayInPositionLook` ~`20.26%`. (`perf_case/timings_summary.md`)

**Hypothesis**
- Heavy `PlayerMoveEvent` listeners (e.g. anti-cheat + Pit move handlers + bossbar) and per-packet work increases CPU cost; during spikes this becomes the limiter.

**How to verify**
1) Use spark main-thread profiler:
   - `/spark profiler --timeout 60 --thread server`
   - Expect `PlayerMoveEvent` stacks and packet handlers near the top.
2) Compare timings before/after:
   - Look at `Matrix::Event ... (PlayerMoveEvent)` and Pit’s move/bossbar handlers.
3) If Matrix supports it, reduce move checks frequency / disable per-tick “FoliaSupport” task and compare.

## H4) Per-tick game loop (`GameRunnable`) is a steady background cost that grows with player count

**Evidence**
- `ThePitPremium::Task: cn.charlotte.pit.runnable.GameRunnable (interval:1)` ~`5.48%`. (`perf_case/timings_summary.md`)

**Hypothesis**
- The runnable performs per-player scans (perks/enchant NBT parsing) each tick; cost scales linearly with online players.

**How to verify**
1) Add lightweight timing metrics around `GameRunnable.run()` (Step E) and log average/max every 60s.
2) Validate improved cost after applying the small scan optimizations (and/or increasing the runnable interval if gameplay allows).

## H5) Matrix per-tick FoliaSupport task adds constant overhead

**Evidence**
- `Matrix::Task: ...FoliaSupport... (interval:1)` ~`7.36%`. (`perf_case/timings_summary.md`)

**Hypothesis**
- The anti-cheat runs per-player per-tick compatibility logic; it is not free and competes with game logic.

**How to verify**
1) Check Matrix config for per-tick compatibility toggles; disable/reduce frequency; compare timings.
2) Confirm `Matrix::Task ... FoliaSupport...` drops and overall MSPT improves.

