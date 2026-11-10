# 过氧化物、黑裤与卑鄙之人反馈分析

修复状态：本报告记录修复前的发现。相关问题已完成源码修复和完整构建，最新结果见 [修复记录](DEFENSE_FIXES_2026-09-13.md)。

分析日期：2026-09-13。依据当前工作区源码、已注册附魔及本地复现测试。未核对现网正在运行的 JAR，也未进行真人对战；以下“已复现”指本地调用真实处理函数的诊断结果。本次未修改生产代码或部署。

## 结论

1. 黑裤确有附魔免疫漏判：箭库命中加成和爆炸弓的范围真伤均已复现绕过无尽黑暗。黑裤本身只有皮裤基础防御，这些漏判会明显放大其脆弱程度。
2. 卑鄙之人本身发放抗性 II 的逻辑正常，但反侦察和破釜沉舟 I 会主动删除它，换成更弱的抗性 I；两种组合均已复现。
3. 过氧的回血量、冷却、延迟回血和生命周期保护已有 10 项测试且本轮全部通过。另有触发覆盖缺口：部分范围真伤直接扣生命值，不发出它监听的受击事件。是否应覆盖这些真伤，需要结合本服对“受到攻击”的规则确定。

## 黑裤：基础防御和免疫分别如何处理

普通神秘裤与黑裤的物品材质都是皮革护腿。`GameEffectListener.modifyLeatherArmor` 对普通神秘裤额外减掉基础伤害的 12%，用于补足到铁护腿；`mythic_color=dark` 明确跳过此补偿。物品说明也分别写明铁护腿、皮革护腿防御。

受控测试输入为基础伤害 10、原护甲修正 -5.6，无其他修正。结果普通神秘裤受到 3.2 点伤害，黑裤受到 4.4 点伤害。主监听器再次以相同基础值调用 `setDamage` 后，普通裤的补偿仍然存在。

因此，截图中“新鲜裤与黑裤受伤一样”不足以判断护甲失效：实际对局还混有攻击附魔是否被禁用、天赋、真伤、药水和黑裤副附魔。控制其余条件后的本地护甲结果并不相同。

无尽黑暗实际由通用附魔可用性判断实现，其类自身的攻击回调为空不代表它没有生效。`PlayerUtil.isEquippingSomber` 检查裤子的 `somber_enchant` NBT，单纯染成黑色不产生免疫；`IMythicItem` 只在黑裤已附魔时自动补上无尽黑暗。

| 相互作用 | 当前规则 |
|---|---|
| 自己穿有无尽黑暗的黑裤 | 自己的非暗黑附魔被禁用 |
| 与黑裤玩家交战 | 这次交战的非暗黑附魔被禁用；附魔持有者穿终末之靴可绕过对方黑裤 |
| 暗黑附魔 | 不因无尽黑暗而失效；通用入口仍受毒液沉默和沉沦限制 |
| 天赋，例如卑鄙之人、吸血鬼 | 使用独立天赋派发，不由无尽黑暗统一禁用 |
| 普通武器伤害、天赋增伤 | 不会仅因为对方穿黑裤就消失 |
| “苦难”副附魔 | 每次受击还会对自己造成 0.3 颗心真伤 |
| “怨恨”副附魔 | 来自皮革装备玩家的伤害增加 5% |
| “怀旧”副附魔 | 普通战斗伤害倍率减少 0.10；不是对所有直接扣血都减免 |
| “血”、耀金、狼人 | 分别提供攻击回血、连击回血、夜间恢复等；黑裤没有统一附送这些效果 |
| “无谓的苦难” | 特定复活条件给予吸收心，同时禁止回血 |

实现位置：`GameEffectListener.java:135`、`IMythicItem.java:140`、`PlayerUtil.java:99`、`AbstractEnchantment.java:147`。

## 已确认的问题

| 问题 | 触发条件与实际结果 | 证据强度 |
|---|---|---|
| 箭库绕过黑裤免疫 | 射手箭上携带箭库 III 元数据，目标有无尽黑暗。通用规则判定该附魔失效，实际独立监听器仍把基础伤害从 10 改成 16 | 已复现 |
| 爆炸弓范围真伤绕过黑裤 | 箭射中普通目标，黑裤玩家在爆炸范围内。爆炸 III 直接把黑裤玩家从 20 生命扣到 17，且没有逐个检查附魔可用性 | 已复现，调用真实直接伤害服务 |
| 反侦察覆盖卑鄙之人 | 击杀派发先运行天赋、再运行附魔。卑鄙之人先给抗性 II 4 秒；反侦察 I 随后删除它，改给抗性 I 2 秒。反侦察更高等级同样只给抗性 I | 已复现两回调在源码派发顺序下的结果 |
| 破釜沉舟 I 覆盖卑鄙之人 | 玩家已获得抗性 II，低血量受击触发破釜沉舟 I，抗性被降成 I 3 秒 | 已复现 |
| 精神攻击漏掉沉默判断 | 通用可用性已因持有者中毒液而判为 false，独立监听器仍修改伤害。无附近皮革玩家时，10 点基础伤害仍被降到 4 | 已复现；影响黑裤附魔一致性，不是上述免疫失效的同一入口 |
| 斗士没有检查攻击者黑裤 | 减伤分支只检查防守者自己是否穿黑裤、中毒液，不检查攻击者。黑裤攻击者因此无法按通用规则禁用对方的斗士格挡减伤 | 源码确认，本轮未运行完整战斗监听器复现 |
| 恶魔小鸡、逃生舱范围伤害有同类漏判 | 遍历附近玩家后直接调用真伤服务，没有对每个受害者检查黑裤。真伤服务的 `canImmune=true` 处理真伤抗性、镜子等，未代替附魔可用性判断 | 源码确认；完整定时/逃生触发未单独复现 |

关键位置：

- 箭库：`src/main/java/cn/charlotte/pit/enchantment/type/normal/ArrowArmoryEnchant.java:114`
- 爆炸：`src/main/java/cn/charlotte/pit/enchantment/type/rare/ExplosiveEnchant.java:68`
- 恶魔小鸡：`src/main/java/cn/charlotte/pit/enchantment/type/rare/DevilChicksEnchant.java:71`
- 逃生舱：`src/main/java/cn/charlotte/pit/enchantment/type/rare/EscapePodEnchant.java:96`
- 斗士：`src/main/java/cn/charlotte/pit/listener/CombatListener.java:204`
- 击杀派发顺序：`src/main/java/cn/charlotte/pit/listener/CombatListener.java:1729`
- 反侦察：`src/main/java/cn/charlotte/pit/enchantment/type/normal/CounterJanitorEnchant.java:51`
- 破釜沉舟：`src/main/java/cn/charlotte/pit/enchantment/type/rare/FightOrDieEnchant.java:61`
- 精神攻击：`src/main/java/cn/charlotte/pit/enchantment/type/dark_normal/MindAssaultEnchant.java:58`

## 过氧化物逐项梳理

| 项目 | 当前行为 |
|---|---|
| 回血量 | I / II / III 分别为 0.5 / 0.75 / 1 颗心，实际传入回血服务为 1 / 1.5 / 2 点生命 |
| 冷却 | 所有等级均为 1500 毫秒；连续受击不叠加触发 |
| 结算时点 | 受击事件后延迟 1 tick 回血，避免满血时先回血、后扣伤害造成治疗浪费 |
| 有效命中 | 未取消且实际接受的伤害大于零；吸收心吸掉全部伤害也可触发 |
| 黑裤关系 | 依据过氧持有者与攻击者的附魔可用性判断；攻击者穿黑裤时，持有者没有终末之靴则不触发 |
| 生命切换 | 死亡、退出、插件自定义重生会使旧治疗任务失效；不能靠延迟任务跨生命回血 |
| 治疗限制 | 仍经过 `PitRegainHealthEvent`，受断流、不愈之毒、凝血等限制。比如断流 II 固定扣 2 点治疗，能把过氧 III 的一次治疗扣到零 |
| 真伤覆盖缺口 | `PlayerUtil.damage(...TRUE...)` 的非致死路径直接 `setHealth`，不会单独触发 `EntityDamageByEntityEvent`。范围真伤受害者可能没有过氧触发；若其同时也是原箭矢事件目标，则仍可能由原箭伤触发 |

本轮没有证据表明基础回血数值算错。玩家所说“不回血”可能来自黑裤/毒液压制、减疗、直接扣血路径或现网版本差异，不能在没有现场样本时归为同一个原因。

实现位置：`PeroxideEnchant.java:73`、`PlayerUtil.java:207`、`PlayerUtil.java:585`、`DryBlood.java:71`。

## 修复优先顺序

1. 补齐独立攻击监听器和每个范围受害者的附魔可用性判断，保留终末之靴例外及暗黑附魔规则。不要在通用真伤函数里一刀切禁用黑裤，因为天赋真伤、暗黑附魔和反射也使用它。
2. 统一有益抗性药水的刷新方式，防止反侦察、破釜沉舟及其他抗性来源删除更强药水；将已存在的 `CombatPotions.refresh` 用到这些来源。
3. 明确过氧是否覆盖所有玩家来源的直接真伤。若覆盖，应在伤害确认层统一派发，处理冷却、一次攻击多段伤害和死亡，避免补事件造成重复攻击或重复回血。
4. 上述问题修复并做固定装备对打后，再评估黑裤皮裤防御是否需要调整。仅提高黑裤护甲不会解决范围真伤漏判或抗性覆盖。

## 本轮验证

| 测试类 | 数量 | 结果 |
|---|---:|---|
| DefenseFeedbackAuditTest | 6 | 全部通过；断言复现当前缺陷和护甲差异，不表示缺陷已修复 |
| PeroxideEnchantTest | 10 | 全部通过 |
| MiseryCombatTest | 3 | 全部通过 |
| CombatPotionsTest | 5 | 全部通过 |
| 合计 | 24 | 失败 0、错误 0 |

诊断源文件及独立 Gradle init 文件位于 `build/feedback-defense-audit/`，属于临时构建资料。常规测试中的对象替身不等价于现网端到端测试。本轮覆盖上述反馈及关联路径，不代表已逐个重审全部附魔。

执行命令（WSL 仓库根目录）：

```sh
JAVA_HOME=/home/kiromorii/.sdkman/candidates/java/17.0.20-tem ./gradlew \
  -I build/feedback-defense-audit/audit.init.gradle test \
  --tests cn.charlotte.pit.audit.DefenseFeedbackAuditTest \
  --tests cn.charlotte.pit.enchantment.PeroxideEnchantTest \
  --tests cn.charlotte.pit.listener.MiseryCombatTest \
  --tests cn.charlotte.pit.util.combat.CombatPotionsTest \
  --offline --console=plain -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
```
