package cn.charlotte.pit.command;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.bot.PitBotManager;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.menu.supporter.SupporterMenu;
import cn.charlotte.pit.menu.supporter.button.SupporterProductButton;
import cn.charlotte.pit.supporter.SupporterPricing;
import cn.charlotte.pit.supporter.SupporterProduct;
import cn.charlotte.pit.supporter.SupporterService;
import cn.charlotte.pit.supporter.SupporterRuntimeCheck;
import cn.charlotte.pit.supporter.SupporterTier;
import cn.charlotte.pit.util.command.Command;
import cn.charlotte.pit.util.command.param.Parameter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SupporterCommand {
    @Command(names = "pitadmin bots", permissionNode = "pit.admin")
    public void bots(CommandSender sender) {
        PitBotManager manager = ThePit.getInstance().getPitBotManager();
        if (manager == null) { sender.sendMessage("PIT_BOTS unavailable"); return; }
        for (String line : manager.diagnostics()) sender.sendMessage(line);
    }

    @Command(names = {"pitsponsor", "pitsupporter"})
    public void open(Player player) { new SupporterMenu().openMenu(player); }

    @Command(names = "pitadmin commercecheck", permissionNode = "pit.admin")
    public void check(CommandSender sender) {
        if (sender instanceof Player) { sender.sendMessage("此检查仅可从服务器控制台执行。"); return; }
        try {
            for (String result : SupporterRuntimeCheck.run()) sender.sendMessage(result);
        } catch (Exception exception) {
            sender.sendMessage("SUPPORTER_SMOKE FAILED " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Command(names = "pitadmin commerce", permissionNode = "pit.admin")
    public void status(CommandSender sender) {
        SupporterService service = SupporterService.get();
        NewConfiguration config = NewConfiguration.INSTANCE;
        SupporterPricing pricing = config.supporterPricing();
        sender.sendMessage("SUPPORTER_COMMERCE revision=contracts-20260907 ready=" + (service != null && service.isReady())
                + " pointsPerYuan=" + SupporterPricing.POINTS_PER_YUAN + " durationDays=" + config.getVipDurationDays());
        for (SupporterProduct product : SupporterProduct.values()) {
            sender.sendMessage(product.getDisplayName() + " points=" + pricing.price(product)
                    + " yuan=" + SupporterPricing.yuan(pricing.price(product)));
        }
        for (SupporterTier tier : SupporterTier.values()) {
            sender.sendMessage(tier.name() + " drop=" + SupporterProductButton.percent(config.getSupporterDropChance(tier))
                    + " rare=" + SupporterProductButton.percent(config.getSupporterRareChance(tier)));
        }
        sender.sendMessage("luckyGem=" + SupporterProductButton.percent(config.getLuckGem()) + " legacyRatePermissions=false");
        sender.sendMessage(cn.charlotte.pit.reward.RareSupply.diagnostics());
    }

    @Command(names = "pitadmin supporterinfo", permissionNode = "pit.admin")
    public void info(CommandSender sender, @Parameter(name = "target") Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        sender.sendMessage(player.getName() + " supporter=" + profile.isSupporter() + " tier="
                + profile.getActiveSupporterTier().name() + " expiresAt=" + profile.getSupporterContractExpiresAt()
                + " accountLoaded=" + (profile.getSupporterAccount() != null));
        sender.sendMessage("drop=" + SupporterProductButton.percent(NewConfiguration.INSTANCE.getMythicDropChance(player))
                + " rare=" + SupporterProductButton.percent(NewConfiguration.INSTANCE.getChance(player, MythicColor.ORANGE)));
    }
}
