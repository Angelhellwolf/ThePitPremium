**天坑经验全链路与 bot 误判审计 — 2026-09-05**

> 这是修复前的审计快照。后续代码已修改，下文的执行顺序、状态和行号不代表修复后的版本。已落实的修复、新手规则及验证结果见 [修复说明](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/REPAIR_NOTES_2026-09-05.md)。

审计对象：ThePitPremium 当前工作区的 Java、Kotlin、资源 JS、配置和本地 Citizens 依赖。用户反馈：真人被当作 bot，经验异常缩水。

结论：最贴近反馈的原因是 **把“受害者没有近 30 秒有效输出记录”直接用作低奖励依据**，再叠加重复目标限制和几处奖励结算错误。未发现普通真人档案被 setBot(true) 的源码证据；NPC 身份标记与低活跃惩罚是两套机制。

本报告按审计时的工作区（包含当时已有未提交改动）分析。审计阶段没有比对线上 JAR、实际运行配置或玩家日志，也没有修改业务代码或部署；后续修复另见上方说明。标注“确定”的项目有源码或依赖字节码证据；金额示例是公式复核，不是在线实测。

**1. 与真人经验缩水直接相关的发现**

| 优先级 | 发现 | 触发条件与影响 | 证据 |
|---|---|---|---|
| P1 | 缺少输出记录直接降奖，不能据此认定 bot | 新登录、30 秒没命中玩家、只逃跑/挨打的受害者被判 inactive；移动、受到伤害、击杀本身均不更新记录。影响的是击杀此人的玩家及助攻者，不是按击杀者自己的活跃程度算 | [登录清记录](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:103)、[活动记录](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:149)、[判定](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1625) |
| P1 | 战斗退服先清证据，后结算 | onQuit 先 remove(lastDamageDealtAt)，再 handlePlayerDeath；只要走这条退服死亡结算，受害者必判 inactive，即使上一刻刚打出伤害 | [退出处理](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:260) |
| P1 | 正常伤害来源记录不全 | 只记录“Player 受害者 + Player/玩家投射物攻击者 + 正伤害”。TNT 分支不写记录；PlayerUtil 带 attacker 的独立伤害方法实际丢弃来源，部分真伤直接扣血。因此仅靠这些来源作战也不能维持活跃 | [近战记录](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:190)、[投射物记录](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:202)、[TNT 分支](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:208)、[独立伤害](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/util/PlayerUtil.java:156)、[来源丢失](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/util/PlayerUtil.java:195) |
| P1 | 普通助攻也消耗重复击杀资格 | 击杀、助攻调用同一 trackRepeatKill(奖励者 UUID, 受害者 UUID)。同一目标先助攻四次再击杀，已是第五次；没有最低贡献门槛，先计数再算伤害比例 | [共同计数器](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:434)、[助攻计数](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:952)、[后算占比](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1044) |
| P1 | 助攻全局倍率只显示、未入账 | 先 setExperience，再 totalXp *= eventBoost；仓库 3 倍设置下，提示可能是实际到账的三倍 | [助攻入账](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1089)、[提示才乘倍率](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1095) |
| P1 | 击杀事件修改奖励晚于入账 | PitKillEvent 在经验、金币发完之后触发；事件修改后的值用于后续事件监听、回放和消息。山丘之王的 4 倍经验/金币、黄金头盔的 10% 经验都不会由这条链补发差额 | [击杀入账](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:585)、[后发事件](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:620)、[山丘倍率](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/events/impl/KingOfTheHillEvent.java:112)、[头盔倍率](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/item/type/GoldenHelmet.kt:49) |
| P1 | 击杀者反刷资格影响死者自己的死亡奖励 | progressionEligible=false 时跳过整个 handleGameEffect，里面包含受害者死亡回调。死者满足条件的超速传动 +4000、月球经验返还也被跳过；之后死者连杀仍清零 | [整个回调被跳过](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:526)、[受害者回调](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1453)、[超速死亡奖励](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/nonpurchased/OverDriveMegaStreak.java:174)、[月球死亡奖励](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/tothemoon/ToTheMoonMegaStreak.kt:197) |
| P1 | 致命真伤会把助攻贡献分母放大 | 为让 Bukkit 处理死亡，事件伤害被写成剩余生命+10000；MONITOR 把最终事件伤害直接累计，分母含击杀者这一笔巨量伤害，其他真人助攻趋近零。非致命真伤直接扣血也未完整计入贡献 | [改为巨量致命伤害](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/GameEffectListener.java:501)、[累计贡献](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:286)、[总贡献](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:824)、[比例](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1044) |
| P2 | 缩水原因不可追溯 | 目前故意不显示反刷细节，且没有每笔经验来源/政策/到账记录。仅有奖励消息或死亡回放无法证明实际到账，回放也可能使用事件修改后的值 | [隐藏原因](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1642)、[回放金额](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:626) |

不应把这些情况统称为“bot 检测通过/失败”。当前 inactive 判定只是一条很窄的输出计时规则：会误伤真人，也不能可靠识别会定时攻击的自动账号。

**2. bot 身份、低活跃、重复目标的实际关系**

| 分类 | 实际判定 | 奖励政策 |
|---|---|---|
| 本插件管理的 Citizens bot | PitBotManager 的 botsByEntity 包含该实体 UUID；档案另外维护 bot=true | CombatListener 直接选择 FULL_REWARD，跳过重复目标和低活跃判定，仍进入普通击杀计算 |
| 有普通档案的 Player | Player 类型且 profile.bot=false | 检查重复 UUID 对，再检查受害者最近一次指定输出时间 |
| 新登录/无输出记录的真人 | lastDamageDealtAt 中没有记录 | 立即 inactive，没有观察期 |
| 30 秒未打出指定伤害的真人 | 距上次记录 >= after-seconds | inactive；跑动、挨打、逃跑不改变结果 |
| 外部 Player 型机器人 | 没有通用外部机器人识别逻辑 | 如果有普通档案，就按上一类 Player 处理；本次未确认实际存在这种部署 |
| MythicMob | 独立监听中的已配置 mob | 随机经验直接入档案，不经过 CombatListener |

证据：[管理器身份](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/bot/PitBotManager.java:136)、[档案身份](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:1818)、[击杀分类](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:480)、[助攻分类](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:952)、[MythicMob 独立路径](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/MythicMobListener.kt:15)。

全仓 setBot(true) 只见于管理器的生成、AI、重生。两套 bot 身份没有统一解析入口。BotHandlerImpl 是未被调用的空壳，不能当作正在运行的另一套 bot 服务。

**3. 击杀经验的完整执行顺序**

~~~mermaid
flowchart TD
    A["玩家死亡 / 战斗退服"] --> B["handlePlayerDeath：归属与去重"]
    N["管理器 bot 致死入口"] --> C["handleKill"]
    B --> C
    C --> D["受害者分类 → 重复计数 → inactive 判定"]
    D --> E["更新击杀与连杀；触发连杀事件"]
    E --> F["基础 XP = 10 + 精通 × 0.5"]
    F --> G["符合 progression 才执行天赋、附魔、JS、死者回调"]
    G --> H["固定奖励 / 连杀 / 等级差 → 全局 booster"]
    H --> I["重复倍率 → inactive 折扣或活跃保底 → 宵禁"]
    I --> J["写入档案经验；刷新等级"]
    J --> K["任务进度/掉落等后续处理"]
    K --> L["PitKillEvent：此时可改金额，但已入账"]
    L --> M["消息、死亡回放"]
    B --> P["死亡后半段：助攻结算、清理、重生"]
~~~

入口在 [普通死亡](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:276)、[死亡处理](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:657)、[bot 致死](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/bot/PitBotManager.java:582)。

按源码进一步展开：

1. 普通死亡入口使用 processingDeaths 去重；识别直接 killer，必要时读最后伤害来源或 TNT shooter。
2. handleKill 先分类和计数。progressionEligible 只取决于同一 UUID 对的累计次数是否 <=4，不是 bot 身份结果，也不包含 inactive 状态。
3. 非宵禁时先请求 streakKills+1，kills 始终+1。这一步在 progression gate 外，会提前触发 PitStreakKillChangeEvent 和直接发经验的连杀奖励；大型活动可取消连杀事件，从而阻止这次实际累加。
4. 创建基础经验 10+0.5×击杀者精通。
5. 有 progression 资格才执行击杀者天赋、手持/裤子附魔（含 JS 回调）、受害者死亡天赋/附魔、Genesis 阵营加成。
6. 再加 XPPrestigeBoost 的等级值；己方连杀<=3加4；终结恰好10倍数连杀的目标加目标连杀/10×5；己方恰好10倍数连杀时加有上限的连杀经验（Sweaty 改上限/倍率）；再算精通差/等级差。
7. 整体乘 eventBoost。第五次及以后跳过上述加成计算，仅基础经验×eventBoost。
8. 乘重复倍率。inactive 目标再乘 max(0,inactiveMultiplier)/eventBoost（eventBoost>0时）；活跃普通 Player 目标则保底到去掉全局增益后的金额。保底不会恢复第五次起被跳过的天赋/附魔等加成。
9. 宵禁启用且在配置时段时，再×0.01；击杀者已120级则本次击杀 totalXp=0。
10. 写经验、金币并刷新等级，然后执行合约、恢复物品、掉落等后续逻辑，最后才派发 PitKillEvent。
11. 有些连杀/死亡奖励在前后步骤直接写档案，并不属于 totalXp；合约则在任务完成后点击领取时另行写档案。因此它们不一定出现在本次击杀消息，也不统一受倍率和折扣控制。

精通/等级差算法见 [完整计算](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1486)：

- 正精通差×7。
- 目标比击杀者高至少30级时，加等级差/30×3。
- 目标<=10级时去掉全部上述差距加成；<=30级时只留25%；<=60且比击杀者等级低时只留50%。
- “高阶装备差异 +10”存在互相矛盾的条件：外层 hasPremiumItem(killer)，内层 !hasPremiumItem(killer)，所以这条击杀加成永远进不去（1564-1567）。助攻版本却比较受害者与助攻者，会生效。

重复策略见 [trackRepeatKill](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:434)：

| 同一奖励者→受害者的计数 | 重复倍率 | progression |
|---|---:|---|
| 1 | 1.00 | 允许 |
| 2 | 0.75 | 允许 |
| 3 | 0.50 | 允许 |
| 4 | 0.25 | 允许 |
| >=5 | 0.05 | 禁止 |

计数同时包含助攻。并非“严格统计最近10分钟的击杀数”：只有距该 UUID 对上次计数超过10分钟才归零，每次又刷新 lastKillAt。持续每几分钟遇到同一人，计数可一直增加。缓存为单服内存，重启消失；退出只清活动记录，不清这组配对计数。

**4. 可核算的真人缩水示例**

[仓库资源配置](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/resources/config.yml:203) 为 booster=3.0、after-seconds=30、inactiveMultiplier=0.5、curfew.enable=false。运行时 eventBoost 还可能由 NewConfiguration 覆盖，需以运行值为准（[加载路径](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/config/NewConfiguration.kt:104)）。

假设：击杀者0精通、从0连杀开始，无天赋/附魔/活动/等级差加成，目标无终结连杀奖励，未到120级；持续击杀同一普通玩家且没有助攻插入。

| 第几次 | 当次未乘全局的主链经验 | 目标被判活跃：主链到账 | 目标被判 inactive：主链到账 |
|---|---:|---:|---:|
| 1 | 14 | 42 | 7 |
| 2 | 14 | 31.5 | 5.25 |
| 3 | 14 | 21 | 3.5 |
| 4 | 10 | 10（活跃保底） | 1.25 |
| 5 | 10（仅基础） | 10（活跃保底） | 0.25 |

第一笔从42降到7，就是6倍差距；第五笔可能仅0.25。此表只统计主链，不包含其他直接奖励。数字已按当前公式计算复核。

特别注意：

- 这里检查的是“被你击杀的人”是否活跃。因此你自己一直在战斗，也可能拿到低奖励。
- 把 multiplier 改成1不能完全恢复全局3倍，它会变为未加全局的1倍；after-seconds=0会使所有目标立即符合时间条件，不能当作关闭开关。
- 增大 after-seconds 不能修复新登录无记录、退服先删记录、助攻倍率只显示这些错误。
- 宵禁如果开启是额外×0.01，并使用 JVM 默认时区；当前时段条件 start<=hour<=end 也不支持跨午夜的 start>end 配置。仓库默认关闭。

**5. 助攻经验链路与击杀的差异**

普通死亡后半段读取受害者 DamageMap，保留最近30秒未过期记录，求全部伤害之和，排除最终击杀者后逐个发助攻。

~~~mermaid
flowchart LR
    A["最近30秒伤害记录"] --> B["按奖励者与受害者累计重复次数"]
    B --> C["基础经验 → PitAssistEvent"]
    C --> D["连杀/差距/装备加成 → 天赋和附魔"]
    D --> E["伤害比例 × 重复倍率 × inactive × 宵禁"]
    E --> F["经验入账与回放"]
    F --> G["全局倍率只乘消息"]
~~~

证据：[贡献汇总](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:814)、[助攻计算](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:934)。

与击杀的差异：

- PitAssistEvent 在计算和入账前，可以实际影响到账；PitKillEvent 在入账后。
- 助攻没有活跃目标的1倍保底，重复倍率直接降到5%。
- 助攻的 inactive 直接乘配置0.5，不做 booster 抵消，因为其主链根本没在入账前应用 booster。
- 助攻有连杀与正等级/精通差加成，但没有击杀路径的目标低等级折减、XPPrestigeBoost固定值、Sweaty 连杀上限扩展等相同处理。
- 助攻统一回调直接调用手持/裤子附魔，与击杀所用的 GameEffectListener.processKilled 入口不同。
- 伤害总和从 double 转成 long 再作为分母，造成小额贡献比例偏差；截断后<=1会直接不结算。
- 原始事件伤害未按目标实际剩余生命裁剪，致死真伤的+10000是最明显的贡献污染。
- 符合资格时，“连杀助手”给 floor(100×比例)/100 的连杀；资格外的助攻不增加 assists。直接击杀却无论资格都增加 kills 与连杀，统计口径不一致。
- 助攻经验无击杀路径的“已120级则先置0”分支，而是在刷新时截断。

**6. 经验加成与特殊改写点**

以下是入口自身的公式，x指该回调收到的当前经验。加法和乘法交错发生，不能把所有倍率相乘后统一加固定值。执行顺序由注册顺序和事件时机决定。

| 来源 | 当前经验变化 | 作用范围/条件 | 证据 |
|---|---|---|---|
| XPBoost | x×(1+0.1×等级) | 击杀、助攻；读取选中天赋等级 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/boost/XPBoostPerk.java:92) |
| XPContractBoost | x×(1+0.015×等级) | 击杀；解锁等级 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/boost/XPContractBoostPerk.java:90) |
| ElGato | x+5 | 当前连杀数<=选中天赋等级时的击杀，并非每日额度 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/boost/ElGatoBoostPerk.java:91) |
| XPPrestigeBoost | x+解锁等级 | 主链固定追加 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1490) |
| BeastMode | x×1.75 | 达到50连杀的击杀 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/beastmode/BeastModeMegaStreak.java:89) |
| OverDrive | x×2 | 达到50连杀的击杀 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/nonpurchased/OverDriveMegaStreak.java:132) |
| Hermit | x×(1+min(0.75,floor((连杀-50)/10))) | >=50连杀；实现从60连杀起立即+75%，与每10杀+5%的文案不符 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/hermit/HermitMegaStreak.java:142) |
| ToTheMoon | x×1.2+随机整数[1,99] | 达到100连杀的击杀；另缓存事件经验用于死亡返还 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/tothemoon/ToTheMoonMegaStreak.kt:155) |
| SuperStreaker | 击杀 x×(2+a)+50；助攻事件 x×(1+a)+50，a=min(连杀/10×0.05,0.5) | >=10连杀；击杀使用 getAndAdd 多加了一个x，且>=10后每次都触发，与“每10连杀一次”的文案不符 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/tothemoon/SuperStreaker.kt:73) |
| CoopCat | x+0.5 | 助攻，之后再乘贡献比例；文案为+50%，加固定0.5与文案不符 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/prestige/CoopCatPerk.java:83) |
| Wisdom | x×(1+0.1×附魔级) | 击杀、助攻 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/normal/WisdomEnchant.java:57) |
| XpBump | 击杀x+2×级；助攻x+级 | 手持/裤子回调 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/normal/XpBumpEnchant.java:52) |
| PitMBA | x×(1+0.1×附魔级) | 周围15格人数加Sybil虚拟人数>=5时 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/normal/PitMBAEnchant.java:59) |
| Lycanthropy | x×1.1 | 游戏夜间的击杀和助攻；与JVM时钟的宵禁规则不同 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/dark_normal/LycanthropyEnchant.java:73) |
| Worm | x×1.2 | 下水道区域击杀 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/sewer_normal/WormEnchant.java:79) |
| Sweaty | 扩大每10连杀经验上限，再×(1+0.2×级) | 见主链；基础上限20，II额外50，III额外100，取手持/裤子较高级 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1511) |
| GrimReaper | 异步写成 -0.8×x | 文案是奖励-80%，实现却为负数，且异步修改可能赶不上主线程读取，可能被忽略或生成负奖励 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/dark_normal/GrimReaperEnchant.kt:43) |
| Genesis Angel | x×(1+0.01×boostTier) | 击杀回调后，代码此处检查队伍/等级 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1476) |
| 全局 booster | 主链×eventBoost | 击杀实际生效；助攻目前只影响消息 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:1576) |
| KingOfTheHill | PitKillEvent.exp×4 | 事件晚于入账，当前只改后续展示/事件值 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/events/impl/KingOfTheHillEvent.java:112) |
| GoldenHelmet | PitKillEvent.exp×1.1 | 佩戴黄金头盔；事件晚于入账，当前不补发差额 | [实现](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/item/type/GoldenHelmet.kt:49) |
| JS hsr_harmony_track | x+3×级 | 被加载且激活该附魔时的击杀回调 | [脚本](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/resources/js/hsr_harmony_track.js:15) |
| JS hsr_harmony_reprise | x+4×级 | 脚本条件满足时的击杀回调 | [脚本](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/resources/js/hsr_harmony_reprise.js:16) |
| XPStack | 设计为事件x+xpStackAddon | 当前未加入实际天赋列表，且类无AutoRegister；应归为休眠代码，不能解释当前正常运行奖励 | [代码](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/tothemoon/XPStack.kt:70)、[天赋列表](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/PitHook.kt:629) |

JS killed/assist/be_killed 接收同一组可变奖励参数（[绑定](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/js/JSHandler.kt:259)），也没有独立的奖励来源模型。仓库脚本可以静态核对，运行目录是否有额外自定义 JS 尚未检查。

**7. CombatListener 之外的全部直接发经验入口**

本次枚举出21处直接奖励写入点。共同特点：直接 setExperience 或 Kotlin experience +=；没有统一的 bot 过滤、全局倍率、反刷、奖励来源及事务入口。是否能触发仍取决于表内业务条件和上游调用。

| # | 来源 | 奖励与条件 | 代码 |
|---:|---|---|---|
| 1 | 快速数学 | 前5名答对者+100 | [QuickMath](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/events/impl/QuickMathEvent.java:109) |
| 2 | 蛋糕活动 | 吃黑色巧克力对应分支+20 | [Cake](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/events/impl/CakeEvent.java:236) |
| 3 | 龙蛋活动 | 首次clicks=0：3×随机[1,5]；以后clicks×0.5×随机[1,5]；clicks为活动全局点击计数 | [DragonEggs](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/events/impl/DragonEggsEvent.kt:159) |
| 4 | 管理员发放 | /giveItems 玩家 exp 数量；加传入整数 | [GiveItemCommand](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/addon/impl/GiveItemCommand.kt:42) |
| 5 | MythicMob | 配置 xp-provide 闭区间内随机值 | [MythicMob](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/MythicMobListener.kt:43) |
| 6 | 下水道箱 | 抽中经验分支+100 | [Sewers](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/runnable/SewersRunnable.kt:95) |
| 7 | 国王任务 | 0.30326×当前精通等级1..120所需经验之和；任务达标且消耗mini_cake | [KingsQuest](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/map/kingsquests/ui/button/KingsQuestsButton.kt:89) |
| 8 | 夜间合约 | 合约等级×250；需夜间任务配置及游戏时刻条件 | [夜间合约](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/quest/AbstractQuest.java:87) |
| 9 | 普通合约 | 合约等级×40 | [普通合约](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/quest/AbstractQuest.java:108) |
| 10 | 空投经验包 | +1000，每UUID每空投一次 | [空投](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/menu/pack/button/ItemButton.java:45) |
| 11 | 邮件领取 | +mail.exp；CDK等先生成邮件，最终在此到账 | [邮件](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/menu/mail/button/MailClaimButton.java:143) |
| 12 | ComboXp附魔 | meleeHit每5次触发：+12+8×附魔级 | [ComboXp](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/enchantment/type/normal/ComboXpEnchant.java:52) |
| 13 | Recon天赋 | bowHit每4次触发：+40，2秒冷却 | [Recon](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/prestige/ReconPerk.java:96) |
| 14 | Olympus药水 | 饮用perk_olympus药水+27 | [药水](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/PlayerListener.java:495) |
| 15 | 钓鱼俱乐部 | 等级>=2成功钓鱼+25；钻石剑特殊分支提前返回 | [钓鱼](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/prestige/FishClubPerk.java:242) |
| 16 | Explicious连杀 | 满足每3连杀跨界条件+12 | [Explicious](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/nonpurchased/ExpliciousKillStreak.java:104) |
| 17 | SecondGapple连杀 | 同类每3连杀跨界条件+5 | [SecondGapple](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/nonpurchased/SecondGappleKillStreak.java:107) |
| 18 | Apostle掷骰 | 每25连杀掷骰1..100，其中8倍数时+随机[500,5000]，对应12%分支概率 | [Apostle](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/grandfinale/ApostleForTheGesusKillStreak.java:199) |
| 19 | 冰立方 | 使用冰立方攻击Player的对应分支+40 | [IceCube](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/hermit/IceCubeKillStreak.java:129) |
| 20 | 超速传动死亡奖励 | 达到50连杀后，受害者死亡回调+4000 | [OverDrive](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/nonpurchased/OverDriveMegaStreak.java:177) |
| 21 | 月球死亡返还 | >=100连杀、无大型活动、缓存>0；返还min((1+(连杀-100)×0.005)×缓存,2×缓存) | [Moon](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/perk/type/streak/tothemoon/ToTheMoonMegaStreak.kt:211) |

补充关系：

- 连杀直接奖励是在 setStreakKills 派发的事件中触发。主链即便 progression=false 仍先增加连杀，所以“重复击杀只剩5%”并不意味着全部经验来源都只剩5%。
- ComboXp、Recon、IceCube 的 Player 类型检查不等于真人检查；Citizens 的 Player 型 bot 也满足。
- 月球缓存只收选中月球、>=100连杀且 progression=true 的 PitKillEvent.exp，不是期间获得的所有经验。缓存读取的是事件值，可能和真实到账不同；退出会清缓存。
- 任务经验在完成后点击领取时入账（[领取入口](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/menu/quest/main/button/QuestButton.java:206)），不受某次击杀的 inactive 折扣；主链 handleQuest 只更新任务进度且有 progression gate，任务体系也有伤害等其他更新路径。
- 邮件经验来源包括 [CDK兑换](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/command/PitCommand.java:605) 和 [登录固定奖励](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/FixedRewardData.kt:26)，均先生成邮件，再走表中第11项。CDK菜单的试领代码已注释，不算额外有效入口。
- 邮件/活动奖励、钓鱼、管理员奖励是否应有全局倍率属于产品规则，不能默认全部乘同一个 booster。

还有非奖励状态写入：管理员设置等级（[设置等级](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/command/PitAdminCommand.java:591)）、精通清零与FastPass到50级（[精通](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/menu/prestige/button/PrestigeStatusButton.java:71)）、debug新档初始满级（[debug初始化](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:253)）、显示刷新时120级截断（[刷新](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:605)）。它们应与“发放经验”分开记录。

**8. 入档案、升级、持久化链路**

等级使用当前精通的 experience 换算，上限显示120级。每升一级的门槛是 round(1.1^精通×该级基数)：0–9级为15，10–19为30，20–29为50，30–39为75，40–49为125，50–59为250，60–69为600，70–79为800，80–89为900，90–99为1000，100–109为1200，110及以上为1500。升到L级累计求和0..L-1，0精通升120级共65,450经验。见 [等级公式](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/util/level/LevelUtil.java:99) 与 [累计门槛](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/util/level/LevelUtil.java:156)。

实际链路为：调用方读取 experience → 算新值 → setExperience(newValue) → checkUpdate(newValue) → 写内存字段 → 某些调用方再 applyExperienceToPlayer → save时整份档案写Mongo。

确定问题：

- [setter](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:1022) 先 checkUpdate 再赋值；[checkUpdate](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:581) 先调用读取旧经验的 applyExperienceToPlayer。没有自行补一次刷新时，经验条可能滞后一笔，不能据经验条认定未到账。
- 120级上限在“刷新显示”时通过直接改字段实施，setter最终又可能写回newValue。主链各入口也不统一，无法保证所有写入时都稳定封顶。
- setter没有奖励来源、非负、NaN/Infinity检查，没有bot/倍率统一政策，也不自动持久化。
- [save](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/data/PlayerProfile.java:471) 才重算 totalExp=当前精通经验+历次精通满120级门槛，再 Mongo replaceOne/upsert。totalExp是进度换算字段，不是真实历史累计奖励流水。
- [退出保存](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/DataListener.java:148)、[停服保存](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/ThePit.java:283) 存在；本次全仓未见 saveAll 的定时调用，不能宣称每笔奖励或固定周期都会落库。
- [邮件领取](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/menu/mail/button/MailClaimButton.java:135) 在异步线程保存“已领取”邮件并直接修改经验/调用界面，且不在此保存档案。与主线程击杀同时读改写时存在丢更新风险；邮件状态已保存而经验未持久化时也有崩溃窗口。此为可见并发/持久化风险，未做故障注入复现。
- 管理员设置等级命令也声明 async=true，和普通战斗经验修改并发的风险需要一起收口。
- 国王任务奖励使用1..120求和，而标准满120级需求是0..119。若意图奖励满级需求的30.326%，这里多计第120档、漏计第0档；0精通会多给450.3411经验。蛋糕活动还把巧克力经验写进活动coins统计字段，属于奖励统计错误。

**9. 插件 bot 死亡分支的另组问题**

这些问题与真人 inactive 误判不同，但会影响完整结算：

- bot 致死入口直接调用 handleKill，用 rewardedDeaths 去重；普通 PlayerDeathEvent 使用 handlePlayerDeath 和 processingDeaths。两套入口不共享结算状态，bot直达路径也没有正常死亡后半段的助攻和清理。
- 本地 Citizens 依赖经 javap 确认：原生 EntityDamage 的 LOWEST 监听里同步派发 NPCDamageByEntityEvent。管理器的 NPC 回调会在原生 NORMAL 附魔修正之前提前判断致死和奖励。
- 随后管理器原生 HIGHEST 监听又 setCancelled(false)。若此前已经置为 respawning，handlePossibleLethal直接返回，刚拦下的伤害重新放行。
- “提前结算、取消状态被撤销”有源码/依赖证据；是否在当前服务器造成二次原生死亡和重复经验，需要真实 Citizens 环境复现，不能直接认定已经双发。

证据：[NPC伤害入口](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/bot/PitBotManager.java:526)、[原生伤害入口](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/bot/PitBotManager.java:553)、[致死处理](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/bot/PitBotManager.java:582)、[普通死亡入口](//wsl.localhost/Ubuntu-26.04/home/kiromorii/Remiaft/ThePitPremium/src/main/java/cn/charlotte/pit/listener/CombatListener.java:276)。

**10. 建议修复顺序与验收**

第一批先解决已确认的真人损失：

1. 给身份、活动证据、奖励反刷分别建状态。没有输出记录应表示“证据不足”，不要直接等价于bot。普通移动也不能单独证明真人；应综合业务参与证据，避免用另一条单一信号替换现有误判。
2. 先完成死亡结算，再清理退出玩家的活动记录；把伤害归属、实际造成伤害和活动记录统一到战斗结果入口，覆盖投射物、技能、真伤与间接伤害。
3. 统一“计算→事件修改→限制/封顶→一次入账→按实际到账显示”的顺序；击杀和助攻都从最终结果生成回放和消息。
4. 死者死亡奖励/清理与击杀者反刷资格分开；不要因击杀者的配对次数吞掉死者自己的收益。
5. 助攻贡献使用实际有效伤害，不记录致死处理所需的虚构10000伤害；重复击杀与低贡献助攻不要无差别共用次数。

第二批统一经验来源：

- 建立经验发放入口，带 source、sourceEventId、奖励者/受害者身份、基础值、加成、限制原因、申请金额、实际到账、发放前后经验。
- 用事件/奖励唯一标识防重复；所有玩家档案的加经验在同一受控线程/存储边界处理，修复邮件并发读改写及领取持久化窗口。
- 分开普通击杀计数与可领奖连杀进度，明确重复目标是否仍触发每3杀、每10杀、死亡返还等机制。
- 单独修复 GrimReaper负数/异步、SuperStreaker/CoopCat/Hermit公式、等级装备差条件、经验条先刷旧值、国王任务求和、休眠XPStack等问题。
- 日志供管理员按玩家和奖励ID查询；玩家消息只需准确显示实际到账及必要的简洁原因。

建议验收场景（本次为审计清单，尚未执行集成测试）：

| 场景 | 验收目标 |
|---|---|
| 新登录真人立即被杀 | 不因“尚无记录”直接按已识别bot降奖 |
| 逃跑/挨打超过30秒后被杀 | 按明确活动政策处理，能查询判定证据 |
| 仅用技能/TNT/独立真伤作战 | 正确归属，活动与贡献记录一致 |
| 刚互打后战斗退服 | 正常死亡与退出死亡的奖励政策一致 |
| 同一目标4次助攻后1次击杀 | 不被无差别当作5次击杀；规则有明确说明 |
| booster=3的普通助攻 | 前后经验差、消息、回放完全一致 |
| 山丘之王范围内击杀 | 事件倍率真正到账 |
| 目标被致命真伤击杀 | 其他玩家助攻不被虚构伤害挤到近零 |
| 被同一对手多次击杀后触发超速/月球死亡奖励 | 死者应得返还与击杀者配对限制解耦 |
| 119→120及已120级的所有奖励入口 | 封顶与实际到账统一，不能负数或非有限值 |
| 领取邮件同时发生战斗奖励 | 不丢更新，领取状态与奖励可恢复 |
| Citizens bot致死 | 统一结算一次；有明确的助攻/连杀/掉落政策 |

本次交付是经验链路审计和修复依据。当前源码足以确认上述主要缺陷；定位某名玩家某一笔线上损失，还需要匹配部署版本、运行时配置和前后经验记录。
