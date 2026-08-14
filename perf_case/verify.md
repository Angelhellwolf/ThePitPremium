# Verify / Rollback Guide (dc6bb4c3b7fd4c95b81e8b23e82351b2)

## 0) Reproduce the data locally (optional)

Fetch + save the raw JSON locally (writes into `perf_case/`):

```bash
python3 perf_case/fetch_timings.py --id dc6bb4c3b7fd4c95b81e8b23e82351b2 --no-proxy
python3 perf_case/parse_timings.py
```

If `--no-proxy` fails in your environment, retry without it (uses `HTTP(S)_PROXY` env vars):

```bash
python3 perf_case/fetch_timings.py --id dc6bb4c3b7fd4c95b81e8b23e82351b2
```

Outputs:
- `perf_case/timings_raw.json`: master + minute reports + idmap
- `perf_case/timings_history.json`: handler breakdown (`history=...`)
- `perf_case/timings_summary.md`: top hotspots table (generated)

## 1) Baseline (before patch)

On the server (under realistic player load):

```text
/timings on
/timings reset
# wait 5–10 minutes
/timings paste
/timings off
```

Record:
- `Bukkit Scheduler` total%
- `ThePitPremium::Task: AssembleThread`
- `ThePitPremium::Event: cn.charlotte.pit.npc.NpcFactory (PitProfileLoadedEvent)`
- `ThePitPremium::Task: null (Single)` / `null (interval:1)`

## 2) Apply patch build

Build the plugin jar:

```bash
chmod +x ./gradlew
./gradlew clean shadowJar
```

Deploy the new jar to the server, restart (avoid `/reload`).

Runtime note (Java 15+): the JDK no longer ships Nashorn. This project bundles `org.openjdk.nashorn:nashorn-core` into the shaded jar and `JSHandler` uses the `org.openjdk.nashorn.*` APIs (not `jdk.nashorn.*`) so it can run on Java 17+.

If Gradle fails to resolve dependencies (e.g. ProtocolLib) due to network issues, retry on a machine with stable access to the configured repositories, or mirror the dependency to your own Maven repo.

If you hit `repo.extendedclip.com` / `repo.helpch.at` connection resets while resolving `ProtocolLib`, update `build.gradle.kts` to restrict the PlaceholderAPI repo content and then retry with:

```bash
./gradlew --no-daemon --refresh-dependencies clean shadowJar
```

If your network cannot reach Maven repos reliably, use the local-jar fallback added in `build.gradle.kts`:

- Download ProtocolLib jar and place it at `libs/ProtocolLib-<version>.jar` (default version is `5.1.0`)
- Then run the build again (it will skip remote resolution for ProtocolLib)

Note: the project now resolves ProtocolLib as `net.dmulloy2:ProtocolLib` (see `gradle/libs.versions.toml`), which is typically served directly by `repo.dmulloy2.net`.

## 3) Enable metrics (evidence log)

In `plugins/ThePitPremium/custom.yml`:

```yaml
perf-metrics:
  enable: true
  interval-seconds: 60
```

Restart the server.

Expected console log format every 60s:
- `[PerfMetrics] last 60s`
- `timer scoreboard.assemble.tick ...`
- `timer npc.profileLoaded ...`
- `timer npc.textUpdate ...`
- `count scoreboard.assemble.players ...`

## 4) After patch timings

Repeat the baseline timings procedure and compare:

Expected improvements (from this report’s hotspots):
- `ThePitPremium::Event: cn.charlotte.pit.npc.NpcFactory (PitProfileLoadedEvent)` should drop sharply (NPCs are created once; per-player work reduced).
- `ThePitPremium::Task: null (Single)` should decrease if it was dominated by NPC text updates / NPCLib main-thread sync tasks.
- `AssembleThread` spikes should flatten (spread-updates prevents updating all online players in one run).

## 5) Rollback switches (no code revert needed)

All switches live in `custom.yml` (restart required):

```yaml
# Disable periodic perf logs
perf-metrics:
  enable: false

perf:
  # Roll back scoreboard spreading (update all players each run)
  scoreboard:
    spread-updates:
      enable: false

  # Roll back NPC optimizations (legacy behavior)
  npc:
    optimize:
      enable: false
```

If rollback switches solve an issue, the next step is to isolate which switch caused it by re-enabling one by one.
