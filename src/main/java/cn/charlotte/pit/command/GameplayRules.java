package cn.charlotte.pit.command;

import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.medal.impl.challenge.hidden.RuleReaderMedal;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import xyz.upperlevel.spigot.book.BookUtil;

/** Player-facing overview; individual items and menus contain their exact values and conditions. */
public final class GameplayRules {
    private GameplayRules() {}

    public static void show(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        if (!profile.isLoaded()) return;
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("天坑玩法规则");
        meta.setAuthor("Remiaft");
        meta.addPage(
                "§l战斗与伤害§r\n\n1❤ = 2点生命值。\n\n普通伤害受护甲及减伤影响；真实伤害可被真伤抗性或平面镜抵挡；必中伤害无法以这些方式免疫。\n\n命中次数按有效攻击累计，近战与箭矢分别计算。",
                cn.charlotte.pit.reward.EconomyV2.active()
                        ? "§lBot 成长收益§r\n\n公共与私人 Bot 收益随精通、有效训练量和剩余额度变化，并有小幅浮动。金币、经验与助攻共用同一只 Bot 的浮动系数。\n\n训练基准为当天真人基础收益的一半，不叠加真人专属收益加成。"
                        : "§lBot 基础收益§r\n\nBot 指公共模拟玩家和军需处训练目标。\n\nBot 击杀与助攻只发放当天真人基础金币、经验的半倍；不叠加新手、附魔、天赋、连杀或山丘收益加成。\n\n助攻按伤害占比分配。",
                "§lBot 的其他规则§r\n\nBot 击杀保留击杀、连杀、任务进度和神话掉落资格。\n\n赏金、额外命中金币经验及活动计分排除 Bot。\n\nBot 助攻不触发连杀助理或助攻浓汤。死亡结算型奖励按已达成连杀另行结算。",
                "§l治疗道具§r\n\nBot 掉落的治疗道具会标注来源：保留治疗与药水效果，但不发放食用时的额外金币或经验。\n\n治疗量受禁疗、治疗削减和生命上限影响。\n\n不同治疗天赋的搭配限制请查看天赋说明。",
                "§l收益日历§r\n\n真人击杀、助攻倍率：\n平日 1倍\n周末 2倍\n普通法定假期 3倍\n春节、国庆假期 5倍\n\n假期优先。新手加成另按当前成长提示计算。" +
                        (cn.charlotte.pit.reward.EconomyV2.active() ? "满级后可储存下轮10%的经验；P50满级后继续积累荣誉。" : "达到本精通等级上限后不再增加经验。"),
                "§l挑战任务§r\n\n同一目标在一单任务中只计一次，Bot 可计入。\n\n任务奖励类型在接取时确定。每完成一单消耗一次额度，每获得一块暗聚块再消耗一次（包含任务大师）。\n\n结束后点击任务菜单结算或放弃，解除限制。",
                "§l活动与结算§r\n\n公共活动计分排除 Bot 和军需处训练。大型活动期间不增加常规连杀。\n\n奖励以实际到账提示为准。消耗物品生命的效果必须满足说明中的剩余生命要求。\n\n交易空间不足会取消并退回物品、金币。"
        );
        if (cn.charlotte.pit.reward.EconomyV2.active()) meta.addPage(
                "§l数值2.0 · 成长§r\n\n精通开放至P50。金币与经验同时推进；购物不减少累计获金，交易及退款不增加进度。\n\n/pit progress 查看成长、周目标、构筑和训练记录。",
                "§l训练与重复对练§r\n\n军需处提供独立成长收益，血量与装备只调整训练难度。正常训练使用完整份额，高频超出部分逐步递减。\n\n公共 Bot 及低互动对练按有效伤害折算。战斗效果保留。",
                "§l连杀与会员§r\n\n连杀金币、经验按整段真人贡献结算，整件奖励按有效训练贡献积累。超速死亡结算本次生命基础击杀收益的20%。\n\n会员、契约及圣契保留原价格和已购权益；预设及记录可在成长面板使用。"
        );
        book.setItemMeta(meta);
        BookUtil.openPlayer(player, book);
        new RuleReaderMedal().addProgress(profile, 1);
    }
}
