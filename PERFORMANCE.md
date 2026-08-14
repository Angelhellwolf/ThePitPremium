# ThePitPremium 卡顿排查/事件处理（Timings/Spark 全命令版）

> 目标：用**可复制执行的完整命令**采集证据、定位到具体模块/事件，然后验证修复是否生效（TPS/单 tick 耗时下降）。

## 1. 采集 Timings（Spigot/Paper）

> 建议在**玩家数接近卡顿时的在线量**、并保持相同地图/相同玩法压力下采集，结果才可对比。

### 1.1 开始采样（控制台或 OP 玩家执行）

依次执行：

```text
/timings on
/timings reset
```

然后让服务器在正常玩法下运行 **3~10 分钟**（越稳定越好）。

### 1.2 结束并上传报告

依次执行：

```text
/timings paste
/timings off
```

`/timings paste` 会输出一个链接（类似 `https://timin.gs/?id=...`）。  
把链接发我，或直接把报告页面里以下几段复制出来发我也可以：

- `Minecraft::Full Server Tick`（总耗时）
- `Plugins`（插件列表中谁最重）
- `Timed Tasks` / `Scheduler`（定时任务）
- `Events`（事件：尤其是 `PlayerMoveEvent` / `EntityDamageEvent` 等）

### 1.3 二次对比（修复前/修复后）

修复后，用同样流程再做一次：

```text
/timings on
/timings reset
# 等 3~10 分钟
/timings paste
/timings off
```

对比两份报告中 `Plugins` 与 `Events` 的占比变化即可。

## 2. 采集 Spark（可选，但更精准）

> 如果你装了 `spark`（推荐），用它能直接看到热点方法栈。

### 2.1 CPU 采样（60 秒）

```text
/spark profiler --timeout 60
```

结束后 spark 会给一个链接，把链接发我。

### 2.2 只看服务器主线程（更贴近 TPS）

```text
/spark profiler --timeout 60 --thread server
```

## 3. 本仓库已做的性能修复点（对应常见 Timings 热点）

> 这些改动不依赖你提供的报告内容，属于“高概率致卡”点的直接优化/修正。

### 3.1 BossBar 重复实例/重复任务修复

- `BossBarHandler` 之前同时满足：
  - `@AutoRegister`（会被 `ThePit.loadListener()` 反射创建并注册）
  - `ThePit.initBossBar()` 又 `new BossBarHandler()` 一次
- 结果：**至少会产生 2 个 BossBarHandler 实例**，并各自启动定时任务，造成不必要的重复工作（也可能导致 BossBar 显示异常）。

修复方式：
- `BossBarHandler` 不再 `@AutoRegister`，只保留 **ThePit 手动创建的单例**，并在 `initBossBar()` 里明确 `registerEvents`。
- 移除 `PlayerMoveEvent` 触发的“每移动一次就更新一次 BossBar”逻辑，改为低频定时刷新（减少 MoveEvent 压力）。

### 3.2 计分板（Scoreboard/Assemble）更新降耗

- 减少重复创建/重复查找：缓存 `Scoreboard` 与 `Objective`
- 更新时做“变更检测”：**行文本和 score 都没变则跳过**，避免每次 tick 都 setPrefix/setSuffix/setScore
- 修正多余的 `getLines()` 重复调用与无意义的 `setup()` 反复执行

### 3.3 移动处理（PlayerMoveHandler）热点降频

移动回调通常非常高频；本次做了三点降耗：

- 未跨方块坐标（BlockX/Y/Z 未变化）时**直接返回**，避免每个移动包都跑完整逻辑
- `BlockUtil.isBlockNearby(..., 3)`（最多 343 次方块查询）加入 **250ms 冷却**，避免每步都扫一圈
- “查端/清档提示书”只弹一次（metadata 标记），避免每次移动都触发 UI 逻辑

## 4. 构建与上服验证（完整命令）

### 4.1 可选：降低计分板更新频率（强烈建议）

本次已新增 `custom.yml` 配置项（默认 10 tick = 0.5 秒）：

```yaml
scoreboard-update-ticks: 10
```

如果在线人数很多、Timings 仍显示 `AssembleThread` 占比高，可改成：

```yaml
scoreboard-update-ticks: 20
```

改完重启生效（不要 `/reload`）。

### 4.2 构建

在仓库根目录执行：

```bash
./gradlew clean shadowJar
```

产物默认在：

```text
build/libs/ThePitPremium-3.93.jar
```

把该 JAR 替换到服务器 `plugins/` 后重启（不要 `/reload`），然后按第 1 节重新采 Timings 对比。

## 5. 你需要再发我什么（为了精确对症）

由于我这边目前**无法直接抓取 timin.gs 页面内容**（工具限制），请你从报告页面把下面内容贴出来（复制文本即可）：

1) `Minecraft::Full Server Tick` 下的 Top 5  
2) `Plugins` 下的 Top 10（带百分比/毫秒）  
3) `Events` 下的 `PlayerMoveEvent` / `PlayerInteractEvent` / `EntityDamageEvent`（如果有）  
4) `Timed Tasks` Top 10  

我拿到这 4 段，就能把优化继续精确到“哪一个事件/哪一个 Runnable/哪一段逻辑”。
