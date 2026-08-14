 # 事件配置命令文档

本服已支持通过统一命令配置事件，不需要再手动改 `config.yml`。

命令前缀：`/pitadmin eventcfg`

## 1. 通用命令

- `/pitadmin eventcfg`
  - 查看帮助
- `/pitadmin eventcfg keys`
  - 列出所有可配置键
- `/pitadmin eventcfg show <key>`
  - 查看当前值
- `/pitadmin eventcfg set <key>`
  - 将键设置为你当前位置
  - 若键是列表，会先清空再只保留当前位置
- `/pitadmin eventcfg add <key>`
  - 向列表键追加当前位置
- `/pitadmin eventcfg remove <key> <index>`
  - 删除列表键中第 `index` 个点（从 1 开始）
- `/pitadmin eventcfg clear <key>`
  - 清空单个键
- `/pitadmin eventcfg clearevent <event>`
  - 按事件组清空配置
- `/pitadmin eventcfg setint <key> <value>`
  - 设置整型键（例如 Rage 半径/高度）
- `/pitadmin eventcfg save`
  - 手动保存（大多数修改命令会自动保存）
- `/pitadmin eventcfg overwrite`
  - 仅清空 `loc.events.*`（安全，不会动 `spawn/portal/packages/squads/blockHead/Genesis`）
- `/pitadmin eventcfg overwriteall confirm`
  - 清空事件相关全部键（含全局依赖键，危险命令）

## 2. key 写法规则

- 支持两种写法：
  - 完整路径：如 `loc.events.rage.middle`
  - 简写别名：如 `rage_middle`
- key 不区分大小写。
- `-`、`_`、`.` 都可以混用，系统会自动归一化匹配。

## 3. 全局必配（多个事件共用）

以下键虽然不是单个事件名下，但被多个事件依赖：

- `spawn`（路径 `loc.spawn`，列表）
  - 被 `Hamburger`、`RagePit`、`Spire` 等事件用于传送
  - 配置示例：
    - `/pitadmin eventcfg add spawn`
    - `/pitadmin eventcfg show spawn`
- `portal_posa`、`portal_posb`（路径 `loc.portal.posA/posB`，单点）
  - 被 `Spire` 用于传送门区域
  - 配置示例：
    - `/pitadmin eventcfg set portal_posa`
    - `/pitadmin eventcfg set portal_posb`

## 4. 每个事件的配置方式

以下按 `PitHook.loadEvents()` 的事件顺序说明。

### 4.1 Patronage（`patronage`）

- 需要配置：无

### 4.2 Hamburger（`ham`）

- 必配键：
  - `hamburger_villager_a_offer`（发汉堡 NPC 点，单点）
  - `hamburger_villager_a`（配送村民刷新点，列表）
- 可配键：
  - `hamburger_shop`（当前代码未直接使用，可按需保留）
- 配置示例：
  - `/pitadmin eventcfg set hamburger_villager_a_offer`
  - `/pitadmin eventcfg add hamburger_villager_a`
  - `/pitadmin eventcfg show hamburger_villager_a`

### 4.3 RagePit（`rage_pit`）

- 必配键：
  - `rage_middle`（圆柱中心点）
  - `rage_radius`（半径，整型）
  - `rage_height`（高度，整型）
- 配置示例：
  - `/pitadmin eventcfg set rage_middle`
  - `/pitadmin eventcfg setint rage_radius 22`
  - `/pitadmin eventcfg setint rage_height 16`

### 4.4 RedVSBlue（`red_vs_blue`）

- 需要配置：无（无独立事件坐标键）

### 4.5 BlockHead（`block_head`）

- 必配键：
  - `blockhead`（Buff 刷新点列表，对应 `loc.blockHead`）
- 配置示例：
  - `/pitadmin eventcfg add blockhead`
  - `/pitadmin eventcfg remove blockhead 1`

### 4.6 Spire（`spire`）

- 必配键：
  - `spire_spireloc`（尖塔结构粘贴基准点）
  - `spire_spirefloorlocations`（各层中心点列表）
  - `portal_posa`、`portal_posb`（传送门区域）
- 依赖键：
  - `rage_middle`、`rage_radius`、`rage_height`（用于外围玻璃墙）
  - `spawn`（事件开始时传送）
- 配置示例：
  - `/pitadmin eventcfg set spire_spireloc`
  - `/pitadmin eventcfg add spire_spirefloorlocations`
  - `/pitadmin eventcfg set portal_posa`
  - `/pitadmin eventcfg set portal_posb`
- 建议：
  - `spire_spirefloorlocations` 至少配置 10 个点（对应楼层索引 0~9）

### 4.7 Auction（`auction`）

- 需要配置：无

### 4.8 Cake（`cake`）

- 必配键（4个区域，每个区域 posA + posB）：
  - `cake_a_posa`、`cake_a_posb`
  - `cake_b_posa`、`cake_b_posb`
  - `cake_c_posa`、`cake_c_posb`
  - `cake_d_posa`、`cake_d_posb`
- 配置示例：
  - `/pitadmin eventcfg set cake_a_posa`
  - `/pitadmin eventcfg set cake_a_posb`

### 4.9 CarePackage（`care_package`）

- 必配键：
  - `packages`（空投落点列表，对应 `loc.packages`）
- 配置示例：
  - `/pitadmin eventcfg add packages`
  - `/pitadmin eventcfg clear packages`

### 4.10 EveOneBounty（`everyone_bounty_event`）

- 需要配置：无

### 4.11 Sewers（`sewers`）

- 必配键：
  - `sewers_chests`（下水道箱子点列表，对应 `loc.Sewers.chests`）
- 配置示例：
  - `/pitadmin eventcfg add sewers_chests`
  - `/pitadmin eventcfg show sewers_chests`
  - `/pitadmin eventcfg clearevent sewers`

### 4.12 DragonEggs（`dragon_egg`）

- 必配键：
  - `egg_loc`（龙蛋初始中心点）
- 配置示例：
  - `/pitadmin eventcfg set egg_loc`

### 4.13 DamagePlus（`hunt`）

- 需要配置：无

### 4.14 QuickMath（`quick_math_event`）

- 需要配置：无

### 4.15 KingOfTheHill（`koth`）

- 必配键：
  - `koth_loc`（占山中心点）
- 配置示例：
  - `/pitadmin eventcfg set koth_loc`

### 4.16 Squads（`squads`）

- 必配键：
  - `squads`（旗帜点列表，对应 `loc.squads`）
- 配置示例：
  - `/pitadmin eventcfg add squads`
  - `/pitadmin eventcfg show squads`

## 5. 批量清理示例

- 清空某个事件：
  - `/pitadmin eventcfg clearevent spire`
  - `/pitadmin eventcfg clearevent rage`
  - `/pitadmin eventcfg clearevent cake`
  - `/pitadmin eventcfg clearevent ham`
- 仅清空 `loc.events.*`：
  - `/pitadmin eventcfg overwrite`
- 清空事件相关全部键（危险）：
  - `/pitadmin eventcfg overwriteall confirm`

## 6. 推荐配置流程

- 1. `/pitadmin eventcfg keys` 看可配置项
- 2. 逐个站位使用 `set/add`
- 3. 用 `show` 检查是否写入正确
- 4. 执行 `/pitadmin eventcfg save`
- 5. 用 `/event <internalName>` 逐个开事件实测

## 7. 兼容旧命令

旧命令（如 `addPackagePoint`、`addSquadsLoc`、`pitadmin setegg` 等）仍可用，但后续建议统一使用 `eventcfg`，便于维护和批量清理。
