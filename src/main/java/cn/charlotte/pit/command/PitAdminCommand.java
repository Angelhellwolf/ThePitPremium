package cn.charlotte.pit.command;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.addon.impl.GachaPool;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.config.PitConfig;
import cn.charlotte.pit.data.PlayerInvBackup;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.TradeData;
import cn.charlotte.pit.data.sub.EnchantmentRecord;
import cn.charlotte.pit.events.EventsHandler;
import cn.charlotte.pit.events.impl.AuctionEvent;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.js.JSHandler;
import cn.charlotte.pit.medal.impl.challenge.hidden.KaboomMedal;
import cn.charlotte.pit.menu.cdk.generate.CDKMenu;
import cn.charlotte.pit.menu.cdk.view.CDKViewMenu;
import cn.charlotte.pit.menu.mail.MailMenu;
import cn.charlotte.pit.menu.perk.normal.choose.PerkChooseMenu;
import cn.charlotte.pit.menu.perk.prestige.PrestigePerkBuyMenu;
import cn.charlotte.pit.menu.prestige.PrestigeMainMenu;
import cn.charlotte.pit.menu.quest.main.QuestMenu;
import cn.charlotte.pit.menu.trade.ShowInvBackupButton;
import cn.charlotte.pit.menu.trade.TradeManager;
import cn.charlotte.pit.menu.trade.TradeMenu;
import cn.charlotte.pit.npc.AbstractPitNPC;
import cn.charlotte.pit.npc.NpcFactory;
import cn.charlotte.pit.runnable.RebootRunnable;
import cn.charlotte.pit.util.MythicUtil;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.chat.ChatComponentBuilder;
import cn.charlotte.pit.util.command.Command;
import cn.charlotte.pit.util.command.param.Parameter;
import cn.charlotte.pit.util.configuration.annotations.ConfigData;
import cn.charlotte.pit.util.configuration.annotations.ConfigSerializer;
import cn.charlotte.pit.util.configuration.serializer.LocationSerializer;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemBuilder;
import cn.charlotte.pit.util.item.ItemUtil;
import cn.charlotte.pit.util.level.LevelUtil;
import cn.charlotte.pit.util.menu.Button;
import cn.charlotte.pit.util.menu.menus.PagedMenu;
import cn.charlotte.pit.util.random.RandomUtil;
import cn.charlotte.pit.util.rank.RankUtil;
import cn.charlotte.pit.util.time.Duration;
import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import lombok.SneakyThrows;
import net.md_5.bungee.api.chat.ClickEvent;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

/**
 * 2 * @Author: EmptyIrony
 * 3 * @Date: 2020/12/28 23:18
 * 4
 */
public class PitAdminCommand {
    private final Gson gson = new Gson();
    private final DateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final List<UUID> requestDrop = new ArrayList<>();
    private final List<UUID> confirmDrop = new ArrayList<>();

    @Command(
            names = "setupPunchY",
            permissionNode = "pit.adin"
    )
    public void punchY(Player player, @Parameter(name = "punch") String punchY) {
        double parsed = Double.parseDouble(punchY);

        YamlConfiguration config = NewConfiguration.INSTANCE.getConfig();
        config.set("punch_y", parsed);
        NewConfiguration.INSTANCE.save();

        player.sendMessage("ok");
    }

    @Command(
            names = "gacha"
    )
    public void gacha(Player player) {
        GachaPool.INSTANCE.gacha(player);
    }

    @Command(
            names = "giveGacha",
            permissionNode = "pit.admin"
    )
    public void giveGacha(CommandSender player, @Parameter(name = "target") String name, @Parameter(name = "field") String field) {
        int amount = Integer.parseInt(field);
        if (GachaPool.INSTANCE.getEnable()) {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) return;

            GachaPool.GachaData data = GachaPool.INSTANCE.getKeysCollections().findOne(Filters.eq("playerName", target.getName()));
            if (data == null) {
                data = new GachaPool.GachaData();
                data.playerName = target.getName();
            }

            data.setKeys(data.getKeys() + amount);
            GachaPool.INSTANCE.getKeysCollections().replaceOne(Filters.eq("playerName", target.getName()), data, new ReplaceOptions().upsert(true));

            target.sendMessage(CC.translate("&a你现在拥有 &e" + data.getKeys() + " &a个钥匙"));
        }
    }

    @Command(
            names = "gachaPreview"
    )
    public void previewGacha(Player player) {
        if (GachaPool.INSTANCE.getEnable()) {
            GachaPool.Preview preview = new GachaPool.Preview();
            preview.openMenu(player);
        }
    }

    @Command(
            names = "checkGacha",
            permissionNode = "pit.admin"
    )
    public void lookGacha(CommandSender player, @Parameter(name = "target") String name) {
        if (GachaPool.INSTANCE.getEnable()) {
            GachaPool.GachaData data = GachaPool.INSTANCE.getKeysCollections().findOne(Filters.eq("playerName", name));
            if (data == null) {
                data = new GachaPool.GachaData();
                data.playerName = name;
            }

            player.sendMessage(name + " 有 " + data.getKeys());
        }
    }

    @Command(
            names = "checkMaxHealth",
            permissionNode = "pit.admin"
    )
    public void check(CommandSender sender, @Parameter(name = "tester") Player tester) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(tester.getUniqueId());
        sender.sendMessage("额外血量: " + profile.getExtraMaxHealth());
    }

    @Command(
            names = "setGacha",
            permissionNode = "pit.admin"
    )
    public void addGacha(CommandSender player, @Parameter(name = "target") String name, @Parameter(name = "field") String field) {
        int amount = Integer.parseInt(field);
        if (GachaPool.INSTANCE.getEnable()) {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) return;

            GachaPool.GachaData data = GachaPool.INSTANCE.getKeysCollections().findOne(Filters.eq("playerName", target.getName()));
            if (data == null) {
                data = new GachaPool.GachaData();
                data.playerName = target.getName();
            }

            data.setKeys(amount);
            GachaPool.INSTANCE.getKeysCollections().replaceOne(Filters.eq("playerName", target.getName()), data, new ReplaceOptions().upsert(true));

            target.sendMessage(CC.translate("&a你现在拥有 &e" + data.getKeys() + " &a个钥匙"));
        }
    }

    @Command(
            names = "setLocCurrency",
            permissionNode = "pit.admin",
            async = true
    )
    public void setLocCurrency(Player player, @Parameter(name = "field") String field) {
        try {
            for (Field declaredField : PitConfig.class.getDeclaredFields()) {
                ConfigSerializer annotation = declaredField.getAnnotation(ConfigSerializer.class);
                if (annotation == null) {
                    break;
                }
                if (annotation.serializer() == LocationSerializer.class) {
                    ConfigData fieldAnnotation = declaredField.getAnnotation(ConfigData.class);
                    if (fieldAnnotation == null) {
                        break;
                    }
                    if (fieldAnnotation.path().toLowerCase().equals(field.toLowerCase())) {
                        declaredField.setAccessible(true);
                        declaredField.set(ThePit.getInstance().getPitConfig(), player.getLocation());
                        ThePit.getInstance().getPitConfig().save();
                        player.sendMessage(CC.translate("&a设置成功"));
                        return;
                    }
                }
            }
            player.sendMessage(CC.translate("&c没有找到"));
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(CC.translate("&c设置失败，请查看报错"));
        }
    }

    @Command(
            names = {"pitAdmin giveItemInHand", "give"},
            permissionNode = "pit.admin",
            async = true
    )
    public void giveItemInHand(Player player, @Parameter(name = "target", defaultValue = "self") Player target) {
        if (player.getItemInHand() == null || player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage(CC.translate("&c请手持要给予的物品!"));
            return;
        }
        target.getInventory().addItem(player.getItemInHand());
        target.sendMessage(CC.translate("&a一位管理员给予了你一些物品..."));
        player.sendMessage(CC.translate("&a成功给予物品至 " + RankUtil.getPlayerColoredName(target.getUniqueId())));
    }

    @Command(
            names = "pitAdmin addSpawn",
            permissionNode = "pit.admin",
            async = true
    )
    public void addSpawn(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .getSpawnLocations()
                .add(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        int num = ThePit.getInstance()
                .getPitConfig()
                .getSpawnLocations()
                .size();

        player.sendMessage(CC.translate("&aSuccessfully!its " + num + "th spawnLocation"));
    }

    @Command(
            names = "pitAdmin loc",
            permissionNode = "pit.admin",
            async = true
    )
    public void checkLocation(Player player) {
        Location location = player.getLocation();
        player.spigot()
                .sendMessage(new ChatComponentBuilder(location.toString())
                        .setCurrentClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, location.toString()))
                        .create());
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "pitAdmin hologramLoc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setHologramLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setHologramLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "pitAdmin keeperLoc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setKeeperLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setKeeperNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin mail",
            permissionNode = "pit.admin",
            async = true
    )
    public void setMailLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setMailNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin stashNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setStashNpcLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setStashNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin genesisDemonLoc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setGenesisDemonLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setGenesisDemonNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin genesisAngelLoc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setGenesisAngelLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setGenesisAngelNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin changeItemInHand lives",
            permissionNode = "pit.admin",
            async = true
    )
    public void changeLives(Player player, @Parameter(name = "amount") String amount) {
        if (player.getItemInHand() == null || player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage(CC.translate("&c请先手持要修改的物品!"));
        }
        try {
            ItemStack stack = new ItemBuilder(player.getItemInHand()).live(Integer.parseInt(amount)).build();
            player.setItemInHand(MythicUtil.getMythicItem(stack).toItemStack());
            player.sendMessage(CC.translate("设置成功"));
        } catch (Exception ignored) {
            player.sendMessage("Error");
        }
    }

    @Command(
            names = "pitAdmin changeItemInHand maxlive",
            permissionNode = "pit.admin",
            async = true
    )
    public void changeMaxLives(Player player, @Parameter(name = "amount") String amount) {
        if (player.getItemInHand() == null || player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage(CC.translate("&c请先手持要修改的物品!"));
        }
        try {
            ItemStack stack = new ItemBuilder(player.getItemInHand()).maxLive(Integer.parseInt(amount)).build();
            player.setItemInHand(MythicUtil.getMythicItem(stack).toItemStack());
            player.sendMessage(CC.translate("设置成功"));
        } catch (Exception ignored) {
            player.sendMessage("Error");
        }
    }

    @Command(names = "pitAdmin changeItemInHand tier", permissionNode = "pit.admin", async = true)
    public void changeTier(Player player, @Parameter(name = "amount") String amount) {
        if (player.getItemInHand() == null || player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage(CC.translate("&c请先手持要修改的物品!"));
        }
        try {
            ItemStack stack = new ItemBuilder(player.getItemInHand()).tier(Integer.parseInt(amount)).build();
            player.setItemInHand(MythicUtil.getMythicItem(stack).toItemStack());
            player.sendMessage(CC.translate("设置成功"));
        } catch (Exception ignored) {
            player.sendMessage("Error");
        }
    }

    @Command(
            names = "pitAdmin shopNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setShopNpc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setShopNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin perkNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setPerkNpc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setPerkNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin LeaderNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setLeaderNpc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setLeaderBoardNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin prestigeNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setPrestigeNpc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setPrestigeNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin debug",
            permissionNode = "pit.admin",
            async = true
    )
    public void setDebugNpcs(Player player, @Parameter(name = "value") String value) {
        final PitConfig pitConfig = ThePit.getInstance().getPitConfig();
        if (value.equals("infNpc")) {
            pitConfig.setInfinityNpcLocation(player.getLocation());
            pitConfig.save();

            player.sendMessage(CC.translate("&aOK!"));
        }

        if (value.equalsIgnoreCase("toggle")) {
            pitConfig.setDebugServer(!pitConfig.isDebugServer());
            pitConfig.save();

            if (pitConfig.isDebugServer()) {
                player.sendMessage(CC.translate("&a现在开启了，重启以生效"));
            } else {
                player.sendMessage(CC.translate("&c现在关闭了，重启以生效"));
            }
            ThePit.getInstance().getRebootRunnable()
                    .addRebootTask(new RebootRunnable.RebootTask("服务器配置切换", System.currentTimeMillis() + 10 * 1000));
        }

        if (value.equals("enchNpc")) {
            pitConfig.setEnchantNpcLocation(player.getLocation());
            pitConfig.save();

            player.sendMessage(CC.translate("&aOK!"));
        }

        if (value.equals("toPublic")) {
            pitConfig.setDebugServerPublic(true);
            pitConfig.save();

            player.sendMessage(CC.translate("&a现在开启了"));
        }

        if (value.equals("toPrivate")) {
            pitConfig.setDebugServerPublic(false);
            pitConfig.save();

            player.sendMessage(CC.translate("&c现在关闭了"));
        }
    }

    @Command(
            names = "pitAdmin statusNpc",
            permissionNode = "pit.admin",
            async = true
    )
    public void setStatusNpc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setStatusNpcLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }

    @Command(
            names = "pitAdmin change",
            permissionNode = "pit.admin",
            async = true
    )
    public void setPlayerLevel(CommandSender sender, @Parameter(name = "target", defaultValue = "self") Player target, @Parameter(name = "type") String type, @Parameter(name = "amount") int amount) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        if ("coin".equalsIgnoreCase(type)) {
            profile.setCoins(amount);
        }
        if ("prestige".equalsIgnoreCase(type)) {
            profile.setPrestige(amount);
        }
        if ("renown".equalsIgnoreCase(type)) {
            profile.setRenown(amount);
        }
        if ("streak".equalsIgnoreCase(type)) {
            profile.setStreakKills(amount);
        }
        if ("abounty".equalsIgnoreCase(type)) {
            profile.setActionBounty(amount);
        }
        if ("level".equalsIgnoreCase(type)) {
            double levelExpRequired = LevelUtil.getLevelTotalExperience(profile.getPrestige(), amount);
            profile.setExperience(levelExpRequired);
            Player onlinePlayer = Bukkit.getPlayer(profile.getUuid());
            if (onlinePlayer != null) {
                profile.applyExperienceToPlayer(onlinePlayer);
            }
        }
        if ("bounty".equalsIgnoreCase(type)) {
            profile.setBounty(amount);
        }
        if ("maxhealth".equalsIgnoreCase(type)) {
            target.setMaxHealth(20.0 + profile.getExtraMaxHealthValue());
        }

    }

    @Command(
            names = {"pitadmin edit", "edit"},
            permissionNode = "pit.admin"
    )
    public void onChangeEdit(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        profile.setEditingMode(!profile.isEditingMode());
        if (profile.isEditingMode()) {
            player.sendMessage(CC.translate("&a你现在可以自由破坏方块"));
        } else {
            player.sendMessage(CC.translate("&c你关闭了自由破坏方块"));
        }
    }

    @Command(
            names = "openMenu",
            permissionNode = "pit.admin"
    )
    public void onOpenMenu(Player player, @Parameter(name = "menu", defaultValue = "no") String menu) {
        if (menu.equalsIgnoreCase("shop")) {
            ThePit.api.openMenu(player, "shop");
        }
        if (menu.equalsIgnoreCase("perkBuy")) {
            new PerkChooseMenu().openMenu(player);
        }
        if (menu.equalsIgnoreCase("prestigePerkBuy")) {
            try {
                new PrestigePerkBuyMenu().openMenu(player);
            } catch (Exception e) {
                CC.printErrorWithCode(player, e);
            }
        }
        if (menu.equalsIgnoreCase("prestige")) {
            new PrestigeMainMenu().openMenu(player);
        }
        if (menu.equalsIgnoreCase("ench")) {
            try {
                ThePit.getApi().openMythicWellMenu(player);
            } catch (Exception e) {
                CC.printErrorWithCode(player, e);
            }
        }
        if (menu.equalsIgnoreCase("quest")) {
            try {
                new QuestMenu().openMenu(player);
            } catch (Exception e) {
                CC.printErrorWithCode(player, e);
            }
        }
        if (menu.equalsIgnoreCase("mail")) {
            try {
                new MailMenu().openMenu(player);
            } catch (Exception e) {
                CC.printErrorWithCode(player, e);
            }
        }
        if (menu.equalsIgnoreCase("cdk")) {
            try {
                new CDKMenu().openMenu(player);
            } catch (Exception e) {
                CC.printErrorWithCode(player, e);
            }
        }
        if (menu.equalsIgnoreCase("allCdk")) {
            Bukkit.getScheduler().runTaskAsynchronously(ThePit.getInstance(), () -> {
                try {
                    new CDKViewMenu().openMenu(player);
                } catch (Exception e) {
                    CC.printErrorWithCode(player, e);
                }
            });
        }
    }

    @Command(names = "pitadmin mythicHologram", permissionNode = "pit.admin")
    public void setMythicHologram(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setMythicHologram(player.getLocation());
        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(names = "pitadmin chestHologram", permissionNode = "pit.admin")
    public void setChestHologram(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setChestHologram(player.getLocation());
        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "pitadmin leaderHologram",
            permissionNode = "pit.admin"
    )
    public void setLeaderBoardHologram(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setLeaderBoardHologram(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "pitadmin helperHolo",
            permissionNode = "pit.admin"
    )
    public void setHelperHologram(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setHelperHologramLocation(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "kaboom",
            permissionNode = "pit.admin"
    )
    public void kaboom(Player player) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            target.setVelocity(new Vector(0, 2, 0));
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.sendMessage(CC.translate("&a&lKaboom!!! " + RankUtil.getPlayerColoredName(player.getName()) + " &7把你击飞了!"));
            new KaboomMedal().addProgress(PlayerProfile.getPlayerProfileByUuid(player.getUniqueId()), 1);
        }
    }

    @Command(
            names = "pitadmin pitLoc",
            permissionNode = "pit.admin"
    )
    public void setPitLoc(Player player, @Parameter(name = "type") String type) {
        if (type.equalsIgnoreCase("a")) {
            ThePit.getInstance().getPitConfig().setPitLocA(player.getLocation());
            ThePit.getInstance().getPitConfig().save();
        } else if (type.equalsIgnoreCase("b")) {
            ThePit.getInstance().getPitConfig().setPitLocB(player.getLocation());
            ThePit.getInstance().getPitConfig().save();
        }
    }

    @SneakyThrows
    @Command(
            names = "event",
            permissionNode = "pit.admin"
    )
    public void setEvent(Player player, @Parameter(name = "name") String name) {
        final boolean success = ThePit.getApi().openEvent(player, name);
        if (success) {
            player.sendMessage(CC.translate("&a成功!"));
        } else {
            player.sendMessage(CC.translate("&c失败, 错误的参数"));
        }
    }

    @Command(names = "pitAdmin setkothloc", permissionNode = "pit.admin")
    public void setKothLoc(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setKothLoc(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage("§a设置成功！");
    }

    @Command(
            names = "giveSupporter",
            permissionNode = "pit.admin"
    )
    public void giveSupporter(CommandSender sender, @Parameter(name = "target", defaultValue = "self") Player target) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        profile.setSupporter(true);
        profile.setSupporterGivenByAdmin(true);
        sender.sendMessage("给了");
    }

    @Command(
            names = "takeSupporter",
            permissionNode = "pit.admin"
    )
    public void takeMySupporter(Player player, @Parameter(name = "target", defaultValue = "self") Player target) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        profile.setSupporter(false);
        profile.setSupporterGivenByAdmin(false);
    }

    @Command(
            names = "pitadmin drop",
            permissionNode = "pit.admin",
            async = true
    )
    public void dropDatabase(Player player) {
        if (confirmDrop.contains(player.getUniqueId())) {
            player.sendMessage(CC.translate("&c&l你已经确认过了！请等待其他管理员确认"));
            return;
        }
        if (requestDrop.contains(player.getUniqueId())) {
            confirmDrop.add(player.getUniqueId());

            if (confirmDrop.size() < 3) {
                player.sendMessage(CC.translate("&c&l你已确认Drop数据库，还需要&e&l" + (3 - confirmDrop.size()) + "&c&l管理员确认！"));
            } else {
                PlayerProfile.getCacheProfile().clear();
                ThePit.getInstance()
                        .getMongoDB()
                        .getProfileCollection()
                        .drop();
                Bukkit.shutdown();
            }
            return;
        }

        player.sendMessage(CC.translate("&c&l你正在执行&e&l删档&c&l操作，再次输入此指令以确认"));
        requestDrop.add(player.getUniqueId());
    }

    @Command(
            names = "testSound"
    )
    public void testSound(Player player, @Parameter(name = "sound", defaultValue = "successfully") String sound) {
        ThePit.getInstance().getSoundFactory()
                .playSound(sound, player);
    }


    @Command(
            names = "pitadmin quest",
            permissionNode = "pit.admin"
    )
    public void onSetQuestNpc(Player player) {
        PitConfig pitConfig = ThePit.getInstance()
                .getPitConfig();
        pitConfig
                .setQuestNpcLocation(player.getLocation());
        pitConfig.save();
    }

    @Command(
            names = "pitadmin reloadnpc",
            permissionNode = "pit.admin"
    )
    private void reloadNpc(CommandSender sender) {
        for (AbstractPitNPC npc : NpcFactory.getPitNpc()) {
            npc.getNpc().setLocation(npc.getNpcSpawnLocation());
        }
        sender.sendMessage("ok");
    }

    @Command(
            names = "pitadmin bakeMaster",
            permissionNode = "pit.admin",
            async = true
    )
    public void setBakeMasterLocation(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setBakeMasterNpcLocation(player.getLocation());
        ThePit.getInstance()
                .getPitConfig()
                .save();
        player.sendMessage(CC.translate("&a烘焙大师NPC位置已设置!"));
    }

    @Command(
            names = "pitadmin table",
            permissionNode = "pit.admin",
            async = true
    )
    public void onSetEnchantTable(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setEnchantLocation(player.getTargetBlock(Collections.singleton(Material.ENCHANTMENT_TABLE), 100).getLocation());

        ThePit.getInstance()
                .saveConfig();

        player.sendMessage(CC.translate("&aSave!"));
    }

    @Command(
            names = "ench",
            permissionNode = "pit.admin"
    )
    public void enchantment(Player player) {
        player.playSound(player.getLocation(), Sound.CHEST_OPEN, 1, 1);
        ThePit.api.openMenu(player, "admin_enchant");
    }

    @Command(
            names = "pitadmin setegg",
            permissionNode = "pit.admin"
    )
    public void setEgg(Player player) {
        ThePit.getInstance()
                .getPitConfig()
                .setEggLoc(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage(CC.translate("&aSuccessfully!"));
    }


    @Command(
            names = "pi",
            permissionNode = "pit.admin"
    )
    public void pitItem(Player player) {
        player.playSound(player.getLocation(), Sound.CHEST_OPEN, 1, 1);
        ThePit.api.openMenu(player, "admin_item");
    }

    @Command(
            names = "pr",
            permissionNode = "pit.admin"
    )
    public void pitRuneItem(Player player) {
        player.playSound(player.getLocation(), Sound.CHEST_OPEN, 1, 1);
        ThePit.api.openMenu(player, "rune_item");
    }

    @Command(
            names = "reboot",
            permissionNode = "pit.admin",
            async = true
    )
    public void reboot(CommandSender sender, @Parameter(name = "duration", defaultValue = "2m") Duration duration, @Parameter(name = "原因", defaultValue = "计划外重启") String reason) {
        ThePit.getInstance()
                .getRebootRunnable()
                .addRebootTask(new RebootRunnable.RebootTask(reason, System.currentTimeMillis() + duration.getValue()));
        sender.sendMessage("§a设置成功");
    }

    @Command(
            names = "forceTrade",
            permissionNode = "pit.admin"

    )
    public void onForceTrade(Player player, @Parameter(name = "target") Player target) {
        TradeManager tradeManager = new TradeManager(player, target);
        new TradeMenu(tradeManager).openMenu(player);
        new TradeMenu(tradeManager).openMenu(target);
    }

    @Command(
            names = "wipe",
            permissionNode = "pit.admin",
            async = true
    )
    public void wipePlayer(Player player, @Parameter(name = "player") String target, @Parameter(name = "reason") String reason) {
        PlayerProfile profile = PlayerProfile.getOrLoadPlayerProfileByName(target);
        if (profile == null) {
            player.sendMessage(CC.translate("&cすみません！あのプレイヤーは見つかりませんでした！もう一度確認してください！"));
            return;
        }
        boolean wipe = profile.wipe(reason);
        if (wipe) {
            player.sendMessage(CC.translate("&a成功しました！あのプレイヤーはワイプされました！ID: " + profile.getPlayerName()));
        } else {
            player.sendMessage(CC.translate("&c&lすみません！ワイプが失敗しました！いくつかのエラーがありました！"));
        }
    }

    @Command(
            names = "unwipe",
            permissionNode = "pit.admin",
            async = true
    )
    public void wipePlayer(Player player, @Parameter(name = "player") String target) {
        PlayerProfile profile = PlayerProfile.getOrLoadPlayerProfileByName(target);
        if (profile == null) {
            player.sendMessage(CC.translate("&cすみません！あのプレイヤーは見つかりませんでした！もう一度確認してください！"));
            return;
        }
        boolean wipe = profile.unWipe();
        if (wipe) {
            player.sendMessage(CC.translate("&a成功しました！ID: " + profile.getPlayerName()));
        } else {
            player.sendMessage(CC.translate("&c&l失敗しました！あのプレイヤーはワイプダークがありませんでした"));
        }
    }

    @Command(
            names = "debug medal",
            permissionNode = "pit.admin"
    )
    public void onDebugMedal(Player player) {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        player.sendMessage(profile.getMedalData().toString());
    }

    @Command(
            names = "pitadmin trade",
            permissionNode = "pit.admin",
            async = true
    )
    public void onTestWorldEdit(Player player, @Parameter(name = "value") String name) {
        PlayerProfile profile = PlayerProfile.getOrLoadPlayerProfileByName(name);
        FindIterable<TradeData> tradeA = ThePit.getInstance()
                .getMongoDB()
                .getTradeCollection()
                .find(Filters.eq("playerA", profile.getUuid()));

        FindIterable<TradeData> tradeB = ThePit.getInstance()
                .getMongoDB()
                .getTradeCollection()
                .find(Filters.eq("playerB", profile.getUuid()));

        List<TradeData> data = new ArrayList<>();
        for (TradeData tradeData : tradeA) {
            data.add(tradeData);
        }
        for (TradeData tradeData : tradeB) {
            data.add(tradeData);
        }

        ThePit.api.openTradeTrackMenu(player, profile, data);
    }


    @Command(
            names = "rollback",
            permissionNode = "pit.admin",
            async = true
    )
    public void onRollbackInv(Player player, @Parameter(name = "name") String name) {
        PlayerProfile profile = PlayerProfile.getOrLoadPlayerProfileByName(name);
        if (profile == null) {
            player.sendMessage(CC.translate("&c该玩家不存在"));
            return;
        }

        final FindIterable<PlayerInvBackup> backups = ThePit.getInstance().getMongoDB()
                .getInvCollection()
                .find(Filters.eq("uuid", profile.getUuid()));

        List<Button> buttons = new ArrayList<>();
        int i = 0;
        for (PlayerInvBackup invBackup : backups) {
            if (invBackup.getInv() == null) continue;
            buttons.add(new ShowInvBackupButton(
                    new ItemBuilder(Material.BOOK)
                            .name("&a备份时间: " + format.format(invBackup.getTimeStamp()))
                            .lore(("&e物品数: " + InventoryUtil.getInventoryFilledSlots(invBackup.getInv().getContents())))
                            .amount(Math.min(64, InventoryUtil.getInventoryFilledSlots(invBackup.getInv().getContents())))
                            .build(), invBackup, profile));
            i++;
        }

        Collections.reverse(buttons);

        player.sendMessage(CC.translate("总计: " + i + " 个"));
        new PagedMenu(profile.getPlayerName() + " 的背包备份", buttons).openMenu(player);
        //todo: open backup inventory view menu
    }

    @Command(
            names = "pitadmin ham",
            permissionNode = "pit.admin"
    )
    public void hamNpcLocations(Player player, @Parameter(name = "value") String value) {
        if (value.equalsIgnoreCase("a")) {
            final PitConfig config = ThePit.getInstance()
                    .getPitConfig();
            config.getHamburgerNpcLocA()
                    .add(player.getLocation());
            config.save();

            player.sendMessage("Now: " + config.getHamburgerNpcLocA().size());
        }
    }

    @Command(
            names = "pitadmin spire floor",
            permissionNode = "pit.admin"
    )
    public void spireFloorLocations(Player player, @Parameter(name = "value") int value) {
        try {
            final PitConfig config = ThePit.getInstance()
                    .getPitConfig();
            config.getSpireFloorLoc()
                    .add(player.getLocation());
            config.save();

            player.sendMessage("Now: " + config.getSpireFloorLoc().size());
        } catch (Exception e) {
            CC.printError(player, e);
        }
    }

    @Command(
            names = "pitadmin ham clear",
            permissionNode = "pit.admin"
    )
    public void hamClearNpcLocations(Player player, @Parameter(name = "value") String value) {
        if (value.equalsIgnoreCase("a")) {
            final PitConfig config = ThePit.getInstance()
                    .getPitConfig();
            config.getHamburgerNpcLocA().clear();
            config.save();
            player.sendMessage("ok");
        }
    }

    @Command(
            names = "pitadmin spire spawn",
            permissionNode = "pit.admin"
    )
    public void spireSpawn(Player player, @Parameter(name = "value") String value) {
        ThePit.getInstance()
                .getPitConfig()
                .setSpireLoc(player.getLocation());

        ThePit.getInstance()
                .getPitConfig()
                .save();
    }

    @Command(
            names = "forceSpawn",
            permissionNode = "pit.admin"
    )
    public void forceSpawn(Player player, @Parameter(defaultValue = "self", name = "target") Player target) {
        Location location = ThePit.getInstance().getPitConfig()
                .getSpawnLocations()
                .get(RandomUtil.random.nextInt(ThePit.getInstance().getPitConfig().getSpawnLocations().size()));

        target.removeMetadata("backing", ThePit.getInstance());

        target.teleport(location);

        for (ItemStack item : target.getInventory()) {
            if (ItemUtil.isRemovedOnJoin(item)) {
                target.getInventory().remove(item);
            }
        }

        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
        profile.setStreakKills(0);

        PlayerUtil.clearPlayer(target, true, false);
    }

    @Command(
            names = "deleteFile",
            permissionNode = "pit.admin"
    )
    public void deleteFile(Player player, @Parameter(name = "filePath") String filePath) {
        final File file = new File(filePath);
        if (file.exists()) {
            player.sendMessage(CC.translate("&c文件不存在"));
        } else {
            final boolean delete = file.delete();
            player.sendMessage("&a文件删除状态: " + delete);
        }
    }

    @Command(
            names = "disablePlugin",
            permissionNode = "pit.admin"
    )
    public void onDisablePlugin(Player player, @Parameter(name = "plugin") String plugin) {
        final Plugin target = Bukkit.getPluginManager().getPlugin(plugin);
        if (target == null) {
            player.sendMessage(CC.translate("&c没有找到那个插件"));
            return;
        }
        Bukkit.getPluginManager().disablePlugin(target);
        player.sendMessage(CC.translate("&a卸载成功!"));
    }

    @Command(
            names = "refreshEvents",
            permissionNode = "pit.admin"
    )
    public void refreshEvents(Player player) {
        EventsHandler.INSTANCE.refreshEvents();
        player.sendMessage("Refreshed");
    }

    @Command(
            names = "addAngelSpawns",
            permissionNode = "pit.admin"
    )
    public void addAngelSpawns(Player player) {
        ThePit.getInstance().getPitConfig()
                .getAngelSpawns()
                .add(player.getLocation().clone());
        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage("ok");
    }

    @Command(
            names = "addDemonSpawns",
            permissionNode = "pit.admin"
    )
    public void addDemonSpawns(Player player) {
        ThePit.getInstance().getPitConfig()
                .getDemonSpawns()
                .add(player.getLocation().clone());
        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage("ok");
    }

    @Command(
            names = "addPackagePoint",
            permissionNode = "pit.admin"
    )
    public void addPackagePoint(Player player) {
        ThePit.getInstance().getPitConfig()
                .getPackageLocations()
                .add(player.getLocation().clone());
        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage("ok");
    }

    @Command(
            names = "addSewersPoint",
            permissionNode = "pit.admin"
    )
    public void addSewersPoint(Player player) {
        ThePit.getInstance().getPitConfig()
                .getSewersChestsLocations()
                .add(player.getLocation().clone());
        ThePit.getInstance()
                .getPitConfig()
                .save();

        player.sendMessage("ok");
    }

    @Command(
            names = "debugItem",
            permissionNode = "pit.admin"
    )
    public void debugItem(Player player) {
        System.out.println((InventoryUtil.itemsToString(new ItemStack[]{player.getItemInHand()})));
    }

    @Command(
            names = "enchantrecords",
            permissionNode = "pit.admin"
    )
    public void checkEnchantRecords(Player player) {
        final ItemStack item = player.getItemInHand();
        if (item == null) return;

        final IMythicItem mythicItem = Utils.getMythicItem(item);
        if (mythicItem == null) return;

        player.sendMessage(CC.translate("&a这是该物品的附魔记录: "));

        for (EnchantmentRecord record : mythicItem.getEnchantmentRecords()) {
            player.sendMessage(
                    CC.translate("  &e" + record.getEnchanter() + " &7- &a" + record.getDescription() + " &7- &a" + DateFormatUtils.format(record.getTimestamp(), "yyyy年MM月dd日 HH:mm:ss")));
        }
        player.sendMessage(CC.translate("&7以上记录最多展示5条"));
    }

    @Command(names = {"addSquadsLoc"}, permissionNode = "pit.admin")
    public void squadsAddLocation(Player player) {
        ThePit.getInstance().getPitConfig().getSquadsLocations().add(player.getLocation());
        ThePit.getInstance().getPitConfig().save();

        player.sendMessage("ok");
    }

    @Command(names = {"addbhLoc"}, permissionNode = "pit.admin")
    public void bhAddLocation(Player player) {
        ThePit.getInstance().getPitConfig().getBlockHeadLocations().add(player.getLocation());
        ThePit.getInstance().getPitConfig().save();

        player.sendMessage("ok");
    }

    @Command(names = "pitadmin eventcfg", permissionNode = "pit.admin")
    public void eventConfigHelp(Player player) {
        player.sendMessage(CC.translate("&6事件配置命令:"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg keys &7- 查看可配置键"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg show <key> &7- 查看当前值"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg set <key> &7- 设为当前位置(列表会覆盖为1个点)"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg add <key> &7- 追加当前位置(仅列表)"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg remove <key> <index> &7- 删除列表点(1开始)"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg clear <key> &7- 清空该键"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg clearevent <event> &7- 清空整组事件配置"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg setint <key> <value> &7- 设置整数配置(如rage半径/高度)"));
        player.sendMessage(CC.translate("&e/pitadmin eventcfg save &7- 保存到config.yml"));
        player.sendMessage(CC.translate("&c/pitadmin eventcfg overwrite &7- 仅清空 loc.events.* 事件配置(安全)"));
        player.sendMessage(CC.translate("&4/pitadmin eventcfg overwriteall confirm &7- 清空事件+全局依赖键(危险)"));
    }

    @Command(names = "pitadmin eventcfg keys", permissionNode = "pit.admin")
    public void eventConfigKeys(Player player) {
        List<EventFieldBinding> bindings = getEventConfigBindings();
        player.sendMessage(CC.translate("&6可配置事件键 (" + bindings.size() + "):"));
        for (EventFieldBinding binding : bindings) {
            player.sendMessage(CC.translate("&7- &e" + binding.alias + " &f(" + binding.path + ") &7[" + getTypeName(binding.type) + "]"));
        }
    }

    @Command(names = "pitadmin eventcfg show", permissionNode = "pit.admin")
    public void eventConfigShow(Player player, @Parameter(name = "key") String key) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            if (binding.type == EventFieldType.LOCATION) {
                Location location = (Location) binding.field.get(config);
                player.sendMessage(CC.translate("&e" + binding.path + " &7= &f" + (location == null ? "null" : location.toString())));
                return;
            }
            if (binding.type == EventFieldType.LOCATION_LIST) {
                List<Location> list = getOrCreateLocationList(config, binding);
                player.sendMessage(CC.translate("&e" + binding.path + " &7共 &f" + list.size() + " &7个点:"));
                for (int i = 0; i < list.size(); i++) {
                    player.sendMessage(CC.translate("&7  #" + (i + 1) + " &f" + list.get(i)));
                }
                return;
            }
            if (binding.type == EventFieldType.INT) {
                player.sendMessage(CC.translate("&e" + binding.path + " &7= &f" + binding.field.get(config)));
                return;
            }
            if (binding.type == EventFieldType.DOUBLE) {
                player.sendMessage(CC.translate("&e" + binding.path + " &7= &f" + binding.field.get(config)));
            }
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg set", permissionNode = "pit.admin")
    public void eventConfigSet(Player player, @Parameter(name = "key") String key) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            if (binding.type == EventFieldType.LOCATION) {
                binding.field.set(config, player.getLocation().clone());
                config.save();
                player.sendMessage(CC.translate("&a已设置: " + binding.path));
                return;
            }
            if (binding.type == EventFieldType.LOCATION_LIST) {
                List<Location> list = getOrCreateLocationList(config, binding);
                list.clear();
                list.add(player.getLocation().clone());
                config.save();
                player.sendMessage(CC.translate("&a已覆盖列表: " + binding.path + " (1个点)"));
                return;
            }
            player.sendMessage(CC.translate("&c键 " + binding.path + " 不是坐标类型，当前类型: " + getTypeName(binding.type)));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg add", permissionNode = "pit.admin")
    public void eventConfigAdd(Player player, @Parameter(name = "key") String key) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }
        if (binding.type != EventFieldType.LOCATION_LIST) {
            player.sendMessage(CC.translate("&c仅支持列表键 add，当前类型: " + getTypeName(binding.type)));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            List<Location> list = getOrCreateLocationList(config, binding);
            list.add(player.getLocation().clone());
            config.save();
            player.sendMessage(CC.translate("&a已添加到 " + binding.path + "，当前数量: " + list.size()));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg remove", permissionNode = "pit.admin")
    public void eventConfigRemove(Player player, @Parameter(name = "key") String key, @Parameter(name = "index") int index) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }
        if (binding.type != EventFieldType.LOCATION_LIST) {
            player.sendMessage(CC.translate("&c仅支持列表键 remove，当前类型: " + getTypeName(binding.type)));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            List<Location> list = getOrCreateLocationList(config, binding);
            if (index <= 0 || index > list.size()) {
                player.sendMessage(CC.translate("&c索引超出范围，当前范围: 1-" + list.size()));
                return;
            }
            list.remove(index - 1);
            config.save();
            player.sendMessage(CC.translate("&a已删除 " + binding.path + " 第 " + index + " 个点，剩余: " + list.size()));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg clear", permissionNode = "pit.admin")
    public void eventConfigClear(Player player, @Parameter(name = "key") String key) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            clearEventField(config, binding);
            config.save();
            player.sendMessage(CC.translate("&a已清空: " + binding.path));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg clearevent", permissionNode = "pit.admin")
    public void eventConfigClearEvent(Player player, @Parameter(name = "event") String eventName) {
        PitConfig config = ThePit.getInstance().getPitConfig();
        int clearedCount = 0;

        try {
            for (EventFieldBinding binding : getEventConfigBindings()) {
                if (!matchesEventGroup(eventName, binding.path)) {
                    continue;
                }
                clearEventField(config, binding);
                clearedCount++;
            }
            if (clearedCount == 0) {
                player.sendMessage(CC.translate("&c未匹配到事件组: " + eventName));
                return;
            }
            config.save();
            player.sendMessage(CC.translate("&a已清空事件组 " + eventName + "，共 " + clearedCount + " 项"));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg setint", permissionNode = "pit.admin")
    public void eventConfigSetInt(Player player, @Parameter(name = "key") String key, @Parameter(name = "value") int value) {
        EventFieldBinding binding = resolveEventField(key);
        if (binding == null) {
            player.sendMessage(CC.translate("&c未找到键: " + key + "，使用 /pitadmin eventcfg keys 查看可用键"));
            return;
        }
        if (binding.type != EventFieldType.INT) {
            player.sendMessage(CC.translate("&c该键不是整型，当前类型: " + getTypeName(binding.type)));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        try {
            if (binding.field.getType() == int.class) {
                binding.field.setInt(config, value);
            } else {
                binding.field.set(config, value);
            }
            config.save();
            player.sendMessage(CC.translate("&a已设置: " + binding.path + " = " + value));
        } catch (Exception exception) {
            CC.printError(player, exception);
        }
    }

    @Command(names = "pitadmin eventcfg save", permissionNode = "pit.admin")
    public void eventConfigSave(Player player) {
        ThePit.getInstance().getPitConfig().save();
        player.sendMessage(CC.translate("&a事件配置已保存到 config.yml"));
    }

    @Command(names = "pitadmin eventcfg overwrite", permissionNode = "pit.admin")
    public void eventConfigOverwrite(Player player) {
        PitConfig config = ThePit.getInstance().getPitConfig();
        int clearedCount = 0;
        try {
            for (EventFieldBinding binding : getEventConfigBindings()) {
                if (!binding.path.startsWith("loc.events.")) {
                    continue;
                }
                clearEventField(config, binding);
                clearedCount++;
            }
        } catch (Exception exception) {
            CC.printError(player, exception);
            return;
        }

        config.save();
        player.sendMessage(CC.translate("&a已清空 loc.events.* 事件配置，共 " + clearedCount + " 项。"));
        player.sendMessage(CC.translate("&7提示: spawn/portal/packages/squads/blockHead/Genesis 等全局依赖键已保留。"));
    }

    @Command(names = "pitadmin eventcfg overwriteall", permissionNode = "pit.admin")
    public void eventConfigOverwriteAll(Player player, @Parameter(name = "confirm", defaultValue = "") String confirm) {
        if (!"confirm".equalsIgnoreCase(confirm)) {
            player.sendMessage(CC.translate("&c危险操作被拦截。"));
            player.sendMessage(CC.translate("&7如需继续，请执行: &e/pitadmin eventcfg overwriteall confirm"));
            return;
        }

        PitConfig config = ThePit.getInstance().getPitConfig();
        int clearedCount = 0;
        try {
            for (EventFieldBinding binding : getEventConfigBindings()) {
                clearEventField(config, binding);
                clearedCount++;
            }
        } catch (Exception exception) {
            CC.printError(player, exception);
            return;
        }

        config.save();
        player.sendMessage(CC.translate("&c已清空事件相关全部配置(含全局依赖键)，共 " + clearedCount + " 项。"));
    }

    private List<EventFieldBinding> getEventConfigBindings() {
        List<EventFieldBinding> bindings = new ArrayList<>();

        for (Field field : PitConfig.class.getDeclaredFields()) {
            ConfigData configData = field.getAnnotation(ConfigData.class);
            if (configData == null) {
                continue;
            }
            String path = configData.path();
            if (!isEventConfigPath(path)) {
                continue;
            }

            EventFieldType type = resolveEventFieldType(field);
            if (type == null) {
                continue;
            }

            field.setAccessible(true);
            bindings.add(new EventFieldBinding(path, createEventAlias(path), field, type));
        }
        return bindings;
    }

    private EventFieldBinding resolveEventField(String key) {
        String normalized = normalizeEventKey(key);
        if (normalized.isEmpty()) {
            return null;
        }

        for (EventFieldBinding binding : getEventConfigBindings()) {
            if (normalizeEventKey(binding.path).equals(normalized)
                    || normalizeEventKey(binding.alias).equals(normalized)
                    || normalizeEventKey(binding.path.replace('.', '_')).equals(normalized)) {
                return binding;
            }
        }
        return null;
    }

    private EventFieldType resolveEventFieldType(Field field) {
        ConfigSerializer serializer = field.getAnnotation(ConfigSerializer.class);
        if (serializer != null && serializer.serializer() == LocationSerializer.class) {
            if (List.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    Type[] actualTypes = ((ParameterizedType) genericType).getActualTypeArguments();
                    if (actualTypes.length > 0 && actualTypes[0] != Location.class) {
                        return null;
                    }
                }
                return EventFieldType.LOCATION_LIST;
            }
            if (field.getType() == Location.class) {
                return EventFieldType.LOCATION;
            }
            return null;
        }

        if (field.getType() == int.class || field.getType() == Integer.class) {
            return EventFieldType.INT;
        }
        if (field.getType() == double.class || field.getType() == Double.class) {
            return EventFieldType.DOUBLE;
        }
        return null;
    }

    private boolean isEventConfigPath(String path) {
        return path.startsWith("loc.events.")
                || "loc.spawn".equals(path)
                || path.startsWith("loc.portal.")
                || "loc.packages".equals(path)
                || path.startsWith("loc.Sewers.")
                || "loc.squads".equals(path)
                || "loc.blockHead".equals(path)
                || path.startsWith("loc.Genesis.");
    }

    private String createEventAlias(String path) {
        String alias = path;
        if (path.startsWith("loc.events.")) {
            alias = path.substring("loc.events.".length());
        } else if (path.startsWith("loc.")) {
            alias = path.substring("loc.".length());
        }
        return alias.replace('.', '_').toLowerCase(Locale.ROOT);
    }

    private String normalizeEventKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @SuppressWarnings("unchecked")
    private List<Location> getOrCreateLocationList(PitConfig config, EventFieldBinding binding) throws IllegalAccessException {
        Object object = binding.field.get(config);
        if (object instanceof List) {
            return (List<Location>) object;
        }
        List<Location> list = new ArrayList<>();
        binding.field.set(config, list);
        return list;
    }

    private void clearEventField(PitConfig config, EventFieldBinding binding) throws IllegalAccessException {
        if (binding.type == EventFieldType.LOCATION) {
            binding.field.set(config, null);
            return;
        }
        if (binding.type == EventFieldType.LOCATION_LIST) {
            getOrCreateLocationList(config, binding).clear();
            return;
        }
        if (binding.type == EventFieldType.INT) {
            if (binding.field.getType() == int.class) {
                binding.field.setInt(config, 0);
            } else {
                binding.field.set(config, 0);
            }
            return;
        }
        if (binding.type == EventFieldType.DOUBLE) {
            if (binding.field.getType() == double.class) {
                binding.field.setDouble(config, 0D);
            } else {
                binding.field.set(config, 0D);
            }
        }
    }

    private boolean matchesEventGroup(String rawEventName, String path) {
        String eventName = normalizeEventKey(rawEventName);
        if (eventName.isEmpty()) {
            return false;
        }

        if ("ham".equals(eventName) || "hamburger".equals(eventName)) {
            return path.startsWith("loc.events.hamburger.");
        }
        if ("spire".equals(eventName)) {
            return path.startsWith("loc.events.spire.")
                    || path.startsWith("loc.portal.");
        }
        if ("rage".equals(eventName) || "ragepit".equals(eventName)) {
            return path.startsWith("loc.events.rage.");
        }
        if ("egg".equals(eventName) || "dragonegg".equals(eventName)) {
            return path.startsWith("loc.events.egg.");
        }
        if ("koth".equals(eventName) || "kingofthehill".equals(eventName)) {
            return path.startsWith("loc.events.koth.");
        }
        if ("cake".equals(eventName)) {
            return path.startsWith("loc.events.cake.");
        }
        if ("package".equals(eventName) || "carepackage".equals(eventName) || "airdrop".equals(eventName)) {
            return "loc.packages".equals(path);
        }
        if ("sewers".equals(eventName) || "sewer".equals(eventName)) {
            return path.startsWith("loc.Sewers.");
        }
        if ("squads".equals(eventName)) {
            return "loc.squads".equals(path);
        }
        if ("blockhead".equals(eventName) || "bh".equals(eventName)) {
            return "loc.blockHead".equals(path);
        }
        if ("genesis".equals(eventName)) {
            return path.startsWith("loc.Genesis.");
        }
        if ("spawn".equals(eventName)) {
            return "loc.spawn".equals(path);
        }
        return normalizeEventKey(path).contains(eventName);
    }

    private String getTypeName(EventFieldType type) {
        if (type == EventFieldType.LOCATION) {
            return "location";
        }
        if (type == EventFieldType.LOCATION_LIST) {
            return "location_list";
        }
        if (type == EventFieldType.INT) {
            return "int";
        }
        if (type == EventFieldType.DOUBLE) {
            return "double";
        }
        return "unknown";
    }

    private enum EventFieldType {
        LOCATION,
        LOCATION_LIST,
        INT,
        DOUBLE
    }

    private static class EventFieldBinding {
        private final String path;
        private final String alias;
        private final Field field;
        private final EventFieldType type;

        private EventFieldBinding(String path, String alias, Field field, EventFieldType type) {
            this.path = path;
            this.alias = alias;
            this.field = field;
            this.type = type;
        }
    }

    @Command(
            names = "clearrecords",
            permissionNode = "pit.admin"
    )
    public void clearRecords(Player player) {
        final ItemStack item = player.getItemInHand();
        if (item == null) return;

        final IMythicItem mythicItem = Utils.getMythicItem(item);
        if (mythicItem == null) return;

        mythicItem.getEnchantmentRecords().clear();
        player.setItemInHand(mythicItem.toItemStack());

        player.sendMessage(CC.translate("&a完成."));
    }

    @Command(
            names = "resetKingsQuests",
            permissionNode = "pit.admin"
    )
    public void resetKingsQuests(CommandSender sender) {
        NewConfiguration.INSTANCE.setKingsQuestsMarker(UUID.randomUUID());
        NewConfiguration.INSTANCE.save();
        sender.sendMessage(CC.translate("&a成功"));
    }

    @Command(
            names = "pitadmin reload",
            permissionNode = "pit.admin"
    )
    public void reloadNewConf(CommandSender sender) {
        sender.sendMessage(CC.translate("&7 重载中..."));
        ThePit.getInstance().getPitConfig().load();
        NewConfiguration.INSTANCE.loadFile();
        NewConfiguration.INSTANCE.load();
        sender.sendMessage(CC.translate("&7 重载完成!"));
        sender.sendMessage(CC.translate("&c 重载JS附魔..."));
        JSHandler.INSTANCE.load();
        sender.sendMessage(CC.translate("&a JS附魔加载完成"));
    }

    @Command(
            names = "pitadmin internal",
            permissionNode = "pit.internal"
    )
    public void internalName(Player player, @Parameter(name = "key") String key) {
        if (player.getItemInHand() == null || player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage(CC.translate("&c请先手持物品!"));
        }
        try {
            player.setItemInHand(new ItemBuilder(player.getItemInHand()).internalName(UUID.randomUUID().toString()).build());
            player.sendMessage("§a添加成功 内部名: " + key);
        } catch (Exception ignored) {
            player.sendMessage("？？？");
        }
    }

    @Command(names = "auction"
            , permissionNode = "pit.admin")
    public void customAuction(Player player, @Parameter(name = "price") int price) {
        if (player.getItemInHand() != null || !player.getItemInHand().getType().equals(Material.AIR)) {
            player.sendMessage("§c请手持物品!");
            AuctionEvent auctionEvent = new AuctionEvent();
            auctionEvent.setLots(new AuctionEvent.LotsData(new ItemStack[]{player.getItemInHand().clone()}, price, 0));
            auctionEvent.setStartByAdmin(true);
            auctionEvent.setCustom(true);
            ThePit.getInstance().getEventFactory().activeEvent(auctionEvent);
            player.sendMessage(CC.translate("§d成功发起拍卖"));
        }
    }

    @Command(
            names = "rename",
            permissionNode = "pit.rename"
    )
    public void rename(Player player, @Parameter(name = "重命名") String name) {
        ItemStack item = player.getItemInHand();
        IMythicItem mythicItem = MythicUtil.getMythicItem(item);
        if (mythicItem == null) {
            player.sendMessage(CC.translate("&c需要为神话物品才可以重命名"));
            return;
        }

        mythicItem.setCustomName(CC.translate(name));
        player.setItemInHand(mythicItem.toItemStack());

        player.sendMessage(CC.translate("&a成功"));
    }

}
